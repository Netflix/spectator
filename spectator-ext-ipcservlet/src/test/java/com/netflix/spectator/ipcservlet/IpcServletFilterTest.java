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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.IpcMetric;
import com.netflix.spectator.ipc.http.HttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.EnumSet;

import static com.netflix.spectator.ipcservlet.TestUtils.*;

public class IpcServletFilterTest {

  private static Server server;
  private static URI baseUri;

  @BeforeAll
  public static void init() throws Exception {
    server = new Server(new InetSocketAddress("localhost", 0));
    ServletHandler handler = new ServletHandler();
    handler.addServletWithMapping(OkServlet.class, "/test/foo/*");
    handler.addServletWithMapping(OkServlet.class, "/api/*");
    handler.addServletWithMapping(BadRequestServlet.class, "/bad/*");
    handler.addServletWithMapping(FailServlet.class, "/throw/*");
    handler.addServletWithMapping(CustomEndpointServlet.class, "/endpoint/*");
    handler.addServletWithMapping(OkServlet.class, "/*");
    handler.addFilterWithMapping(IpcServletFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    server.setHandler(handler);
    server.start();
    baseUri = server.getURI();
  }

  @AfterAll
  public static void shutdown() throws Exception {
    server.stop();
  }

  private Registry registry;
  private HttpClient client;

  @BeforeEach
  public void before() {
    registry = new DefaultRegistry();
    client = HttpClient.create(new IpcLogger(registry));
    Spectator.globalRegistry().removeAll();
    Spectator.globalRegistry().add(registry);
  }

  @Test
  public void validateIdTest() throws Exception {
    client.get(baseUri.resolve("/test/foo/12345?q=54321")).send();
    checkResult(registry, "success");
    checkStatus(registry, "200");
    checkMethod(registry, "get");
    checkEndpoint(registry, "/test/foo");
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateIdApi() throws Exception {
    client.get(baseUri.resolve("/api/v1/asg/12345")).send();
    checkEndpoint(registry, "/api");
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateIdRoot() throws Exception {
    client.get(baseUri.resolve("/12345")).send();
    checkEndpoint(registry, "/");
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateIdBadRequest() throws Exception {
    client.post(baseUri.resolve("/bad/12345")).send();
    checkResult(registry, "failure");
    checkErrorReason(registry, null);
    checkStatus(registry, "400");
    checkMethod(registry, "post");
    checkEndpoint(registry, "/bad");
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateIdThrow() throws Exception {
    client.get(baseUri.resolve("/throw/12345")).send();
    checkResult(registry, "failure");
    checkClientErrorReason(registry, null);
    checkServerErrorReason(registry, "RuntimeException");
    checkMethod(registry, "get");
    checkClientEndpoint(registry, "_throw_-");
    checkServerEndpoint(registry, "/throw");
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateIdCustom() throws Exception {
    client.delete(baseUri.resolve("/endpoint/foo/12345?q=54321")).send();
    checkResult(registry, "success");
    checkStatus(registry, "200");
    checkMethod(registry, "delete");
    checkEndpoint(registry, "/servlet"); // header set in the servlet
    IpcMetric.validate(registry, true);
  }

  public static class OkServlet extends HttpServlet {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(200);
    }
  }

  public static class BadRequestServlet extends HttpServlet {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(400);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(400);
    }
  }

  public static class FailServlet extends HttpServlet {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
      throw new RuntimeException("something bad happened");
    }
  }

  public static class CustomEndpointServlet extends HttpServlet {
    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(200);
      response.addHeader("Netflix-Endpoint", "/servlet");
    }
  }
}
