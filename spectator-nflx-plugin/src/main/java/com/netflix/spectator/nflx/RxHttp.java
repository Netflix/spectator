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
package com.netflix.spectator.nflx;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.spectator.sandbox.HttpLogEntry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.timeout.ReadTimeoutException;
import iep.io.reactivex.netty.RxNetty;
import iep.io.reactivex.netty.pipeline.PipelineConfigurator;
import iep.io.reactivex.netty.pipeline.PipelineConfiguratorComposite;
import iep.io.reactivex.netty.pipeline.ssl.DefaultFactories;
import iep.io.reactivex.netty.protocol.http.HttpObjectAggregationConfigurator;
import iep.io.reactivex.netty.protocol.http.client.HttpClient;
import iep.io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import iep.io.reactivex.netty.protocol.http.client.HttpClientPipelineConfigurator;
import iep.io.reactivex.netty.protocol.http.client.HttpClientRequest;
import iep.io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import iep.rx.Observable;
import iep.rx.functions.Action0;
import iep.rx.functions.Action1;
import iep.rx.functions.Func1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Helper for some simple uses of rxnetty with eureka. Only intended for use within the spectator
 * plugin.
 */
public final class RxHttp {

  private RxHttp() {
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(RxHttp.class);

  private static final Pattern NIWS_URI = Pattern.compile("niws://([^/]+).*");

  private static final Pattern VIP_URI = Pattern.compile("vip://([^:]+):([^/]+).*");

  private static final int MIN_COMPRESS_SIZE = 512;

  private static HttpClientRequest<ByteBuf> compress(
      ClientConfig clientCfg, HttpClientRequest<ByteBuf> req, byte[] entity) {
    if (entity.length >= MIN_COMPRESS_SIZE && clientCfg.gzipEnabled()) {
      req.withHeader(HttpHeaders.Names.CONTENT_ENCODING, HttpHeaders.Values.GZIP);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
        gzip.write(entity);
      } catch (IOException e) {
        // This isn't expected to occur
        throw new RuntimeException("failed to gzip request payload", e);
      }
      req.withContent(baos.toByteArray());
    } else {
      req.withContent(entity);
    }
    return req;
  }

  /** Create a log entry for an rxnetty request. */
  public static HttpLogEntry create(HttpClientRequest<ByteBuf> req) {
    HttpLogEntry entry = new HttpLogEntry()
        .withMethod(req.getMethod().name())
        .withRequestUri(URI.create(req.getUri()))
        .withRequestContentLength(req.getHeaders().getContentLength(-1));

    for (Map.Entry<String, String> h : req.getHeaders().entries()) {
      entry.withRequestHeader(h.getKey(), h.getValue());
    }

    return entry;
  }

  private static HttpLogEntry create(ClientConfig cfg, HttpClientRequest<ByteBuf> req) {
    return create(req)
        .withClientName(cfg.name())
        .withOriginalUri(cfg.originalUri())
        .withMaxAttempts(cfg.numRetries() + 1);
  }

  private static void nextAttempt(HttpLogEntry entry, int attempt, HttpClientRequest<ByteBuf> req) {
    entry.withAttempt(attempt)
        .withMethod(req.getMethod().name())
        .withRequestUri(URI.create(req.getUri()))
        .withRequestContentLength(req.getHeaders().getContentLength(-1));

    for (Map.Entry<String, String> h : req.getHeaders().entries()) {
      entry.withRequestHeader(h.getKey(), h.getValue());
    }
  }

  private static void update(HttpLogEntry entry, HttpClientResponse<ByteBuf> res) {
    int code = res.getStatus().code();
    boolean canRetry = (code == 429 || code >= 500);
    entry.mark("received-response")
        .withStatusCode(code)
        .withStatusReason(res.getStatus().reasonPhrase())
        .withResponseContentLength(res.getHeaders().getContentLength(-1))
        .withCanRetry(canRetry);

    for (Map.Entry<String, String> h : res.getHeaders().entries()) {
      entry.withResponseHeader(h.getKey(), h.getValue());
    }
  }

