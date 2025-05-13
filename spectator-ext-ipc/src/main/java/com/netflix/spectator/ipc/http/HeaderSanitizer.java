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

/**
 * Helper for sanitizing string values before using as a header. Guards against
 * <a href="https://cwe.mitre.org/data/definitions/93.html">CRLF injection</a>
 * in header values.
 */
public final class HeaderSanitizer {

  private HeaderSanitizer() {
  }

  /** Returns a sanitized value that is safe to use as a header. */
  public static String sanitize(String value) {
    return value == null
        ? value
        : value.replace("\r", "").replace("\n", "");
  }
}
