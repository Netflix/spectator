/*
 * Copyright 2014-2018 Netflix, Inc.
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
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  private String protocol;

  private IpcStatus status;
  private String statusDetail;
  private Throwable exception;

  private IpcAttempt attempt;
  private IpcAttemptFinal attemptFinal;

  private String vip;
  private String endpoint;

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

  private String httpMethod;
  private int httpStatus;

  private String uri;
  private String path;

  private List<Header> requestHeaders = new ArrayList<>();
  private List<Header> responseHeaders = new ArrayList<>();

  private String remoteAddress;
  private int remotePort;

  private Map<String, String> additionalTags = new HashMap<>();

  private StringBuilder builder = new StringBuilder();

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
    if (registry != null) {
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
   * @deprecated Use {@link #withStatus(IpcStatus)} instead. This method will be removed in
   * January of 2019.
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
   * @deprecated Use {@link #withStatusDetail(String)} instead. This method will be removed in
   * January of 2019.
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
    this.httpMethod = method;
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
    } else {
      this.requestHeaders.add(new Header(name, value));
    }
    return this;
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
    } else {
      this.responseHeaders.add(new Header(name, value));
    }
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

  private void putTag(Map<String, String> tags, Tag tag) {
    if (tag != null) {
      tags.put(tag.key(), tag.value());
    }
  }

  private void putTag(Map<String, String> tags, String k, String v) {
    if (!isNullOrEmpty(v)) {
      String value = logger.limiterForKey(k).apply(v);
      tags.put(k, value);
    }
  }

  private IpcResult getResult() {
    if (result == null) {
      result = status == null ? IpcResult.success : status.result();
    }
    return result;
  }

  private IpcStatus getStatus() {
    if (status == null) {
      status = (result == IpcResult.success) ? IpcStatus.success : IpcStatus.unexpected_error;
    }
    return status;
  }

  private IpcAttempt getAttempt() {
    if (attempt == null) {
      attempt = IpcAttempt.forAttemptNumber(1);
    }
    return attempt;
  }

  private IpcAttemptFinal getAttemptFinal() {
    if (attemptFinal == null) {
      attemptFinal = IpcAttemptFinal.is_true;
    }
    return attemptFinal;
  }

  private String getEndpoint() {
    return (endpoint == null) ? "unknown" : endpoint;
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
    putTag(tags, getResult());
    putTag(tags, getStatus());

    if (isClient()) {
      // Required for client, should be null on server
      putTag(tags, getAttempt());
      putTag(tags, getAttemptFinal());

      // Optional for client
      putTag(tags, IpcTagKey.serverRegion.key(), serverRegion);
      putTag(tags, IpcTagKey.serverZone.key(), serverZone);
      putTag(tags, IpcTagKey.serverApp.key(), serverApp);
      putTag(tags, IpcTagKey.serverCluster.key(), serverCluster);
      putTag(tags, IpcTagKey.serverAsg.key(), serverAsg);
    } else {
      // Optional for server
      putTag(tags, IpcTagKey.clientRegion.key(), clientRegion);
      putTag(tags, IpcTagKey.clientZone.key(), clientZone);
      putTag(tags, IpcTagKey.clientApp.key(), clientApp);
      putTag(tags, IpcTagKey.clientCluster.key(), clientCluster);
      putTag(tags, IpcTagKey.clientAsg.key(), clientAsg);
    }

    // Optional for both client and server
    putTag(tags, IpcTagKey.endpoint.key(), getEndpoint());
    putTag(tags, IpcTagKey.vip.key(), vip);
    putTag(tags, IpcTagKey.protocol.key(), protocol);
    putTag(tags, IpcTagKey.statusDetail.key(), statusDetail);
    putTag(tags, IpcTagKey.httpStatus.key(), Integer.toString(httpStatus));
    putTag(tags, IpcTagKey.httpMethod.key(), httpMethod);

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
    Id clientCall = createCallId(IpcMetric.clientCall.metricName());
    PercentileTimer.builder(registry)
        .withId(clientCall)
        .build()
        .record(getLatency(), TimeUnit.NANOSECONDS);
  }

  private void recordServerMetrics() {
    Id serverCall = createCallId(IpcMetric.serverCall.metricName());
    PercentileTimer.builder(registry)
        .withId(serverCall)
        .build()
        .record(getLatency(), TimeUnit.NANOSECONDS);
  }

  /**
   * Log the request. This entry will potentially be reused after this is called. The user
   * should not attempt any further modifications to the state of this entry.
   */
  public void log() {
    if (logger != null) {
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

  @Override
  public String toString() {
    return new JsonStringBuilder(builder)
        .startObject()
        .addField("owner", owner)
        .addField("start", startTime)
        .addField("latency", getLatency() / 1e9)
        .addField("protocol", protocol)
        .addField("uri", uri)
        .addField("path", path)
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
        .addField("status", status)
        .addField("statusDetail", statusDetail)
        .addField("exceptionClass", getExceptionClass())
        .addField("exceptionMessage", getExceptionMessage())
        .addField("httpMethod", httpMethod)
        .addField("httpStatus", httpStatus)
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
    protocol = null;
    status = null;
    statusDetail = null;
    exception = null;
    attempt = null;
    attemptFinal = null;
    vip = null;
    endpoint = null;
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
    httpMethod = null;
    httpStatus = -1;
    uri = null;
    path = null;
    requestHeaders.clear();
    responseHeaders.clear();
    remoteAddress = null;
    remotePort = -1;
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

  private static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
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
      if (!isNullOrEmpty(v)) {
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
