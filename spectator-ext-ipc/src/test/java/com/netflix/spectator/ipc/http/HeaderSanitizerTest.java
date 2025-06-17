/*
 * Copyright 2014-2025 Netflix, Inc.
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

public class HeaderSanitizerTest {

  @Test
  public void okValue() {
    String value = "foo\tbar baz";
    Assertions.assertEquals(value, HeaderSanitizer.sanitize(value));
  }

  @Test
  public void removeCR() {
    String value = "foo\rbar";
    Assertions.assertEquals("foobar", HeaderSanitizer.sanitize(value));
  }

  @Test
  public void removeLF() {
    String value = "foo\nbar";
    Assertions.assertEquals("foobar", HeaderSanitizer.sanitize(value));
  }

  @Test
  public void removeCRLF() {
    String value = "foo\r\nbar";
    Assertions.assertEquals("foobar", HeaderSanitizer.sanitize(value));
  }

  @Test
  public void removeMisc() {
    String value = "foo\r\nbar\nbaz\r";
    Assertions.assertEquals("foobarbaz", HeaderSanitizer.sanitize(value));
  }
}
