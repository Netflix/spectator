/*
 * Copyright 2014-2016 Netflix, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

@RunWith(JUnit4.class)
public class HttpResponseTest {

  @Test
  public void toStringEmpty() {
    HttpResponse res = new HttpResponse(200, Collections.emptyMap());
    String expected = "HTTP/1.1 200\n\n... 0 bytes ...\n";
    Assert.assertEquals(expected, res.toString());
  }

  @Test
  public void toStringContent() {
    byte[] entity = "content".getBytes(StandardCharsets.UTF_8);
    HttpResponse res = new HttpResponse(200, Collections.emptyMap(), entity);
    String expected = "HTTP/1.1 200\n\n... 7 bytes ...\n";
    Assert.assertEquals(expected, res.toString());
  }

  @Test
  public void toStringHeaders() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Date", Collections.singletonList("Mon, 27 Jul 2012 17:21:03 GMT"));
    headers.put("Content-Type", Collections.singletonList("application/json"));
    byte[] entity = "{}".getBytes(StandardCharsets.UTF_8);
    HttpResponse res = new HttpResponse(200, headers, entity);
    String expected = "HTTP/1.1 200\nContent-Type: application/json\nDate: Mon, 27 Jul 2012 17:21:03 GMT\n\n... 2 bytes ...\n";
    Assert.assertEquals(expected, res.toString());
  }

  @Test
  public void header() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singletonList("application/json"));
    HttpResponse res = new HttpResponse(200, headers);
    Assert.assertEquals("application/json", res.header("content-type"));
    Assert.assertEquals("application/json", res.header("Content-Type"));
  }

  @Test
  public void dateHeaderNull() {
    Map<String, List<String>> headers = new HashMap<>();
    HttpResponse res = new HttpResponse(200, headers);
    Assert.assertNull(res.dateHeader("Date"));
  }

  @Test
  public void dateHeaderGMT() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Date", Collections.singletonList("Fri, 27 Jul 2012 17:21:03 GMT"));
    HttpResponse res = new HttpResponse(200, headers);
    Instant expected = Instant.ofEpochMilli(1343409663000L);
    Assert.assertEquals(expected, res.dateHeader("Date"));
  }

  @Test
  public void decompressNoChange() throws IOException {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singletonList("application/json"));
    byte[] entity = HttpUtils.gzip("foo bar baz foo bar baz".getBytes(StandardCharsets.UTF_8));
    HttpResponse res = new HttpResponse(200, headers, entity);
    Assert.assertSame(res, res.decompress());
  }

  @Test
  public void decompress() throws IOException {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singletonList("application/json"));
    headers.put("Content-Encoding", Collections.singletonList("gzip"));
    byte[] entity = HttpUtils.gzip("foo bar baz foo bar baz".getBytes(StandardCharsets.UTF_8));
    HttpResponse res = new HttpResponse(200, headers, entity);
    Assert.assertEquals("foo bar baz foo bar baz", res.decompress().entityAsString());
    Assert.assertNull(res.decompress().header("content-encoding"));
  }

  @Test
  public void decompressEmpty() throws IOException {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singletonList("application/json"));
    headers.put("Content-Encoding", Collections.singletonList("gzip"));
    HttpResponse res = new HttpResponse(200, headers);
    Assert.assertEquals("", res.decompress().entityAsString());
    Assert.assertEquals(0, res.decompress().entity().length);
  }
}
