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
package com.netflix.spectator.ipcservlet;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.ipc.IpcLogEntry;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.NetflixHeader;
import com.netflix.spectator.ipc.NetflixHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

/**
 * Servlet filter implementation that provides common IPC metrics for filtered requests.
 */
@Singleton
public class IpcServletFilter implements Filter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IpcServletFilter.class);

  private final IpcLogger ipcLogger;
  private final Map<String, String> netflixHeaders;

  /**
   * Create a new instance using the global registry. This is typically used when defining
   * the filter in the {@code web.xml} file and it is created by the container.
   */
  public IpcServletFilter() {
    this(Spectator.globalRegistry());
  }

  /** Create a new instance using the specified registry. */
  @Inject
  public IpcServletFilter(Registry registry) {
    this.ipcLogger = new IpcLogger(registry, LOGGER);
    this.netflixHeaders = NetflixHeaders.extractFromEnvironment();
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      HttpServletRequest httpReq = (HttpServletRequest) request;
      HttpServletResponse httpRes = (HttpServletResponse) response;

      String endpoint = getEndpoint(httpReq);

      IpcLogEntry entry = ipcLogger.createServerEntry()
          .withOwner("spectator")
          .withHttpMethod(httpReq.getMethod())
          .withUri(httpReq.getRequestURI(), httpReq.getRequestURI());
      addRequestHeaders(httpReq, entry);

      entry.markStart();
      try {
        chain.doFilter(request, response);
        entry.markEnd().withHttpStatus(httpRes.getStatus());
      } catch (Throwable t) {
        entry.markEnd()
            .withException(t)
            .withHttpStatus(500);
        throw t;
      } finally {
        addNetflixHeaders(httpRes, endpoint);
        addResponseHeaders(httpRes, entry);
        entry.log();
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  private String getEndpoint(HttpServletRequest httpReq) {
    String servletPath = ServletPathHack.getServletPath(httpReq);
    String endpoint = (servletPath == null || servletPath.isEmpty())
        ? "/"
        : servletPath;
    return sanitizeHeaderValue(endpoint);
  }

  private String sanitizeHeaderValue(String value) {
    // Remove CR and LF characters to prevent HTTP response splitting
    return value.replaceAll("[\\r\\n]", "");
  }

  private void addNetflixHeaders(HttpServletResponse httpRes, String endpoint) {
    addIfNotPresent(httpRes, NetflixHeader.Endpoint.headerName(), endpoint);
    for (Map.Entry<String, String> entry : netflixHeaders.entrySet()) {
      addIfNotPresent(httpRes, entry.getKey(), entry.getValue());
    }
  }

  private void addIfNotPresent(HttpServletResponse httpRes, String name, String value) {
    if (httpRes.getHeader(name) == null) {
      httpRes.addHeader(name, value);
    }
  }

  private void addRequestHeaders(HttpServletRequest httpReq, IpcLogEntry entry) {
    Enumeration<String> headers = httpReq.getHeaderNames();
    while (headers.hasMoreElements()) {
      String header = headers.nextElement();
      Enumeration<String> values = httpReq.getHeaders(header);
      while (values.hasMoreElements()) {
        entry.addRequestHeader(header, values.nextElement());
      }
    }
  }

  private void addResponseHeaders(HttpServletResponse httpRes, IpcLogEntry entry) {
    Collection<String> headers = httpRes.getHeaderNames();
    for (String header : headers) {
      Collection<String> values = httpRes.getHeaders(header);
      for (String value : values) {
        entry.addResponseHeader(header, value);
      }
    }
  }

  //
  // In the servlet-api 4.x versions there are default implementations of the methods
  // below. To avoid AbstractMethodErrors when running on older versions, we explicitly
  // override them with empty implementations.
  //

  @Override public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override public void destroy() {
  }
}