  private static void update(HttpLogEntry entry, Throwable t) {
    boolean canRetry = (t instanceof ConnectException || t instanceof ReadTimeoutException);
    entry.mark("received-error").withException(t).withCanRetry(canRetry);
  }

  /**
   * Perform a GET request.
   *
   * @param uri
   *     Location to send the request.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> get(String uri) {
    return get(URI.create(uri));
  }

  /**
   * Perform a GET request.
   *
   * @param uri
   *     Location to send the request.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> get(URI uri) {
    final ClientConfig clientCfg = getConfigForUri(uri);
    final List<Server> servers = getServers(clientCfg);
    return execute(clientCfg, servers, HttpClientRequest.createGet(relative(clientCfg.uri())));
  }

  /**
   * Perform a POST request.
   *
   * @param uri
   *     Location to send the data.
   * @param contentType
   *     MIME type for the request payload.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>>
  post(URI uri, String contentType, byte[] entity) {
    final ClientConfig clientCfg = getConfigForUri(uri);
    final List<Server> servers = getServers(clientCfg);
    HttpClientRequest<ByteBuf> req = HttpClientRequest.createPost(relative(clientCfg.uri()))
        .withHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
    return execute(clientCfg, servers, compress(clientCfg, req, entity));
  }

  /**
   * Perform a POST request with {@code Content-Type: application/json}.
   *
   * @param uri
   *     Location to send the data.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> postJson(URI uri, byte[] entity) {
    return post(uri, "application/json", entity);
  }

  /**
   * Perform a POST request with {@code Content-Type: application/json}.
   *
   * @param uri
   *     Location to send the data.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> postJson(URI uri, String entity) {
    return postJson(uri, getBytes(entity));
  }

  /**
   * Perform a POST request with form data. The body will be extracted from the query string
   * in the URI.
   *
   * @param uri
   *     Location to send the data.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> postForm(URI uri) {
    Preconditions.checkNotNull(uri.getRawQuery(), "uri.query");
    byte[] entity = getBytes(uri.getRawQuery());
    return post(uri, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED, entity);
  }

  /**
   * Perform a PUT request.
   *
   * @param uri
   *     Location to send the data.
   * @param contentType
   *     MIME type for the request payload.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>>
  put(URI uri, String contentType, byte[] entity) {
    final ClientConfig clientCfg = getConfigForUri(uri);
    final List<Server> servers = getServers(clientCfg);
    HttpClientRequest<ByteBuf> req = HttpClientRequest.createPut(relative(clientCfg.uri()))
        .withHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
    return execute(clientCfg, servers, compress(clientCfg, req, entity));
  }

  /**
   * Perform a PUT request with {@code Content-Type: application/json}.
   *
   * @param uri
   *     Location to send the data.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> putJson(URI uri, byte[] entity) {
    return put(uri, "application/json", entity);
  }

  /**
   * Perform a PUT request with {@code Content-Type: application/json}.
   *
   * @param uri
   *     Location to send the data.
   * @param entity
   *     Data to send.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> putJson(URI uri, String entity) {
    return putJson(uri, getBytes(entity));
  }

  /**
   * Perform a DELETE request.
   *
   * @param uri
   *     Location to send the request.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> delete(String uri) {
    return delete(URI.create(uri));
  }

  /**
   * Perform a DELETE request.
   *
   * @param uri
   *     Location to send the request.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> delete(URI uri) {
    final ClientConfig clientCfg = getConfigForUri(uri);
    final List<Server> servers = getServers(clientCfg);
    return execute(clientCfg, servers, HttpClientRequest.createDelete(relative(clientCfg.uri())));
  }

  /**
   * Submit an HTTP request.
   *
   * @param req
   *     Request to execute. Note the content should be passed in separately not already passed
   *     to the request. The RxNetty request object doesn't provide a way to get the content
   *     out via the public api, so we need to keep it separate in case a new request object must
   *     be created.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>> submit(HttpClientRequest<ByteBuf> req) {
    return submit(req, (byte[]) null);
  }

  /**
   * Submit an HTTP request.
   *
   * @param req
   *     Request to execute. Note the content should be passed in separately not already passed
   *     to the request. The RxNetty request object doesn't provide a way to get the content
   *     out via the public api, so we need to keep it separate in case a new request object must
   *     be created.
   * @param entity
   *     Content data or null if no content is needed for the request body.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>>
  submit(HttpClientRequest<ByteBuf> req, String entity) {
    return submit(req, (entity == null) ? null : getBytes(entity));
  }

  /**
   * Submit an HTTP request.
   *
   * @param req
   *     Request to execute. Note the content should be passed in separately not already passed
   *     to the request. The RxNetty request object doesn't provide a way to get the content
   *     out via the public api, so we need to keep it separate in case a new request object must
   *     be created.
   * @param entity
   *     Content data or null if no content is needed for the request body.
   * @return
   *     Observable with the response of the request.
   */
  public static Observable<HttpClientResponse<ByteBuf>>
  submit(HttpClientRequest<ByteBuf> req, byte[] entity) {
    final URI uri = URI.create(req.getUri());
    final ClientConfig clientCfg = getConfigForUri(uri);
    final List<Server> servers = getServers(clientCfg);
    final String reqUri = relative(clientCfg.uri());
    final HttpClientRequest<ByteBuf> newReq = copy(req, reqUri);
    final HttpClientRequest<ByteBuf> finalReq = (entity == null)
        ? newReq
        : compress(clientCfg, newReq, entity);
    return execute(clientCfg, servers, finalReq);
  }

