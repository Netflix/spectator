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
package com.netflix.spectator.ipcservlet;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.http.HttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Singleton;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.EnumSet;

import static com.netflix.spectator.ipcservlet.TestUtils.*;

@RunWith(JUnit4.class)
public class GuiceServletFilterTest {

  // https://github.com/google/guice/issues/807

  private static Server server;
  private static URI baseUri;

  @BeforeClass
  public static void init() throws Exception {
    server = new Server(new InetSocketAddress("localhost", 0));
    ServletContextHandler handler = new ServletContextHandler(server, "/");
    handler.addEventListener(new TestListener());
    handler.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
    server.setHandler(handler);
    server.start();
    baseUri = server.getURI();
  }

  @AfterClass
  public static void shutdown() throws Exception {
    server.stop();
  }

  private Registry registry;
  private HttpClient client;

  @Before
  public void before() {
    registry = new DefaultRegistry();
    client = HttpClient.create(new IpcLogger(registry));
    Spectator.globalRegistry().removeAll();
    Spectator.globalRegistry().add(registry);
  }

  @Test
  public void validateIdTest() throws Exception {
    client.get(baseUri.resolve("/test/foo/12345")).send();
    checkEndpoint(registry, "/test/foo");
  }

  @Test
  public void validateIdApi() throws Exception {
    client.get(baseUri.resolve("/api/v1/asgs/12345")).send();
    checkEndpoint(registry, "/api");
  }

  @Test
  public void validateIdRoot() throws Exception {
    client.get(baseUri.resolve("/12345")).send();
    checkEndpoint(registry, "/");
  }

  @Singleton
  public static class TestServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(200);
    }
  }

  @Singleton
  public static class TestListener extends GuiceServletContextListener {

    @Override
    protected Injector getInjector() {
      return Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(Registry.class).toInstance(Spectator.globalRegistry());
            }
          },
          new ServletModule() {
            @Override
            protected void configureServlets() {
              serve("/test/foo/*").with(TestServlet.class);
              serve("/api/*").with(TestServlet.class);
              serve("/*").with(TestServlet.class);
              filter("/*").through(IpcServletFilter.class);
            }
          }
      );
    }
  }
}
