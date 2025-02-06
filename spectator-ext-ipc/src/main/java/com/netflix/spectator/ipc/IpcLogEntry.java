/*
 * Copyright 2014-2023 Netflix, Inc.
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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.ipc.http.PathSanitizer;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Builder used to fill in and submit a log entry associated with an IPC request.
 */
@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidStringBufferField"})
public final class IpcLogEntry {

  private final Clock clock;

  private Registry registry;
  private IpcLogger logger;
  private Level level;
  private Marker marker;

  private long startNanos;
  private long startTime;
  private long latency;

  private String owner;
  private IpcResult result;
  private IpcSource source;

  private String protocol;

  private IpcStatus status;
  private String statusDetail;
  private Throwable exception;

  private IpcAttempt attempt;
  private IpcAttemptFinal attemptFinal;

  private String vip;
  private String endpoint;
  private IpcMethod method;

  private String clientRegion;
  private String clientZone;
  private String clientApp;
  private String clientCluster;
  private String clientAsg;
  private String clientNode;

  private String serverRegion;
  private String serverZone;
  private String serverApp;
  private String serverCluster;
  private String serverAsg;
  private String serverNode;

  private int httpStatus;

  private String uri;
  private String path;
  private long requestContentLength = -1L;
  private long responseContentLength = -1L;

  private final List<Header> requestHeaders = new ArrayList<>();
  private final List<Header> responseHeaders = new ArrayList<>();

  private String remoteAddress;
  private int remotePort;

  private boolean disableMetrics;

  private final Map<String, String> additionalTags = new HashMap<>();

  private final StringBuilder builder = new StringBuilder();

  private Id inflightId;

  /** Create a new instance. */
  IpcLogEntry(Clock clock) {
    this.clock = clock;
    reset();
  }

  /** Set the registry to use for recording metrics. */
  IpcLogEntry withRegistry(Registry registry) {
    this.registry = registry;
    return this;
  }

  /**
   * Set the logger instance to use for tracking state such as the number of inflight
   * requests.
   */
  IpcLogEntry withLogger(IpcLogger logger) {
    this.logger = logger;
    return this;
  }

  /**
   * Set the marker indicating whether it is a client or server request.
   */
  IpcLogEntry withMarker(Marker marker) {
    this.marker = marker;
    return this;
  }

  /**
   * Set the log level to use when sending to SLF4j. The default level is DEBUG. For
   * high volume use-cases it is recommended to set the level to TRACE to avoid excessive
   * logging.
   */
  public IpcLogEntry withLogLevel(Level level) {
    this.level = level;
    return this;
  }

  /**
   * Set the latency for the request. This will typically be set automatically using
   * {@link #markStart()} and {@link #markEnd()}. Use this method if the latency value
   * is provided by the implementation rather than measured using this entry.
   */
  public IpcLogEntry withLatency(long latency, TimeUnit unit) {
    this.latency = unit.toNanos(latency);
    return this;
  }

  /**
   * Record the starting time for the request and update the number of inflight requests.
   * This should be called just before starting the execution of the request. As soon as
   * the request completes it is recommended to call {@link #markEnd()}.
   */
  public IpcLogEntry markStart() {
    if (registry != null && !disableMetrics && logger != null && logger.inflightEnabled()) {
      inflightId = getInflightId();
      int n = logger.inflightRequests(inflightId).incrementAndGet();
      registry.distributionSummary(inflightId).record(n);
    }

    startTime = clock.wallTime();
    startNanos = clock.monotonicTime();
    return this;
  }