  /**
   * Execute an HTTP request.
   *
   * @param clientCfg
   *     Configuration settings for the request.
   * @param servers
   *     List of servers to attempt. The servers will be tried in order until a successful
   *     response or a non-retriable error occurs. For status codes 429 and 503 the
   *     {@code Retry-After} header is honored. Otherwise back-off will be based on the
   *     {@code RetryDelay} config setting.
   * @param req
   *     Request to execute.
   * @return
   *     Observable with the response of the request.
   */
  static Observable<HttpClientResponse<ByteBuf>>
  execute(final ClientConfig clientCfg, final List<Server> servers, final HttpClientRequest<ByteBuf> req) {
    final HttpLogEntry entry = create(clientCfg, req);

    if (servers.isEmpty()) {
      final String msg = "empty server list for client " + clientCfg.name();
      return Observable.error(new IllegalStateException(msg));
    }

    if (clientCfg.gzipEnabled()) {
      req.withHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
    }

    final long backoffMillis = clientCfg.retryDelay();
    Observable<HttpClientResponse<ByteBuf>> observable = execute(entry, clientCfg, servers.get(0), req);
    for (int i = 1; i < servers.size(); ++i) {
      final Server server = servers.get(i);
      final long delay = backoffMillis << (i - 1);
      final int attempt = i + 1;
      observable = observable
          .flatMap(new RedirectHandler(entry, clientCfg, server, req))
          .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<HttpClientResponse<ByteBuf>>>() {
            @Override
            public Observable<HttpClientResponse<ByteBuf>> call(HttpClientResponse<ByteBuf> res) {
              final int code = res.getStatus().code();
              Observable<HttpClientResponse<ByteBuf>> resObs;
              if (code == 429 || code == 503) {
                final long retryDelay = getRetryDelay(res, delay);
                res.getContent().subscribe();
                entry.withAttempt(attempt);
                resObs = execute(entry, clientCfg, server, req);
                if (retryDelay > 0) {
                  resObs = resObs.delaySubscription(retryDelay, TimeUnit.MILLISECONDS);
                }
              } else if (code >= 500) {
                res.getContent().subscribe();
                entry.withAttempt(attempt);
                resObs = execute(entry, clientCfg, server, req);
              } else {
                resObs = Observable.just(res);
              }
              return resObs;
            }
          })
          .onErrorResumeNext(new Func1<Throwable, Observable<? extends HttpClientResponse<ByteBuf>>>() {
            @Override
            public Observable<? extends HttpClientResponse<ByteBuf>> call(Throwable throwable) {
              if (throwable instanceof ConnectException
                  || throwable instanceof ReadTimeoutException) {
                entry.withAttempt(attempt);
                return execute(entry, clientCfg, server, req);
              }
              return Observable.error(throwable);
            }
          });
    }

