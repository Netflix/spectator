/*
 * Copyright 2014-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.aws2;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.ipc.IpcMetric;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SpectatorExecutionInterceptorTest {

  private static final int RETRIES = 3;

  private ManualClock clock;
  private Registry registry;
  private SpectatorExecutionInterceptor interceptor;

  @BeforeEach
  public void before() {
    clock = new ManualClock();
    registry = new DefaultRegistry(clock);
    interceptor = new SpectatorExecutionInterceptor(registry);
  }

  @AfterEach
  public void after() {
    IpcMetric.validate(registry, true);
  }

  private void execute(TestContext context, ExecutionAttributes attrs, long latency) {
    interceptor.beforeExecution(context, attrs);
    interceptor.modifyRequest(context, attrs);
    interceptor.beforeMarshalling(context, attrs);
    interceptor.afterMarshalling(context, attrs);
    interceptor.modifyHttpRequest(context, attrs);
    interceptor.beforeTransmission(context, attrs);
    clock.setMonotonicTime(latency);
    if (context.httpResponse() == null) {
      // Simulate network failure with no response received
      for (int i = 0; i < RETRIES; ++i) {
        interceptor.beforeTransmission(context, attrs);
        clock.setMonotonicTime(clock.monotonicTime() + latency);
      }
      interceptor.onExecutionFailure(context.failureContext(), attrs);
    } else {
      interceptor.afterTransmission(context, attrs);
      interceptor.modifyHttpResponse(context, attrs);
      interceptor.beforeUnmarshalling(context, attrs);
      if (context.isFailure()) {
        interceptor.onExecutionFailure(context.failureContext(), attrs);
      } else {
        interceptor.afterUnmarshalling(context, attrs);
        interceptor.modifyResponse(context, attrs);
        interceptor.afterExecution(context, attrs);
      }
    }
  }

  private ExecutionAttributes createAttributes(String service, String op) {
    ExecutionAttributes attrs = new ExecutionAttributes();
    attrs.putAttribute(SdkExecutionAttribute.SERVICE_NAME, service);
    attrs.putAttribute(SdkExecutionAttribute.OPERATION_NAME, op);
    return attrs;
  }

  private String get(Id id, String k) {
    return Utils.getTagValue(id, k);
  }

  private long millis(long v) {
    return TimeUnit.MILLISECONDS.toNanos(v);
  }

  @Test
  public void successfulRequest() {
    SdkHttpRequest request = SdkHttpRequest.builder()
        .method(SdkHttpMethod.POST)
        .uri(URI.create("https://ec2.us-east-1.amazonaws.com"))
        .build();
    SdkHttpResponse response = SdkHttpResponse.builder()
        .statusCode(200)
        .build();
    TestContext context = new TestContext(request, response);
    execute(context, createAttributes("EC2", "DescribeInstances"), millis(42));
    Assertions.assertEquals(1, registry.timers().count());

    Timer t = registry.timers().findFirst().orElse(null);
    Assertions.assertNotNull(t);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(millis(42), t.totalTime());
    Assertions.assertEquals("EC2.DescribeInstances", get(t.id(), "ipc.endpoint"));
    Assertions.assertEquals("200", get(t.id(), "http.status"));
    Assertions.assertEquals("POST", get(t.id(), "http.method"));
  }

  @Test
  public void networkFailure() {
    SdkHttpRequest request = SdkHttpRequest.builder()
        .method(SdkHttpMethod.POST)
        .uri(URI.create("https://ec2.us-east-1.amazonaws.com"))
        .build();
    Throwable error = new ConnectException("failed to connect");
    TestContext context = new TestContext(request, null, error);
    execute(context, createAttributes("EC2", "DescribeInstances"), millis(30));
    Assertions.assertEquals(2, registry.timers().count());

    registry.timers().forEach(t -> {
      Assertions.assertEquals("EC2.DescribeInstances", get(t.id(), "ipc.endpoint"));
      switch ((int) t.count()) {
        case 1:
          Assertions.assertEquals("connection_error", get(t.id(), "ipc.status"));
          Assertions.assertEquals("ConnectException", get(t.id(), "ipc.status.detail"));
          break;
        case 3:
          // Captured for the retries attempts, we do not know the exception so it should have
          // an unexpected status
          Assertions.assertEquals("unexpected_error", get(t.id(), "ipc.status"));
          break;
        default:
          Assertions.fail("unexpected count: " + t.id() + " = " + t.count());
      }
    });
  }

  @Test
  public void awsFailure() {
    SdkHttpRequest request = SdkHttpRequest.builder()
        .method(SdkHttpMethod.POST)
        .uri(URI.create("https://ec2.us-east-1.amazonaws.com"))
        .build();
    SdkHttpResponse response = SdkHttpResponse.builder()
        .statusCode(403)
        .build();
    Throwable error = AwsServiceException.builder()
        .awsErrorDetails(AwsErrorDetails.builder()
            .errorCode("AccessDenied")
            .errorMessage("credentials have expired")
            .build())
        .build();
    TestContext context = new TestContext(request, response, error);
    execute(context, createAttributes("EC2", "DescribeInstances"), millis(30));
    Assertions.assertEquals(1, registry.timers().count());

    Timer t = registry.timers().findFirst().orElse(null);
    Assertions.assertNotNull(t);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(millis(30), t.totalTime());
    Assertions.assertEquals("403", get(t.id(), "http.status"));
    Assertions.assertEquals("AccessDenied", get(t.id(), "ipc.status.detail"));
  }

  @Test
  public void awsThrottling() {
    SdkHttpRequest request = SdkHttpRequest.builder()
        .method(SdkHttpMethod.POST)
        .uri(URI.create("https://ec2.us-east-1.amazonaws.com"))
        .build();
    SdkHttpResponse response = SdkHttpResponse.builder()
        .statusCode(400)
        .build();
    Throwable error = AwsServiceException.builder()
        .awsErrorDetails(AwsErrorDetails.builder()
            .errorCode("Throttling")
            .errorMessage("too many requests")
            .build())
        .build();
    TestContext context = new TestContext(request, response, error);
    execute(context, createAttributes("EC2", "DescribeInstances"), millis(30));
    Assertions.assertEquals(1, registry.timers().count());

    Timer t = registry.timers().findFirst().orElse(null);
    Assertions.assertNotNull(t);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(millis(30), t.totalTime());
    Assertions.assertEquals("400", get(t.id(), "http.status"));
    Assertions.assertEquals("throttled", get(t.id(), "ipc.status"));
  }

  private void parseRetryHeaderTest(String expected, String header) {
    SdkHttpRequest request = SdkHttpRequest.builder()
        .method(SdkHttpMethod.POST)
        .uri(URI.create("https://ec2.us-east-1.amazonaws.com"))
        .appendHeader("amz-sdk-request", header)
        .build();
    SdkHttpResponse response = SdkHttpResponse.builder()
        .statusCode(200)
        .build();
    TestContext context = new TestContext(request, response);
    execute(context, createAttributes("EC2", "DescribeInstances"), millis(30));
    Assertions.assertEquals(1, registry.timers().count());

    Timer t = registry.timers().findFirst().orElse(null);
    Assertions.assertNotNull(t);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(millis(30), t.totalTime());
    Assertions.assertEquals(expected, get(t.id(), "ipc.attempt"));
  }

  @Test
  public void parseRetryHeaderInitial() {
    parseRetryHeaderTest("initial", "attempt=1; max=4");
  }

  @Test
  public void parseRetryHeaderSecond() {
    parseRetryHeaderTest("second", "attempt=2; max=4");
  }

  @Test
  public void parseRetryHeaderThird() {
    parseRetryHeaderTest("third_up", "attempt=3; max=4");
  }

  @Test
  public void parseRetryHeader50() {
    parseRetryHeaderTest("third_up", "attempt=50; max=50");
  }

  @Test
  public void parseRetryHeaderInvalidNumber() {
    parseRetryHeaderTest("unknown", "attempt=foo; max=bar");
  }

  @Test
  public void parseRetryHeaderBadFormat() {
    parseRetryHeaderTest("unknown", "foo");
  }

  private static class TestContext implements Context.AfterExecution {

    private SdkHttpRequest request;
    private SdkHttpResponse response;
    private Throwable error;

    public TestContext(SdkHttpRequest request, SdkHttpResponse response) {
      this(request, response, null);
    }

    public TestContext(SdkHttpRequest request, SdkHttpResponse response, Throwable error) {
      this.request = request;
      this.response = response;
      this.error = error;
    }

    @Override public SdkResponse response() {
      return null;
    }

    @Override public SdkHttpResponse httpResponse() {
      return response;
    }

    @Override public Optional<Publisher<ByteBuffer>> responsePublisher() {
      return Optional.empty();
    }

    @Override public Optional<InputStream> responseBody() {
      return Optional.empty();
    }

    @Override public SdkHttpRequest httpRequest() {
      return request;
    }

    @Override public Optional<RequestBody> requestBody() {
      return Optional.empty();
    }

    @Override public Optional<AsyncRequestBody> asyncRequestBody() {
      return Optional.empty();
    }

    @Override public SdkRequest request() {
      return null;
    }

    boolean isFailure() {
      return error != null;
    }

    Context.FailedExecution failureContext() {
      return new Context.FailedExecution() {
        @Override
        public Throwable exception() {
          return error;
        }

        @Override
        public SdkRequest request() {
          return null;
        }

        @Override
        public Optional<SdkHttpRequest> httpRequest() {
          return Optional.ofNullable(request);
        }

        @Override
        public Optional<SdkHttpResponse> httpResponse() {
          return Optional.ofNullable(response);
        }

        @Override
        public Optional<SdkResponse> response() {
          return Optional.empty();
        }
      };
    }
  }
}
