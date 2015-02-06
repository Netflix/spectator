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
package com.netflix.spectator.http;

import com.netflix.config.ConfigurationManager;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;

@RunWith(JUnit4.class)
public class ClientConfigTest {

  private static void clear(String k) {
    ConfigurationManager.getConfigInstance().clearProperty(k);
  }

  private static void set(String k, String v) {
    ConfigurationManager.getConfigInstance().setProperty(k, v);
  }

  private ClientConfig cfg;

  @Before
  public void before() {
    clear("foo.niws.client.UseIpAddress");
    clear("niws.client.UseIpAddress");
    final URI uri = URI.create("/test");
    cfg = new ClientConfig("foo", "foo:7001", uri, uri);
  }

  @Test
  public void useIpAddressNoProp() {
    Assert.assertEquals(cfg.useIpAddress(), false);
  }

  @Test
  public void useIpAddressDefaultProp() {
    set("niws.client.UseIpAddress", "true");
    Assert.assertEquals(cfg.useIpAddress(), true);
  }

  @Test
  public void useIpAddressNamedProp() {
    set("foo.niws.client.UseIpAddress", "true");
    Assert.assertEquals(cfg.useIpAddress(), true);
  }

  @Test
  public void useIpAddressNamedOverridesDefaultProp() {
    set("niws.client.UseIpAddress", "true");
    set("foo.niws.client.UseIpAddress", "false");
    Assert.assertEquals(cfg.useIpAddress(), false);
  }

  @Test
  public void fromUriNiws() {
    ClientConfig config = ClientConfig.fromUri(URI.create("niws://foo/bar"));
    Assert.assertEquals(config.uri().toString(), "/bar");
  }

  @Test
  public void fromUriNiwsWithQuery() {
    ClientConfig config = ClientConfig.fromUri(URI.create("niws://foo/bar?a=b"));
    Assert.assertEquals(config.uri().toString(), "/bar?a=b");
  }

  @Test
  public void fromUriNiwsWithAbsolute() {
    ClientConfig config = ClientConfig.fromUri(URI.create("niws://foo/http://foo.com/bar"));
    Assert.assertEquals(config.name(), "foo");
    Assert.assertEquals(config.originalUri().toString(), "niws://foo/http://foo.com/bar");
    Assert.assertEquals(config.uri().toString(), "http://foo.com/bar");
    Assert.assertEquals(config.relativeUri(), "/bar");
  }

  @Test
  public void fromUriVip() {
    ClientConfig config = ClientConfig.fromUri(URI.create("vip://foo:vip:7001/bar"));
    Assert.assertEquals(config.uri().toString(), "/bar");
    Assert.assertEquals(config.vip(), "vip:7001");
  }

  @Test
  public void fromUriVipWithQuery() {
    ClientConfig config = ClientConfig.fromUri(URI.create("vip://foo:vip:7001/bar?a=b"));
    Assert.assertEquals(config.uri().toString(), "/bar?a=b");
    Assert.assertEquals(config.vip(), "vip:7001");
  }
}
