/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Helper for logging http request related information.
 */
public class HttpLogEntry {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpLogEntry.class);

  private static final Marker CLIENT = MarkerFactory.getMarker("http-client");
  private static final Marker SERVER = MarkerFactory.getMarker("http-server");

  private static final Registry REGISTRY = Spectator.globalRegistry();
  private static final Id COMPLETE = REGISTRY.createId("http.req.complete");
  private static final Id ATTEMPT = REGISTRY.createId("http.req.attempt");
  private static final Id REQ_HEADER_SIZE = REGISTRY.createId("http.req.headerSize");
  private static final Id REQ_ENTITY_SIZE = REGISTRY.createId("http.req.entitySize");
  private static final Id RES_HEADER_SIZE = REGISTRY.createId("http.res.headerSize");
  private static final Id RES_ENTITY_SIZE = REGISTRY.createId("http.res.entitySize");

  private static final BucketFunction BUCKETS =
      BucketFunctions.latency(maxLatency(), TimeUnit.MILLISECONDS);

  /**
   * Including the endpoint is useful, but we need to be careful about the number of
   * matches. A fixed prefix list is fairly easy to use and makes the number and set of matches
   * explicit.
   */
  private static final List<String> ENDPOINT_PREFIXES = parseEndpoints(endpointPrefixes());

  private static long maxLatency() {
    return Long.parseLong(System.getProperty("spectator.http.maxLatency", "8000"));
  }

  private static String endpointPrefixes() {
    return System.getProperty("spectator.http.endpointPrefixes", "/healthcheck");
  }

  private static List<String> parseEndpoints(String s) {
    String[] prefixes = (s == null) ? new String[] {} : s.split("[,\\s]+");
    List<String> buf = new ArrayList<>();
    for (String prefix : prefixes) {
      String tmp = prefix.trim();
      if (tmp.length() > 0) {
        buf.add(prefix);
      }
    }
    Collections.sort(buf);
    return buf;
  }

  private static String longestPrefixMatch(String path, String dflt) {
    if (path == null || path.length() == 0) {
      return dflt;
    }

    int length = 0;
    String longest = null;
    for (String prefix : ENDPOINT_PREFIXES) {
      if (path.startsWith(prefix) && prefix.length() > length) {
        longest = prefix;
        length = prefix.length();
      }
    }

    return (longest == null) ? dflt : longest;
  }

  /** Log a client request. */
  public static void logClientRequest(HttpLogEntry entry) {
    log(LOGGER, CLIENT, entry);
  }

  /**
   * Log a client request.
   * @deprecated Use {@link #logClientRequest(HttpLogEntry)} instead.
   */
  @Deprecated
  public static void logClientRequest(Logger logger, HttpLogEntry entry) {
    log(logger, CLIENT, entry);
  }

  /** Log a request received by a server. */
  public static void logServerRequest(HttpLogEntry entry) {
    log(LOGGER, SERVER, entry);
  }

  /**
   * Log a request received by a server.
   * @deprecated Use {@link #logServerRequest(HttpLogEntry)} instead.
   */
  @Deprecated
  public static void logServerRequest(Logger logger, HttpLogEntry entry) {
    log(logger, SERVER, entry);
  }

  private static void log(Logger logger, Marker marker, HttpLogEntry entry) {
    Id dimensions = REGISTRY.createId("tags")
        .withTag("mode", marker.getName())
        .withTag("status", entry.getStatusTag())
        .withTag("statusCode", entry.getStatusCodeTag())
        .withTag("method", entry.method);

    if (entry.clientName != null) {
      dimensions = dimensions.withTag("client", entry.clientName);
    }

    if (marker == SERVER && entry.requestUri != null) {
      String path = entry.requestUri.getPath();
      dimensions = dimensions.withTag("endpoint", longestPrefixMatch(path, "other"));
    }

    // Update stats for the final attempt after retries are exhausted
    if (!entry.canRetry || entry.attempt >= entry.maxAttempts) {
      BucketTimer.get(REGISTRY, COMPLETE.withTags(dimensions.tags()), BUCKETS)
          .record(entry.getOverallLatency(), TimeUnit.MILLISECONDS);
    }

    // Update stats for every actual http request
    BucketTimer.get(REGISTRY, ATTEMPT.withTags(dimensions.tags()), BUCKETS)
        .record(entry.getLatency(), TimeUnit.MILLISECONDS);
    REGISTRY.distributionSummary(REQ_HEADER_SIZE.withTags(dimensions.tags()))
        .record(entry.getRequestHeadersLength());
    REGISTRY.distributionSummary(REQ_ENTITY_SIZE.withTags(dimensions.tags()))
        .record(entry.requestContentLength);
    REGISTRY.distributionSummary(RES_HEADER_SIZE.withTags(dimensions.tags()))
        .record(entry.getResponseHeadersLength());
    REGISTRY.distributionSummary(RES_ENTITY_SIZE.withTags(dimensions.tags()))
        .record(entry.responseContentLength);

    // Write data out to logger if enabled. For many monitoring use-cases there tend to be
    // frequent requests that can be quite noisy so the log level is set to debug. This class is
    // mostly intended to generate something like an access log so it presumes users who want the
    // information will configure an appender based on the markers to send the data to a
    // dedicated file. Others shouldn't have to deal with the spam in the logs, so debug for the
    // level seems reasonable.
    if (logger.isDebugEnabled(marker)) {
      logger.debug(marker, entry.toString());
    }
  }

  /** Generate a new request id. */
  private static String newId() {
    return UUID.randomUUID().toString();
  }

  // Cannot be static constant, date format is not thread-safe
  private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  private String clientName = null;

  private String requestId = newId();

  private URI originalUri = null;
  private URI requestUri = null;
  private String method = null;
  private List<Header> requestHeaders = new ArrayList<>();
  private long requestContentLength = -1;

  private String remoteAddr = null;
  private int remotePort = -1;

  private String attemptId = requestId;
  private int attempt = 1;
  private int maxAttempts = -1;
  private boolean canRetry = false;

  private int redirect = 0;

  private Throwable exception = null;

  private int statusCode = -1;
  private String statusReason = null;
  private List<Header> responseHeaders = new ArrayList<>();
  private long responseContentLength = -1;

  private List<Event> events = new ArrayList<>();
  private long latency = -1;
  private long originalStart = -1;

  private void reset(int redir) {
    if (originalStart < 0 && !events.isEmpty()) {
      originalStart = events.get(0).timestamp();
    }
    requestHeaders.clear();
    requestContentLength = -1;
    remoteAddr = null;
    remotePort = -1;
    redirect = redir;
    exception = null;
    statusCode = -1;
    responseHeaders.clear();
    responseContentLength = -1;
    events.clear();
    latency = -1;
  }

  /** Set the name of the client, often used for clients to identify a particular config. */
  public HttpLogEntry withClientName(String name) {
    this.clientName = name;
    return this;
  }

  /**
   * Set the original uri. In the case of approaches with client-side load balancing this will
   * be some alias indicating the group of hosts. The request uri would indicate a specific host
   * used for an actual network request.
   */
  public HttpLogEntry withOriginalUri(URI uri) {
    this.originalUri = uri;
    return this;
  }

  /** Set the URI for the actual http request. */
  public HttpLogEntry withRequestUri(URI uri) {
    this.requestUri = uri;
    return this;
  }

  /** Set the method for the request. */
  public HttpLogEntry withMethod(String httpMethod) {
    this.method = httpMethod;
    return this;
  }

  /** Add a header that was on the request. */
  public HttpLogEntry withRequestHeader(String name, String value) {
    requestHeaders.add(new Header(name, value));
    return this;
  }

  /** Set the content-length for the request. */
  public HttpLogEntry withRequestContentLength(long size) {
    this.requestContentLength = size;
    return this;
  }

  /**
   * Set the remote address. For a client making a request this should be the server, for a
   * server receiving a request it should be the client.
   */
  public HttpLogEntry withRemoteAddr(String addr) {
    this.remoteAddr = addr;
    return this;
  }

  /**
   * Set the remote port. For a client making a request this should be the server, for a
   * server receiving a request it should be the client.
   */
  public HttpLogEntry withRemotePort(int port) {
    this.remotePort = port;
    return this;
  }

  /** Set the attempt if retries are used, should only be used after the initial request. */
  public HttpLogEntry withAttempt(int n) {
    this.attempt = n;
    this.attemptId = newId();
    reset(0);
    return this;
  }

  /** Set the attempt if redirect occurs, should only be used after the initial request. */
  public HttpLogEntry withRedirect(URI loc) {
    reset(redirect + 1);
    return withRequestUri(loc);
  }

  /** Set the max number of attempts that will be tried. */
  public HttpLogEntry withMaxAttempts(int attempts) {
    this.maxAttempts = attempts;
    return this;
  }

  /** Set to true if the error is one that can be retried. */
  public HttpLogEntry withCanRetry(boolean retry) {
    this.canRetry = retry;
    return this;
  }

  /** Set the exception if there is a failure such as a connect timeout. */
  public HttpLogEntry withException(Throwable t) {
    exception = t;
    return this;
  }

  /** Set the status code from the response. */
  public HttpLogEntry withStatusCode(int code) {
    this.statusCode = code;
    return this;
  }

  /** Set the status reason from the response. */
  public HttpLogEntry withStatusReason(String reason) {
    this.statusReason = reason;
    return this;
  }

  /** Add a header that was on the response. */
  public HttpLogEntry withResponseHeader(String name, String value) {
    responseHeaders.add(new Header(name, value));
    return this;
  }

  /** Set the content-length from the response. */
  public HttpLogEntry withResponseContentLength(long size) {
    this.responseContentLength = size;
    return this;
  }

  /** Set the latency for the request. */
  public HttpLogEntry withRequestLatency(long t) {
    this.latency = t;
    return this;
  }

  /** Mark the time an event occurred. Should include at least the start and end of a request. */
  public HttpLogEntry mark(String name) {
    events.add(new Event(name, System.currentTimeMillis()));
    return this;
  }

  /** Mark the time an event occurred. Should include at least the start and end of a request. */
  public HttpLogEntry mark(String name, long timestamp) {
    events.add(new Event(name, timestamp));
    return this;
  }

  /** Return the request id. */
  public String getRequestId() {
    return requestId;
  }

  /** Return the attempt id. */
  public String getAttemptId() {
    return attemptId;
  }

  /**
   * Return the latency for the request. If not explicitly set it will be calculated from the
   * events.
   */
  public long getLatency() {
    if (latency >= 0L) {
      return latency;
    } else if (events.size() >= 2) {
      return events.get(events.size() - 1).timestamp() - events.get(0).timestamp();
    } else {
      return -1;
    }
  }

  /** Return the overall latency for a group of requests including all retries. */
  public long getOverallLatency() {
    if (maxAttempts <= 1 || originalStart < 0) {
      return getLatency();
    } else if (events.isEmpty()) {
      return -1;
    } else {
      return events.get(events.size() - 1).timestamp() - originalStart;
    }
  }

  /** Return the starting time for the request. */
  public String getStartTime() {
    return events.isEmpty()
        ? "unknown"
        : isoDate.format(new Date(events.get(0).timestamp()));
  }

  private int getHeadersLength(List<Header> headers) {
    int size = 0;
    for (Header h : headers) {
      size += h.numBytes();
    }
    return size;
  }

  /** Return the size in bytes of all request headers. */
  public int getRequestHeadersLength() {
    return getHeadersLength(requestHeaders);
  }

  /** Return the size in bytes of all response headers. */
  public int getResponseHeadersLength() {
    return getHeadersLength(responseHeaders);
  }

  /** Return a time line based on marked events. */
  public String getTimeline() {
    StringBuilder builder = new StringBuilder();
    for (Event event : events) {
      builder.append(event.name()).append(":").append(event.timestamp()).append(";");
    }
    return builder.toString();
  }

  private String getExceptionClass() {
    return (exception == null)
        ? "null"
        : exception.getClass().getName();
  }

  private String getExceptionMessage() {
    return (exception == null)
        ? "null"
        : exception.getMessage();
  }

  private String getHeaders(List<Header> headers) {
    StringBuilder builder = new StringBuilder();
    for (Header h : headers) {
      builder.append(h.name()).append(':').append(h.value()).append(';');
    }
    return builder.toString();
  }

  /** Return a summary of all request headers. */
  public String getRequestHeaders() {
    return getHeaders(requestHeaders);
  }

  /** Return a summary of all response headers. */
  public String getResponseHeaders() {
    return getHeaders(responseHeaders);
  }

  private String getStatusTag() {
    return (exception != null)
        ? exception.getClass().getSimpleName()
        : (statusCode >= 100 ? (statusCode / 100) + "xx" : "unknown");
  }

  private String getStatusCodeTag() {
    return (exception != null)
        ? exception.getClass().getSimpleName()
        : (statusCode >= 100 ? "" + statusCode : "unknown");
  }

  @Override public String toString() {
    return new StringBuilder()
        .append(clientName).append('\t')
        .append(getStartTime()).append('\t')
        .append(getLatency()).append('\t')
        .append(getOverallLatency()).append('\t')
        .append(getTimeline()).append('\t')
        .append(method).append('\t')
        .append(originalUri).append('\t')
        .append(requestUri).append('\t')
        .append(remoteAddr).append('\t')
        .append(remotePort).append('\t')
        .append(statusCode).append('\t')
        .append(statusReason).append('\t')
        .append(getExceptionClass()).append('\t')
        .append(getExceptionMessage()).append('\t')
        .append(getRequestHeadersLength()).append('\t')
        .append(requestContentLength).append('\t')
        .append(getResponseHeadersLength()).append('\t')
        .append(responseContentLength).append('\t')
        .append(getRequestHeaders()).append('\t')
        .append(getResponseHeaders()).append('\t')
        .append(redirect).append('\t')
        .append(attempt).append('\t')
        .append(maxAttempts)
        .toString();
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

    int numBytes() {
      return name.length() + ": ".length() + value.length() + "\n".length();
    }
  }

  private static class Event {
    private final String name;
    private final long timestamp;

    Event(String name, long timestamp) {
      this.name = name;
      this.timestamp = timestamp;
    }

    String name() {
      return name;
    }

    long timestamp() {
      return timestamp;
    }
  }
}
