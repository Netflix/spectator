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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.ipc.IpcAttempt;
import com.netflix.spectator.ipc.IpcLogEntry;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.IpcProtocol;
import com.netflix.spectator.ipc.IpcStatus;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.util.List;

/**
 * Collect common <a href="http://netflix.github.io/spectator/en/latest/ext/ipc/">IPC metrics</a>
 * for AWS SDK requests. This class should get loaded automatically by the SDK if it is in the
 * classpath.
 */
public class SpectatorExecutionInterceptor implements ExecutionInterceptor {

  private static final ExecutionAttribute<IpcLogEntry> LOG_ENTRY =
      new ExecutionAttribute<>("SpectatorIpcLogEntry");

  private static final ExecutionAttribute<Boolean> STATUS_IS_SET =
      new ExecutionAttribute<>("SpectatorIpcStatusIsSet");

  private final IpcLogger logger;

  /**
   * Create a new instance using {@link Spectator#globalRegistry()}.
   */
  public SpectatorExecutionInterceptor() {
    this(Spectator.globalRegistry());
  }

  /**
   * Create a new instance using the specified registry.
   *
   * @param registry
   *     Registry to use for managing the collected metrics.
   */
  public SpectatorExecutionInterceptor(Registry registry) {
    this.logger = new IpcLogger(registry);
  }

  /**
   * For network errors there will not be a response so the status will not have been set. This
   * method looks for a flag in the attributes to see if we need to close off the log entry for
   * the attempt.
   */
  private boolean isStatusSet(ExecutionAttributes attrs) {
    Boolean s = attrs.getAttribute(STATUS_IS_SET);
    return s != null && s;
  }

  /**
   * If there is a retry, then {@code beforeTransmission} will be called with the previous
   * attributes. This method will look for an existing entry and write out the log message.
   * The log entry may not have been filled in with a status if no response was received,
   * e.g., a connection exception. Since we do not have access to the failure, the status
   * will get set to {@code unexpected_error}.
   */
  private void logRetryAttempt(ExecutionAttributes attrs) {
    IpcLogEntry logEntry = attrs.getAttribute(LOG_ENTRY);
    if (logEntry != null) {
      if (!isStatusSet(attrs)) {
        logEntry.markEnd().withStatus(IpcStatus.unexpected_error);
      }
      logEntry.log();
    }
  }

  /**
   * Extract the attempt number from the {@code amz-sdk-retry} header.
   */
  private void updateAttempts(IpcLogEntry logEntry, SdkHttpRequest request) {
    int attempt = 0;
    int max = 0;
    List<String> vs = request.headers().get("amz-sdk-request");
    if (vs != null) {
      for (String v : vs) {
        // Format is: attempt={}; max={}
        // https://github.com/aws/aws-sdk-java-v2/pull/2179/files
        int pos = v.indexOf(';');
        if (pos > 0) {
          attempt = parseFieldValue(v.substring(0, pos));
          max = parseFieldValue(v.substring(pos + 1));
        }
      }
    }
    logEntry
        .withAttempt(IpcAttempt.forAttemptNumber(attempt))
        .withAttemptFinal(attempt == max);
  }

  private int parseFieldValue(String field) {
    int pos = field.indexOf("=");
    try {
      return pos > 0 ? Integer.parseInt(field.substring(pos + 1)) : 0;
    } catch (NumberFormatException e) {
      // If we cannot parse it, then attempt is unknown
      return 0;
    }
  }

  @Override
  public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes attrs) {
    logRetryAttempt(attrs);

    String serviceName = attrs.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String opName = attrs.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    String endpoint = serviceName + "." + opName;

    SdkHttpRequest request = context.httpRequest();

    IpcLogEntry logEntry = logger.createClientEntry()
        .withOwner("aws-sdk-java-v2")
        .withProtocol(IpcProtocol.http_1)
        .withHttpMethod(request.method().name())
        .withUri(request.getUri())
        .withEndpoint(endpoint);
    updateAttempts(logEntry, request);

    request.headers().forEach((k, vs) -> vs.forEach(v -> logEntry.addRequestHeader(k, v)));

    attrs.putAttribute(LOG_ENTRY, logEntry.markStart());
  }

  @Override
  public void afterTransmission(Context.AfterTransmission context, ExecutionAttributes attrs) {
    SdkHttpResponse response = context.httpResponse();
    IpcLogEntry logEntry = attrs.getAttribute(LOG_ENTRY)
        .markEnd()
        .withHttpStatus(response.statusCode());
    attrs.putAttribute(STATUS_IS_SET, true);

    response.headers().forEach((k, vs) -> vs.forEach(v -> logEntry.addResponseHeader(k, v)));
  }

  @Override
  public void afterExecution(Context.AfterExecution context, ExecutionAttributes attrs) {
    attrs.getAttribute(LOG_ENTRY).log();
  }

  @Override
  public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attrs) {
    IpcLogEntry logEntry = attrs.getAttribute(LOG_ENTRY);
    Throwable t = context.exception();
    if (t instanceof AwsServiceException) {
      AwsServiceException exception = ((AwsServiceException) t);
      if (exception.isThrottlingException()) {
        logEntry.withStatus(IpcStatus.throttled);
      }
      logEntry.withStatusDetail(exception.awsErrorDetails().errorCode());
    }
    logEntry.withException(context.exception()).log();
  }
}
