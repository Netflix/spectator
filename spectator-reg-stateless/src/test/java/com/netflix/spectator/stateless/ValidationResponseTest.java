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
package com.netflix.spectator.stateless;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ValidationResponseTest {

  private List<String> list(String... vs) {
    return Arrays.asList(vs);
  }

  @Test
  public void simple() throws Exception {
    String payload = "{\"type\":\"error\",\"errorCount\":42,\"message\":[\"foo\",\"bar\"]}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list("foo", "bar"), response.getMessage());
  }

  @Test
  public void noMessage() throws Exception {
    String payload = "{\"type\":\"error\",\"errorCount\":42}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list(), response.getMessage());
  }

  @Test
  public void emptyMessage() throws Exception {
    String payload = "{\"type\":\"error\",\"errorCount\":42,\"message\":[]}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list(), response.getMessage());
  }

  @Test
  public void nullMessage() throws Exception {
    String payload = "{\"type\":\"error\",\"errorCount\":42,\"message\":null}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list(), response.getMessage());
  }

  @Test
  public void noErrorCount() throws Exception {
    String payload = "{\"type\":\"error\",\"errorCount\":42}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list(), response.getMessage());
  }

  @Test
  public void ignoredFields() throws Exception {
    String payload = "{\"type\":\"error\",\"foo\":\"bar\",\"errorCount\":42,\"a\":{\"b\":[1,2,3]},\"message\":[\"foo\",\"bar\"]}";
    ValidationResponse response = ValidationResponse.fromJson(payload.getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("error", response.getType());
    Assertions.assertEquals(42, response.getErrorCount());
    Assertions.assertEquals(list("foo", "bar"), response.getMessage());
  }
}
