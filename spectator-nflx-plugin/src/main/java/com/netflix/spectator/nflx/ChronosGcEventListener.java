/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.nflx;

import com.netflix.client.ClientException;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.gc.GcEvent;
import com.netflix.spectator.gc.GcEventListener;
import com.sun.management.GcInfo;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action0;
import rx.functions.Action1;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listener that sends GC events to a chronos backend.
 */
public class ChronosGcEventListener implements GcEventListener {

  private static final DynamicBooleanProperty ENABLED =
    DynamicPropertyFactory.getInstance().getBooleanProperty("spectator.gc.chronosEnabled", true);

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ExtendedRegistry registry = Spectator.registry();
  private final Id requestCount = registry.createId("spectator.gc.chronosPost");

  private final ObjectMapper mapper = new ObjectMapper();

  private final LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(
          DefaultClientConfigImpl.getClientConfigWithDefaultValues("chronos_gc", "niws.client"));

  // Keep track of the previous end time to provide additional context for chronos visualization
  // without needing to find the previous event. Stored as atomic long as there is no guarantee
  // that the listener won't receive events from multiple threads.
  private final AtomicLong previousEndTime = new AtomicLong(-1L);

  private String getenv(String k) {
    String v = System.getenv(k);
    return (v == null || v.length() == 0) ? "unknown" : v;
  }

  /** Convert a GC event into a map. */
  Map<String, Object> toGcInfoMap(GcEvent event) {
    final GcInfo info = event.getInfo().getGcInfo();
    Map<String, Object> map = new HashMap<>();
    map.put("id", info.getId());
    map.put("startTime", event.getStartTime());
    map.put("endTime", event.getStartTime() + info.getDuration());
    map.put("previousEndTime", previousEndTime.get());
    map.put("duration", info.getDuration());
    map.put("memoryBeforeGc", info.getMemoryUsageBeforeGc());
    map.put("memoryAfterGc", info.getMemoryUsageAfterGc());
    return map;
  }

  /** Convert a GC event into a map. */
  Map<String, Object> toEventMap(GcEvent event) {
    Map<String, Object> map = new HashMap<>();
    map.put("action", event.getInfo().getGcAction());
    map.put("cause", event.getInfo().getGcCause());
    map.put("name", event.getName());
    map.put("gcInfo", toGcInfoMap(event));
    map.put("app", getenv("NETFLIX_APP"));
    map.put("cluster", getenv("NETFLIX_CLUSTER"));
    map.put("asg", getenv("NETFLIX_AUTO_SCALE_GROUP"));
    map.put("region", getenv("EC2_REGION"));
    map.put("zone", getenv("EC2_AVAILABILITY_ZONE"));
    map.put("ami", getenv("EC2_AMI_ID"));
    map.put("node", getenv("EC2_INSTANCE_ID"));
    return map;
  }

  private void sendToChronos(final byte[] json) {
    final ChronosGcEventListener listener = this;
    final HttpClientRequest<ByteBuf> request = HttpClientRequest.createPost("/api/v2/event")
        .withHeader("Content-Type", "application/json")
        .withContent(json);

    final long start = System.nanoTime();
    client.submit(request).subscribe(
        new Action1<HttpClientResponse<ByteBuf>>() {
          @Override
          public void call(HttpClientResponse<ByteBuf> response) {
            final int code = response.getStatus().code();
            if (code != 200) {
              logger.warn("failed to send GC event to chronos (status={})", code);
            }
            final long latency = System.nanoTime() - start;
            registry.timer(requestCount.withTag("status", "" + code)).record(latency, TimeUnit.NANOSECONDS);
          }
        },
        new Action1<Throwable>() {
          @Override
          public void call(Throwable t) {
            logger.warn("failed to send GC event to chronos", t);
            final String status = (t instanceof ClientException)
                ? ((ClientException) t).getErrorType().name()
                : t.getClass().getSimpleName();
            final long latency = System.nanoTime() - start;
            registry.timer(requestCount.withTag("status", status)).record(latency, TimeUnit.NANOSECONDS);
            requestCompleted();
          }
        },
        new Action0() {
          @Override
          public void call() {
            requestCompleted();
          }
        }
    );
  }

  private synchronized void requestCompleted() {
    // This is here for unit tests so that the completion can be detected reliably.
    notify();
  }

  @Override
  public void onComplete(final GcEvent event) {
    if (!ENABLED.get()) {
      return;
    }

    try {
      final byte[] json = mapper.writeValueAsBytes(toEventMap(event));
      sendToChronos(json);
    } catch (IOException e) {
      logger.warn("failed to send GC event to chronos", e);
    }

    previousEndTime.set(event.getStartTime() + event.getInfo().getGcInfo().getDuration());
  }

  /** Shutdown the client used to send data to chronos. */
  public void shutdown() {
    client.shutdown();
  }
}
