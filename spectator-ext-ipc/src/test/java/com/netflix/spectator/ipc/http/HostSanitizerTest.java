/*
 * Copyright 2014-2024 Netflix, Inc.
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

public class HostSanitizerTest {

  @Test
  public void simple() {
    String host = "www.example.com";
    Assertions.assertEquals(host, HostSanitizer.sanitize(host));
  }

  @Test
  public void simpleTooManySegments() {
    String host = "a.b.c.d.example.com";
    Assertions.assertEquals("_.c.d.example.com", HostSanitizer.sanitize(host));
  }

  @Test
  public void simpleWithPort() {
    String host = "www.example.com:443";
    String expected = "www.example.com";
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void fourParts() {
    String host = "git.api.example.com";
    Assertions.assertEquals(host, HostSanitizer.sanitize(host));
  }

  @Test
  public void fiveParts() {
    String host = "foo.git.api.example.com";
    String expected = "_." + host.substring(4);
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void ipAddressV4() {
    String host = "127.0.0.1";
    String expected = "_";
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void ipAddressV4WithPort() {
    String host = "127.0.0.1:443";
    String expected = "_";
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void ipAddressV6() {
    String host = "[::1]";
    String expected = "_";
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void ipAddressV6WithPort() {
    String host = "[::1]:443";
    String expected = "_";
    Assertions.assertEquals(expected, HostSanitizer.sanitize(host));
  }

  @Test
  public void tooLongPreferEnd() {
    String host = "example.com";
    for (int i = 0; i < 255; ++i) {
      host = "abc." + host;
    }
    Assertions.assertEquals("_.abc.abc.example.com", HostSanitizer.sanitize(host));
  }

  @Test
  public void tooLongSingleSegmentPreferEnd() {
    String segment = "ab";
    for (int i = 0; i < 55; ++i) {
      segment += "ab";
    }
    String host = segment + ".example.com";
    Assertions.assertEquals("_.example.com", HostSanitizer.sanitize(host));
  }
}
