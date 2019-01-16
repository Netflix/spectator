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
package com.netflix.spectator.ipc.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

public class HttpUtilsTest {

  private String extract(String u) {
    return HttpUtils.clientNameForURI(URI.create(u));
  }

  @Test
  public void relativeUri() {
    Assertions.assertEquals("default", extract("/foo"));
  }

  @Test
  public void dashFirst() {
    Assertions.assertEquals("ec2", extract("http://ec2-127-0-0-1.compute-1.amazonaws.com/foo"));
  }

  @Test
  public void dotFirst() {
    Assertions.assertEquals("foo", extract("http://foo.test.netflix.com/foo"));
  }

  @Test
  public void gzip() throws IOException {
    byte[] data = "foo bar baz".getBytes(StandardCharsets.UTF_8);
    String result = new String(HttpUtils.gunzip(HttpUtils.gzip(data)), StandardCharsets.UTF_8);
    Assertions.assertEquals("foo bar baz", result);
  }

  @Test
  public void gzipWithLevel() throws IOException {
    byte[] data = "foo bar baz".getBytes(StandardCharsets.UTF_8);
    String result = new String(HttpUtils.gunzip(HttpUtils.gzip(data, Deflater.BEST_SPEED)), StandardCharsets.UTF_8);
    Assertions.assertEquals("foo bar baz", result);
  }
}