  /**
   * Record the latency for the request based on the completion time. This will be
   * implicitly called when the request is logged, but it is advisable to call as soon
   * as the response is received to minimize the amount of response processing that is
   * counted as part of the request latency.
   */
  public IpcLogEntry markEnd() {
    return withLatency(clock.monotonicTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Set the library that produced the metric.
   */
  public IpcLogEntry withOwner(String owner) {
    this.owner = owner;
    return this;
  }

  /**
   * Set the protocol used for this request. See {@link IpcProtocol} for more information.
   */
  public IpcLogEntry withProtocol(IpcProtocol protocol) {
    return withProtocol(protocol.value());
  }

  /**
   * Set the protocol used for this request. See {@link IpcProtocol} for more information.
   */
  public IpcLogEntry withProtocol(String protocol) {
    this.protocol = protocol;
    return this;
  }

  /**
   * Set the result for this request. See {@link IpcResult} for more information.
   */
  public IpcLogEntry withResult(IpcResult result) {
    this.result = result;
    return this;
  }

  /**
   * Set the source for this request. See {@link IpcSource} for more information.
   */
  public IpcLogEntry withSource(IpcSource source) {
    this.source = source;
    return this;
  }

  /**
   * Set the high level status for the request. See {@link IpcStatus} for more
   * information.
   */
  public IpcLogEntry withStatus(IpcStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Set the detailed implementation specific status for the request. In most cases it
   * is preferable to use {@link #withException(Throwable)} or {@link #withHttpStatus(int)}
   * instead of calling this directly.
   */
  public IpcLogEntry withStatusDetail(String statusDetail) {
    this.statusDetail = statusDetail;
    return this;
  }

  /**
   * Set the high level cause for a request failure. See {@link IpcErrorGroup} for more
   * information.
   *
   * @deprecated Use {@link #withStatus(IpcStatus)} instead. This method is scheduled for
   * removal in a future release.
   */
  @Deprecated
  public IpcLogEntry withErrorGroup(IpcErrorGroup errorGroup) {
    return this;
  }

  /**
   * Set the implementation specific reason for the request failure. In most cases it
   * is preferable to use {@link #withException(Throwable)} or {@link #withHttpStatus(int)}
   * instead of calling this directly.
   *
   * @deprecated Use {@link #withStatusDetail(String)} instead. This method is scheduled for
   * removal in a future release.
   */
  @Deprecated
  public IpcLogEntry withErrorReason(String errorReason) {
    return this;
  }

  /**
   * Set the exception that was thrown while trying to execute the request. This will be
   * logged and can be used to fill in the error reason.
   */
  public IpcLogEntry withException(Throwable exception) {
    this.exception = exception;
    if (statusDetail == null) {
      statusDetail = exception.getClass().getSimpleName();
    }
    if (status == null) {
      status = IpcStatus.forException(exception);
    }
    return this;
  }

  /**
   * Set the attempt number for the request.
   */
  public IpcLogEntry withAttempt(IpcAttempt attempt) {
    this.attempt = attempt;
    return this;
  }

  /**
   * Set the attempt number for the request.
   */
  public IpcLogEntry withAttempt(int attempt) {
    return withAttempt(IpcAttempt.forAttemptNumber(attempt));
  }

  /**
   * Set whether or not this is the final attempt for the request.
   */
  public IpcLogEntry withAttemptFinal(boolean isFinal) {
    this.attemptFinal = IpcAttemptFinal.forValue(isFinal);
    return this;
  }

  /**
   * Set the vip that was used to determine which server to contact. This will only be
   * present if using client side load balancing via Eureka.
   */
  public IpcLogEntry withVip(String vip) {
    this.vip = vip;
    return this;
  }

  /**
   * Set the endpoint for this request.
   */
  public IpcLogEntry withEndpoint(String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  /**
   * Set the method used for this request. See {@link IpcMethod} for possible values.
   */
  public IpcLogEntry withMethod(IpcMethod method) {
    this.method = method;
    return this;
  }

  /**
   * Set the client region for the request. In the case of the server side this will be
   * automatically filled in if the {@link NetflixHeader#Zone} is specified on the client
   * request.
   */
  public IpcLogEntry withClientRegion(String region) {
    this.clientRegion = region;
    return this;
  }

  /**
   * Set the client zone for the request. In the case of the server side this will be
   * automatically filled in if the {@link NetflixHeader#Zone} is specified on the client
   * request.
   */
  public IpcLogEntry withClientZone(String zone) {
    this.clientZone = zone;
    if (clientRegion == null) {
      clientRegion = extractRegionFromZone(zone);
    }
    return this;
  }

  /**
   * Set the client app for the request. In the case of the server side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the client
   * request. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withClientApp(String app) {
    this.clientApp = app;
    return this;
  }

  /**
   * Set the client cluster for the request. In the case of the server side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the client
   * request. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withClientCluster(String cluster) {
    this.clientCluster = cluster;
    return this;
  }

  /**
   * Set the client ASG for the request. In the case of the server side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the client
   * request. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withClientAsg(String asg) {
    this.clientAsg = asg;
    if (clientApp == null || clientCluster == null) {
      ServerGroup group = ServerGroup.parse(asg);
      clientApp = (clientApp == null) ? group.app() : clientApp;
      clientCluster = (clientCluster == null) ? group.cluster() : clientCluster;
    }
    return this;
  }

  /**
   * Set the client node for the request. This will be used for access logging only and will
   * not be present on metrics. The server will log this value if the {@link NetflixHeader#Node}
   * header is present on the client request.
   */
  public IpcLogEntry withClientNode(String node) {
    this.clientNode = node;
    return this;
  }

  /**
   * Set the server region for the request. In the case of the client side this will be
   * automatically filled in if the {@link NetflixHeader#Zone} is specified on the server
   * response.
   */
  public IpcLogEntry withServerRegion(String region) {
    this.serverRegion = region;
    return this;
  }

  /**
   * Set the server zone for the request. In the case of the client side this will be
   * automatically filled in if the {@link NetflixHeader#Zone} is specified on the server
   * response.
   */
  public IpcLogEntry withServerZone(String zone) {
    this.serverZone = zone;
    if (serverRegion == null) {
      serverRegion = extractRegionFromZone(zone);
    }
    return this;
  }

  /**
   * Set the server app for the request. In the case of the client side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the server
   * response. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withServerApp(String app) {
    this.serverApp = app;
    return this;
  }

  /**
   * Set the server cluster for the request. In the case of the client side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the server
   * response. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withServerCluster(String cluster) {
    this.serverCluster = cluster;
    return this;
  }

  /**
   * Set the server ASG for the request. In the case of the client side this will be
   * automatically filled in if the {@link NetflixHeader#ASG} is specified on the server
   * response. The ASG value must follow the
   * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">
   * Frigga server group</a> naming conventions.
   */
  public IpcLogEntry withServerAsg(String asg) {
    this.serverAsg = asg;
    if (serverApp == null || serverCluster == null) {
      ServerGroup group = ServerGroup.parse(asg);
      serverApp = (serverApp == null) ? group.app() : serverApp;
      serverCluster = (serverCluster == null) ? group.cluster() : serverCluster;
    }
    return this;
  }

  /**
   * Set the server node for the request. This will be used for access logging only and will
   * not be present on metrics. The client will log this value if the {@link NetflixHeader#Node}
   * header is present on the server response.
   */
  public IpcLogEntry withServerNode(String node) {
    this.serverNode = node;
    return this;
  }

  /**
   * Set the HTTP method used for this request.
   */
  public IpcLogEntry withHttpMethod(String method) {
    try {
      IpcMethod m = IpcMethod.valueOf(method.toLowerCase(Locale.US));
      withMethod(m);
    } catch (Exception e) {
      // Ignore invalid methods
      withMethod(IpcMethod.unknown);
    }
    return this;
  }

  /**
   * Set the HTTP status code for this request. If not already set, then this will set an
   * appropriate value for {@link #withStatus(IpcStatus)} and {@link #withResult(IpcResult)}
   * based on the status code.
   */
  public IpcLogEntry withHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 600) {
      this.httpStatus = httpStatus;
      if (status == null) {
        status = IpcStatus.forHttpStatus(httpStatus);
      }
      if (result == null) {
        result = status.result();
      }
    } else {
      // If an invalid HTTP status code is passed in
      this.httpStatus = -1;
    }
    return this;
  }

  /**
   * Set the URI and path for the request.
   */
  public IpcLogEntry withUri(String uri, String path) {
    this.uri = uri;
    this.path = path;
    return this;
  }

  /**
   * Set the URI and path for the request. The path will get extracted from the URI. If the
   * URI is non-strict and cannot be parsed with the java URI class, then use
   * {@link #withUri(String, String)} instead.
   */
  public IpcLogEntry withUri(URI uri) {
    return withUri(uri.toString(), uri.getPath());
  }

  /**
   * Set the length for the request entity if it is known at the time of logging. If the size
   * is not known, e.g. a chunked HTTP entity, then a negative value can be used and the length
   * will be ignored.
   */
  public IpcLogEntry withRequestContentLength(long length) {
    this.requestContentLength = length;
    return this;
  }

  /**
   * Set the length for the request entity if it is known at the time of logging. If the size
   * is not known, e.g. a chunked HTTP entity, then a negative value can be used and the length
   * will be ignored.
   */
  public IpcLogEntry withResponseContentLength(long length) {
    this.responseContentLength = length;
    return this;
  }

  /**
   * Add a request header value. For special headers in {@link NetflixHeader} it will
   * automatically fill in the more specific fields based on the header values.
   */
  public IpcLogEntry addRequestHeader(String name, String value) {
    if (clientAsg == null && name.equalsIgnoreCase(NetflixHeader.ASG.headerName())) {
      withClientAsg(value);
    } else if (clientZone == null && name.equalsIgnoreCase(NetflixHeader.Zone.headerName())) {
      withClientZone(value);
    } else if (clientNode == null && name.equalsIgnoreCase(NetflixHeader.Node.headerName())) {
      withClientNode(value);
    } else if (vip == null && name.equalsIgnoreCase(NetflixHeader.Vip.headerName())) {
      withVip(value);
    } else if (isMeshRequest(name, value)) {
      disableMetrics();
    }
    this.requestHeaders.add(new Header(name, value));
    return this;
  }

  private boolean isMeshRequest(String name, String value) {
    return name.equalsIgnoreCase(NetflixHeader.IngressCommonIpcMetrics.headerName())
        && "true".equalsIgnoreCase(value);
  }

  /**
   * Add a response header value. For special headers in {@link NetflixHeader} it will
   * automatically fill in the more specific fields based on the header values.
   */
  public IpcLogEntry addResponseHeader(String name, String value) {
    if (serverAsg == null && name.equalsIgnoreCase(NetflixHeader.ASG.headerName())) {
      withServerAsg(value);
    } else if (serverZone == null && name.equalsIgnoreCase(NetflixHeader.Zone.headerName())) {
      withServerZone(value);
    } else if (serverNode == null && name.equalsIgnoreCase(NetflixHeader.Node.headerName())) {
      withServerNode(value);
    } else if (endpoint == null && name.equalsIgnoreCase(NetflixHeader.Endpoint.headerName())) {
      withEndpoint(value);
    }
    this.responseHeaders.add(new Header(name, value));
    return this;
  }

  /**
   * Set the remote address for the request.
   */
  public IpcLogEntry withRemoteAddress(String remoteAddress) {
    this.remoteAddress = remoteAddress;
    return this;
  }

  /**
   * Set the remote port for the request.
   */
  public IpcLogEntry withRemotePort(int remotePort) {
    this.remotePort = remotePort;
    return this;
  }

  /**
   * Add custom tags to the request metrics. Note, IPC metrics already have many tags and it
   * is not recommended for users to tack on additional context. In particular, any additional
   * tags should have a <b>guaranteed</b> low cardinality. If additional tagging causes these
   * metrics to exceed limits, then you may lose all visibility into requests.
   */
  public IpcLogEntry addTag(Tag tag) {
    this.additionalTags.put(tag.key(), tag.value());
    return this;
  }

  /**
   * Add custom tags to the request metrics. Note, IPC metrics already have many tags and it
   * is not recommended for users to tack on additional context. In particular, any additional
   * tags should have a <b>guaranteed</b> low cardinality. If additional tagging causes these
   * metrics to exceed limits, then you may lose all visibility into requests.
   */
  public IpcLogEntry addTag(String k, String v) {
    this.additionalTags.put(k, v);
    return this;
  }

  /**
   * Disable the metrics. The log will still get written, but none of the metrics will get
   * updated.
   */
  public IpcLogEntry disableMetrics() {
    this.disableMetrics = true;
    return this;
  }

  private void putTag(Map<String, String> tags, Tag tag) {
    if (tag != null) {
      tags.put(tag.key(), tag.value());
    }
  }

  private void putTag(Map<String, String> tags, String k, String v) {
    if (notNullOrEmpty(v)) {
      String value = logger.limiterForKey(k).apply(v);
      tags.put(k, value);
    }
  }

  private void putTag(Map<String, String> tags, String k, Enum<?> v) {
    if (v != null) {
      putTag(tags, k, v.name());
    }
  }

  private void finalizeFields() {
    // Do final checks and update fields that haven't been explicitly set if needed
    // before logging.
    if (result == null) {
      result = status == null ? IpcResult.success : status.result();
    }
    if (status == null) {
      status = (result == IpcResult.success) ? IpcStatus.success : IpcStatus.unexpected_error;
    }
    if (attempt == null) {
      attempt = IpcAttempt.forAttemptNumber(1);
    }
    if (attemptFinal == null) {
      attemptFinal = IpcAttemptFinal.is_true;
    }
    if (endpoint == null) {
      endpoint = (path == null || httpStatus == 404) ? "unknown" : PathSanitizer.sanitize(path);
    }
  }

  private boolean isClient() {
    return marker != null && "ipc-client".equals(marker.getName());
  }

  private Id createCallId(String name) {
    Map<String, String> tags = new HashMap<>();

    // User specified custom tags, add individually to ensure that limiter is applied
    // to the values
    for (Map.Entry<String, String> entry : additionalTags.entrySet()) {
      putTag(tags, entry.getKey(), entry.getValue());
    }

    // Required for both client and server
    putTag(tags, IpcTagKey.owner.key(), owner);
    putTag(tags, result);
    putTag(tags, status);

    if (isClient()) {
      // Required for client, should be null on server
      putTag(tags, attempt);
      putTag(tags, attemptFinal);

      // Optional for client
      putTag(tags, IpcTagKey.serverApp.key(), serverApp);
      putTag(tags, IpcTagKey.serverCluster.key(), serverCluster);
      putTag(tags, IpcTagKey.serverAsg.key(), serverAsg);
    } else {
      // Optional for server
      putTag(tags, IpcTagKey.clientApp.key(), clientApp);
      putTag(tags, IpcTagKey.clientCluster.key(), clientCluster);
      putTag(tags, IpcTagKey.clientAsg.key(), clientAsg);
    }

    // Optional for both client and server
    putTag(tags, IpcTagKey.endpoint.key(), endpoint);
    putTag(tags, IpcTagKey.vip.key(), vip);
    putTag(tags, IpcTagKey.protocol.key(), protocol);
    putTag(tags, IpcTagKey.statusDetail.key(), statusDetail);
    putTag(tags, IpcTagKey.httpStatus.key(), Integer.toString(httpStatus));
    putTag(tags, IpcTagKey.httpMethod.key(), method);

    return registry.createId(name, tags);
  }

  private Id getInflightId() {
    if (inflightId == null) {
      Map<String, String> tags = new HashMap<>();

      // Required for both client and server
      putTag(tags, IpcTagKey.owner.key(), owner);

      // Optional for both client and server
      putTag(tags, IpcTagKey.vip.key(), vip);

      String name = isClient()
          ? IpcMetric.clientInflight.metricName()
          : IpcMetric.serverInflight.metricName();
      inflightId = registry.createId(name, tags);
    }
    return inflightId;
  }

  private void recordClientMetrics() {
    if (disableMetrics) {
      return;
    }

    Id clientCall = createCallId(IpcMetric.clientCall.metricName());
    PercentileTimer.builder(registry)
        .withId(clientCall)
        .build()
        .record(getLatency(), TimeUnit.NANOSECONDS);

    if (responseContentLength >= 0L) {
      Id clientCallSizeInbound = registry.createId(
          IpcMetric.clientCallSizeInbound.metricName(), clientCall.tags());
      registry.distributionSummary(clientCallSizeInbound).record(responseContentLength);
    }

    if (requestContentLength >= 0L) {
      Id clientCallSizeOutbound = registry.createId(
          IpcMetric.clientCallSizeOutbound.metricName(), clientCall.tags());
      registry.distributionSummary(clientCallSizeOutbound).record(requestContentLength);
    }
  }

  private void recordServerMetrics() {
    if (disableMetrics) {
      return;
    }

    Id serverCall = createCallId(IpcMetric.serverCall.metricName());
    PercentileTimer.builder(registry)
        .withId(serverCall)
        .build()
        .record(getLatency(), TimeUnit.NANOSECONDS);

    if (requestContentLength >= 0L) {
      Id serverCallSizeInbound = registry.createId(
          IpcMetric.serverCallSizeInbound.metricName(), serverCall.tags());
      registry.distributionSummary(serverCallSizeInbound).record(requestContentLength);
    }

    if (responseContentLength >= 0L) {
      Id serverCallSizeOutbound = registry.createId(
          IpcMetric.serverCallSizeOutbound.metricName(), serverCall.tags());
      registry.distributionSummary(serverCallSizeOutbound).record(responseContentLength);
    }
  }

  /**
   * Log the request. This entry will potentially be reused after this is called. The user
   * should not attempt any further modifications to the state of this entry.
   */
  public void log() {
    if (logger != null) {
      finalizeFields();
      if (registry != null) {
        if (isClient()) {
          recordClientMetrics();
        } else {
          recordServerMetrics();
        }
      }
      if (inflightId != null) {
        logger.inflightRequests(inflightId).decrementAndGet();
      }

      logger.log(this);
    } else {
      reset();
    }
  }

  /** Return the log level set for this log entry. */
  Level getLevel() {
    return level;
  }

  /** Return the marker set for this log entry. */
  Marker getMarker() {
    return marker;
  }

  /** Return true if the request is successful and the entry can be reused. */
  boolean isSuccessful() {
    return result == IpcResult.success;
  }

  private String extractRegionFromZone(String zone) {
    int n = zone.length();
    if (n < 4) {
      return null;
    } else {
      char c = zone.charAt(n - 2);
      if (Character.isDigit(c) && zone.charAt(n - 3) == '-') {
        // AWS zones have a pattern of `${region}[a-f]`, for example: `us-east-1a`
        return zone.substring(0, n - 1);
      } else if (c == '-') {
        // GCE zones have a pattern of `${region}-[a-f]`, for example: `us-east1-c`
        // https://cloud.google.com/compute/docs/regions-zones/
        return zone.substring(0, n - 2);
      } else {
        // Pattern doesn't look familiar
        return null;
      }
    }
  }

  private long getLatency() {
    if (startNanos >= 0L && latency < 0L) {
      // If latency was not explicitly set but the start time was, then compute the
      // time since the start. The field is updated so subsequent calls will return
      // a consistent value for the latency.
      latency = clock.monotonicTime() - startNanos;
    }
    return latency;
  }

  private String getExceptionClass() {
    return (exception == null)
        ? null
        : exception.getClass().getName();
  }

  private String getExceptionMessage() {
    return (exception == null)
        ? null
        : exception.getMessage();
  }

  private void putInMDC(String key, String value) {
    if (value != null && !value.isEmpty()) {
      MDC.put(key, value);
    }
  }

  private void putInMDC(Tag tag) {
    if (tag != null) {
      putInMDC(tag.key(), tag.value());
    }
  }

  void populateMDC() {
    putInMDC("marker", marker.getName());

    putInMDC("uri", uri);
    putInMDC("path", path);
    putInMDC(IpcTagKey.endpoint.key(), endpoint);
    putInMDC(method);

    putInMDC(IpcTagKey.owner.key(), owner);
    putInMDC(IpcTagKey.protocol.key(), protocol);
    putInMDC(IpcTagKey.vip.key(), vip);

    putInMDC("ipc.client.region", clientRegion);
    putInMDC("ipc.client.zone", clientZone);
    putInMDC("ipc.client.node", clientNode);
    putInMDC(IpcTagKey.clientApp.key(), clientApp);
    putInMDC(IpcTagKey.clientCluster.key(), clientCluster);
    putInMDC(IpcTagKey.clientAsg.key(), clientAsg);

    putInMDC("ipc.server.region", serverRegion);
    putInMDC("ipc.server.zone", serverZone);
    putInMDC("ipc.server.node", serverNode);
    putInMDC(IpcTagKey.serverApp.key(), serverApp);
    putInMDC(IpcTagKey.serverCluster.key(), serverCluster);
    putInMDC(IpcTagKey.serverAsg.key(), serverAsg);

    putInMDC("ipc.remote.address", remoteAddress);
    putInMDC("ipc.remote.port", Integer.toString(remotePort));

    putInMDC(attempt);
    putInMDC(attemptFinal);

    putInMDC(result);
    putInMDC(source);
    putInMDC(status);
    putInMDC(IpcTagKey.statusDetail.key(), statusDetail);

    putInMDC(IpcTagKey.httpStatus.key(), Integer.toString(httpStatus));
  }

  @Override
  public String toString() {
    finalizeFields();
    return new JsonStringBuilder(builder)
        .startObject()
        .addField("owner", owner)
        .addField("start", startTime)
        .addField("latency", getLatency() / 1e9)
        .addField("protocol", protocol)
        .addField("uri", uri)
        .addField("path", path)
        .addField("method", method)
        .addField("endpoint", endpoint)
        .addField("vip", vip)
        .addField("clientRegion", clientRegion)
        .addField("clientZone", clientZone)
        .addField("clientApp", clientApp)
        .addField("clientCluster", clientCluster)
        .addField("clientAsg", clientAsg)
        .addField("clientNode", clientNode)
        .addField("serverRegion", serverRegion)
        .addField("serverZone", serverZone)
        .addField("serverApp", serverApp)
        .addField("serverCluster", serverCluster)
        .addField("serverAsg", serverAsg)
        .addField("serverNode", serverNode)
        .addField("remoteAddress", remoteAddress)
        .addField("remotePort", remotePort)
        .addField("attempt", attempt)
        .addField("attemptFinal", attemptFinal)
        .addField("result", result)
        .addField("source", source)
        .addField("status", status)
        .addField("statusDetail", statusDetail)
        .addField("exceptionClass", getExceptionClass())
        .addField("exceptionMessage", getExceptionMessage())
        .addField("httpStatus", httpStatus)
        .addField("requestContentLength", requestContentLength)
        .addField("responseContentLength", responseContentLength)
        .addField("requestHeaders", requestHeaders)
        .addField("responseHeaders", responseHeaders)
        .addField("additionalTags", additionalTags)
        .endObject()
        .toString();
  }

  /**
   * Resets this log entry so the instance can be reused. This helps to reduce allocations.
   */
  void reset() {
    logger = null;
    level = Level.DEBUG;
    marker = null;
    startTime = -1L;
    startNanos = -1L;
    latency = -1L;
    owner = null;
    result = null;
    source = null;
    protocol = null;
    status = null;
    statusDetail = null;
    exception = null;
    attempt = null;
    attemptFinal = null;
    vip = null;
    endpoint = null;
    method = null;
    clientRegion = null;
    clientZone = null;
    clientApp = null;
    clientCluster = null;
    clientAsg = null;
    clientNode = null;
    serverRegion = null;
    serverZone = null;
    serverApp = null;
    serverCluster = null;
    serverAsg = null;
    serverNode = null;
    httpStatus = -1;
    uri = null;
    path = null;
    requestContentLength = -1L;
    responseContentLength = -1L;
    requestHeaders.clear();
    responseHeaders.clear();
    remoteAddress = null;
    remotePort = -1;
    disableMetrics = false;
    additionalTags.clear();
    builder.delete(0, builder.length());
    inflightId = null;
  }

  /**
   * Partially reset this log entry so it can be used for another request attempt. Any
   * attributes that can change for a given request need to be cleared.
   */
  void resetForRetry() {
    startTime = -1L;
    startNanos = -1L;
    latency = -1L;
    result = null;
    status = null;
    statusDetail = null;
    exception = null;
    attempt = null;
    attemptFinal = null;
    vip = null;
    serverRegion = null;
    serverZone = null;
    serverApp = null;
    serverCluster = null;
    serverAsg = null;
    serverNode = null;
    httpStatus = -1;
    requestContentLength = -1L;
    responseContentLength = -1L;
    requestHeaders.clear();
    responseHeaders.clear();
    remoteAddress = null;
    remotePort = -1;
    builder.delete(0, builder.length());
    inflightId = null;
  }

  /**
   * Apply a mapping function to this log entry. This method is mostly used to allow the
   * final mapping to be applied to the entry without breaking the operator chaining.
   */
  public <T> T convert(Function<IpcLogEntry, T> mapper) {
    return mapper.apply(this);
  }

  private static boolean notNullOrEmpty(String s) {
    return s != null && !s.isEmpty();
  }

  private static class Header {
    private final String name;
    private final String value;

    Header(String name, String value) {
      this.name = name;
      this.value = value;
    }

    String name() {
      return name;
    }

    String value() {
      return value;
    }
  }

  private static class JsonStringBuilder {
    private final StringBuilder builder;
    private boolean firstEntry = true;

    JsonStringBuilder(StringBuilder builder) {
      this.builder = builder;
    }

    JsonStringBuilder startObject() {
      builder.append('{');
      return this;
    }

    JsonStringBuilder endObject() {
      builder.append('}');
      return this;
    }

    private void addSep() {
      if (firstEntry) {
        firstEntry = false;
      } else {
        builder.append(',');
      }
    }

    JsonStringBuilder addField(String k, String v) {
      if (notNullOrEmpty(v)) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":\"");
        escapeAndAppend(builder, v);
        builder.append('"');
      }
      return this;
    }

    JsonStringBuilder addField(String k, Tag tag) {
      if (tag != null) {
        addField(k, tag.value());
      }
      return this;
    }

    JsonStringBuilder addField(String k, int v) {
      if (v >= 0) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":").append(v);
      }
      return this;
    }