    return observable;
  }

  /**
   * Execute an HTTP request.
   *
   * @param clientCfg
   *     Configuration settings for the request.
   * @param server
   *     Server to send the request to.
   * @param req
   *     Request to execute.
   * @return
   *     Observable with the response of the request.
   */
  static Observable<HttpClientResponse<ByteBuf>>
  execute(final HttpLogEntry entry, ClientConfig clientCfg, Server server, HttpClientRequest<ByteBuf> req) {
    entry.withRemoteAddr(server.host()).withRemotePort(server.port());

    HttpClient.HttpClientConfig config = new HttpClient.HttpClientConfig.Builder()
        .readTimeout(clientCfg.readTimeout(), TimeUnit.MILLISECONDS)
        .userAgent(clientCfg.userAgent())
        .build();

    PipelineConfiguratorComposite<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>>
        pipelineCfg = new PipelineConfiguratorComposite<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>>(
        new HttpClientPipelineConfigurator<ByteBuf, ByteBuf>(),
        new HttpDecompressionConfigurator(),
        new HttpObjectAggregationConfigurator(clientCfg.aggregationLimit())
    );

    HttpClientBuilder<ByteBuf, ByteBuf> builder =
        RxNetty.<ByteBuf, ByteBuf>newHttpClientBuilder(server.host(), server.port())
            .pipelineConfigurator(pipelineCfg)
            .config(config)
            .channelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientCfg.connectTimeout());

    if (server.isSecure()) {
      builder.withSslEngineFactory(DefaultFactories.trustAll());
    }

    entry.mark("start");
    final HttpClient<ByteBuf, ByteBuf> client = builder.build();
    return client.submit(req)
        .doOnNext(new Action1<HttpClientResponse<ByteBuf>>() {
          @Override public void call(HttpClientResponse<ByteBuf> res) {
            update(entry, res);
            HttpLogEntry.logClientRequest(LOGGER, entry);
          }
        })
        .doOnError(new Action1<Throwable>() {
          @Override public void call(Throwable throwable) {
            update(entry, throwable);
            HttpLogEntry.logClientRequest(LOGGER, entry);
          }
        })
        .doOnTerminate(new Action0() {
          @Override public void call() {
            client.shutdown();
          }
        });
  }

  /**
   * Create a copy of a request object. It can only copy the method, uri, and headers so should
   * not be used for any request with a content already specified.
   */
  static HttpClientRequest<ByteBuf> copy(HttpClientRequest<ByteBuf> req, String uri) {
    HttpClientRequest<ByteBuf> newReq = HttpClientRequest.create(
        req.getHttpVersion(), req.getMethod(), uri);
    for (Map.Entry<String, String> h : req.getHeaders().entries()) {
      newReq.withHeader(h.getKey(), h.getValue());
    }
    return newReq;
  }

  private static long getRetryDelay(HttpClientResponse<ByteBuf> res, long dflt) {
    try {
      if (res.getHeaders().contains(HttpHeaders.Names.RETRY_AFTER)) {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
        int delaySeconds = res.getHeaders().getIntHeader(HttpHeaders.Names.RETRY_AFTER);
        return TimeUnit.MILLISECONDS.convert(delaySeconds, TimeUnit.SECONDS);
      }
    } catch (NumberFormatException e) {
      // We don't support the date version, so use dflt in this case
      return dflt;
    }
    return dflt;
  }

  /** We expect UTF-8 to always be supported. */
  private static byte[] getBytes(String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /** Create relative uri string with the path and query. */
  static String relative(URI uri) {
    String r = uri.getRawPath();
    if (uri.getRawQuery() != null) {
      r += "?" + uri.getRawQuery();
    }
    return r;
  }

  private static String fixPath(String path) {
    return (path.startsWith("/http://") || path.startsWith("/https://"))
        ? path.substring(1)
        : path;
  }

  private static ClientConfig getConfigForUri(URI uri) {
    Matcher m = null;
    ClientConfig cfg = null;
    switch (uri.getScheme()) {
      case "niws":
        m = NIWS_URI.matcher(uri.toString());
        if (m.matches()) {
          final URI newUri = URI.create(fixPath(uri.getRawPath()));
          cfg = new ClientConfig(m.group(1), null, uri, newUri);
        } else {
          throw new IllegalArgumentException("invalid niws uri: " + uri);
        }
        break;
      case "vip":
        m = VIP_URI.matcher(uri.toString());
        if (m.matches()) {
          cfg = new ClientConfig(m.group(1), m.group(2), uri, URI.create(uri.getRawPath()));
        } else {
          throw new IllegalArgumentException("invalid vip uri: " + uri);
        }
        break;
      default:
        cfg = new ClientConfig("default", null, uri, uri);
        break;
    }
    return cfg;
  }

  private static List<Server> getServers(ClientConfig clientCfg) {
    List<Server> servers = null;
    if (clientCfg.uri().isAbsolute()) {
      servers = getServersForUri(clientCfg, clientCfg.uri());
    } else {
      servers = getServersForVip(clientCfg, clientCfg.vip());
    }
    return servers;
  }

  private static List<Server> getServersForUri(ClientConfig clientCfg, URI uri) {
    final int numRetries = clientCfg.numRetries();
    final boolean secure = "https".equals(uri.getScheme());
    List<Server> servers = new ArrayList<>();
    servers.add(new Server(uri.getHost(), getPort(uri), secure));
    for (int i = 0; i < numRetries; ++i) {
      servers.add(new Server(uri.getHost(), getPort(uri), secure));
    }
    return servers;
  }

  private static List<Server> getServersForVip(ClientConfig clientCfg, String vip) {
    Preconditions.checkNotNull(vip, "vipAddress");
    DiscoveryClient discoClient = DiscoveryManager.getInstance().getDiscoveryClient();
    List<InstanceInfo> instances = discoClient.getInstancesByVipAddress(vip, clientCfg.isSecure());
    List<InstanceInfo> filtered = new ArrayList<>(instances.size());
    for (InstanceInfo info : instances) {
      if (info.getStatus() == InstanceInfo.InstanceStatus.UP) {
        filtered.add(info);
      }
    }
    Collections.shuffle(filtered);

    // If the number of instances is less than the number of attempts, retry multiple times
    // on previously used servers
    int numAttempts = clientCfg.numRetries() + 1;
    int numServers = filtered.size();

    if (numServers == 0) {
      throw new IllegalStateException("no UP servers for vip: " + vip);
    }

    List<Server> servers = new ArrayList<>();
    for (int i = 0; i < numAttempts; ++i) {
      InstanceInfo instance = filtered.get(i % numServers);
      servers.add(toServer(clientCfg, instance));
    }
    return servers;
  }

  /** Convert a eureka InstanceInfo object to a server. */
  static Server toServer(ClientConfig clientCfg, InstanceInfo instance) {
    String host = clientCfg.useIpAddress() ? instance.getIPAddr() : instance.getHostName();
    int dfltPort = clientCfg.isSecure() ? instance.getSecurePort() : instance.getPort();
    int port = clientCfg.port(dfltPort);
    return new Server(host, port, clientCfg.isSecure());
  }

  /**
   * Return the port taking care of handling defaults for http and https if not explicit in the
   * uri.
   */
  static int getPort(URI uri) {
    final int defaultPort = ("https".equals(uri.getScheme())) ? 443 : 80;
    return (uri.getPort() <= 0) ? defaultPort : uri.getPort();
  }

  private static class HttpDecompressionConfigurator implements PipelineConfigurator<ByteBuf, ByteBuf> {
    @Override public void configureNewPipeline(ChannelPipeline pipeline) {
      pipeline.addLast("deflater", new HttpContentDecompressor());
    }
  }

  /**
   * Represents a server to try and connect to.
   */
  public static class Server {
    private final String host;
    private final int port;
    private final boolean secure;

    /** Create a new instance. */
    public Server(String host, int port, boolean secure) {
      this.host = host;
      this.port = port;
      this.secure = secure;
    }

    /** Return the host name for the server. */
    public String host() {
      return host;
    }

    /** Return the port for the server. */
    public int port() {
      return port;
    }

    /** Return true if HTTPS should be used. */
    public boolean isSecure() {
      return secure;
    }
  }

  /** Configuration settings to use for making the request. */
  static class ClientConfig {

    private final String name;
    private final String vipAddress;
    private final URI originalUri;
    private final URI uri;

    /** Create a new instance. */
    ClientConfig(String name, String vipAddress, URI originalUri, URI uri) {
      this.name = name;
      this.vipAddress = vipAddress;
      this.originalUri = originalUri;
      this.uri = uri;
    }

    private String prop(String k) {
      return name + ".niws.client." + k;
    }

    /** Name of the client. */
    String name() {
      return name;
    }

    /** Original URI specified before selecting a specific server. */
    URI originalUri() {
      return originalUri;
    }

    /** URI for the request. */
    URI uri() {
      return uri;
    }

    /** Port to use for the connection. */
    int port(int dflt) {
      return Spectator.config().getInt(prop("Port"), dflt);
    }

    /** Maximum time to wait for a connection attempt in milliseconds. */
    int connectTimeout() {
      return Spectator.config().getInt(prop("ConnectTimeout"), 1000);
    }

    /** Maximum time to wait for reading data in milliseconds. */
    int readTimeout() {
      return Spectator.config().getInt(prop("ReadTimeout"), 30000);
    }

    /** Maximum number of redirects to follow. Set to 0 to disable. */
    int followRedirects() {
      return Spectator.config().getInt(prop("FollowRedirects"), 3);
    }

    /** Should HTTPS be used for the request? */
    boolean isSecure() {
      final boolean https = "https".equals(uri.getScheme());
      return https || Spectator.config().getBoolean(prop("IsSecure"), false);
    }

    /**
     * When getting a server list from eureka should the host name or ip address be used? The
     * default is to use the ip address and avoid the dns lookup.
     */
    boolean useIpAddress() {
      return Spectator.config().getBoolean(prop("UseIpAddress"), false);
    }

    /**
     * Should it attempt to compress the request body and automatically decompress the response
     * body?
     */
    boolean gzipEnabled() {
      return Spectator.config().getBoolean(prop("GzipEnabled"), true);
    }

    /** Max number of retries. */
    int numRetries() {
      return Spectator.config().getInt(prop("MaxAutoRetriesNextServer"), 2);
    }

    /**
     * Initial delay to use between retries if a throttled response (429 or 503) is received. The
     * delay will be doubled between each throttled attempt.
     */
    int retryDelay() {
      return Spectator.config().getInt(prop("RetryDelay"), 500);
    }

    /** Max size of the request body. Defaults to 10MB. */
    int aggregationLimit() {
      return Spectator.config().getInt(prop("AggregationLimit"), 10 * 1024 * 1024);
    }

    /** User agent string to use when making the request. */
    String userAgent() {
      return Spectator.config().get(prop("UserAgent"), "RxHttp");
    }

    /** VIP used to lookup a set of servers in eureka. */
    String vip() {
      return (vipAddress == null)
          ? Spectator.config().get(prop("DeploymentContextBasedVipAddresses"))
          : vipAddress;
    }
  }
}