    JsonStringBuilder addField(String k, long v) {
      if (v >= 0L) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":").append(v);
      }
      return this;
    }

    JsonStringBuilder addField(String k, double v) {
      if (v >= 0.0) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":").append(v);
      }
      return this;
    }

    JsonStringBuilder addField(String k, List<Header> headers) {
      if (!headers.isEmpty()) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":[");

        boolean first = true;
        for (Header h : headers) {
          if (first) {
            first = false;
          } else {
            builder.append(',');
          }
          builder.append("{\"name\":\"");
          escapeAndAppend(builder, h.name());
          builder.append("\",\"value\":\"");
          escapeAndAppend(builder, h.value());
          builder.append("\"}");
        }
        builder.append(']');
      }
      return this;
    }

    JsonStringBuilder addField(String k, Map<String, String> tags) {
      if (!tags.isEmpty()) {
        addSep();
        builder.append('"');
        escapeAndAppend(builder, k);
        builder.append("\":{");

        boolean first = true;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
          if (first) {
            first = false;
          } else {
            builder.append(',');
          }
          builder.append('"');
          escapeAndAppend(builder, entry.getKey());
          builder.append("\":\"");
          escapeAndAppend(builder, entry.getValue());
          builder.append('"');
        }
        builder.append('}');
      }
      return this;
    }

    private void escapeAndAppend(StringBuilder builder, String str) {
      int length = str.length();
      for (int i = 0; i < length; ++i) {
        char c = str.charAt(i);
        switch (c) {
          case '"':
            builder.append("\\\"");
            break;
          case '\\':
            builder.append("\\\\");
            break;
          case '\b':
            builder.append("\\b");
            break;
          case '\f':
            builder.append("\\f");
            break;
          case '\n':
            builder.append("\\n");
            break;
          case '\r':
            builder.append("\\r");
            break;
          case '\t':
            builder.append("\\t");
            break;
          default:
            // Ignore control characters that are not matched explicitly above
            if (!Character.isISOControl(c)) {
              builder.append(c);
            }
            break;
        }
      }
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }
}
