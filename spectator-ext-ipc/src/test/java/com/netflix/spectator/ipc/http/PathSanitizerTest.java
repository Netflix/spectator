/*
 * Copyright 2014-2021 Netflix, Inc.
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class PathSanitizerTest {

  private final Random random = new Random(42);

  private String sanitize(String path) {
    return PathSanitizer.sanitize(path);
  }

  @Test
  public void uuids() {
    String path = "/api/v1/foo/" + UUID.randomUUID();
    Assertions.assertEquals("_api_v1_foo_-", sanitize(path));
  }

  @Test
  public void matrixParameters() {
    String path = "/api/v1/foo;user=bob";
    Assertions.assertEquals("_api_v1_foo", sanitize(path));
  }

  @Test
  public void decimalNumbers() {
    String path = "/api/v1/foo/1234567890/123";
    Assertions.assertEquals("_api_v1_foo_-_-", sanitize(path));
  }

  @Test
  public void floatingPointNumbers() {
    String path = "/api/v1/foo/1234.567890/1e2";
    Assertions.assertEquals("_api_v1_foo_-_-", sanitize(path));
  }

  @Test
  public void hexNumbers() {
    String path = "/api/v1/foo/a1ed5bc27";
    Assertions.assertEquals("_api_v1_foo_-", sanitize(path));
  }

  @Test
  public void isoDate() {
    String path = "/api/v1/foo/2021-05-04T13:35:00.000Z";
    Assertions.assertEquals("_api_v1_foo_-", sanitize(path));
  }

  @Test
  public void versionNumber() {
    String path = "/api/4.2.3";
    Assertions.assertEquals("_api_-", sanitize(path));
  }

  @Test
  public void unixTimestamp() {
    String path = "/api/v1/foo/" + System.currentTimeMillis();
    Assertions.assertEquals("_api_v1_foo_-", sanitize(path));
  }

  @Test
  public void iso_3166_2() {
    String path = "/country/US";
    Assertions.assertEquals("_country_-", sanitize(path));
  }

  @Test
  public void iso_3166_lang() {
    String path = "/country/en-US";
    Assertions.assertEquals("_country_-", sanitize(path));
  }

  @Test
  public void randomLookingString() {
    String path = "/random/AOTV16LMT2";
    Assertions.assertEquals("_random_-", sanitize(path));
  }

  @Test
  public void randomLookingString2() {
    String path = "/random/f3hnq9z8t";
    Assertions.assertEquals("_random_-", sanitize(path));
  }

  @Test
  public void hexStringsInt() {
    // Check that majority of hex strings are suppressed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/hex/" + String.format("%x", random.nextInt());
      if ("_hex_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 950, "suppressed " + suppressed + " of 1000");
  }

  @Test
  public void hexStringsLong() {
    // Check that majority of hex strings are suppressed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/hex/" + String.format("%x", random.nextLong());
      if ("_hex_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 950, "suppressed " + suppressed + " of 1000");
  }

  private String randomString(int n) {
    int bound = '~' - '!';
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < n; ++i) {
      char c = (char) ('!' + random.nextInt(bound));
      builder.append(c == '/' || c == ';' ? 'a' : c);
    }
    return builder.toString();
  }

  @Test
  public void random8() {
    // Check that majority of random strings are suppressed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/random/" + randomString(8);
      if ("_random_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 950, "suppressed " + suppressed + " of 1000");
  }

  @Test
  public void random16() {
    // Check that majority of random strings are suppressed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/random/" + randomString(16);
      if ("_random_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 950, "suppressed " + suppressed + " of 1000");
  }

  private String randomAlphaString(int n) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < n; ++i) {
      char c = (char) ('a' + random.nextInt(26));
      builder.append(c);
    }
    return builder.toString();
  }

  @Test
  public void randomAlpha8() {
    // Check that majority of random strings with only alpha characters are suppressed. This
    // is mostly based on runs of consonants, so shorter patterns are more likely to be missed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/random/" + randomAlphaString(8);
      if ("_random_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 500, "suppressed " + suppressed + " of 1000");
  }

  @Test
  public void randomAlpha16() {
    // Check that majority of random strings with only alpha characters are suppressed. This
    // is mostly based on runs of consonants, so shorter patterns are more likely to be missed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/random/" + randomAlphaString(16);
      if ("_random_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 900, "suppressed " + suppressed + " of 1000");
  }

  @Test
  public void randomAlphaUpper16() {
    // Check that majority of random strings with only alpha characters are suppressed. This
    // is mostly based on runs of consonants, so shorter patterns are more likely to be missed.
    int suppressed = 0;
    for (int i = 0; i < 1000; ++i) {
      String path = "/random/" + randomAlphaString(16).toUpperCase(Locale.US);
      if ("_random_-".equals(sanitize(path))) {
        ++suppressed;
      }
    }
    Assertions.assertTrue(suppressed > 900, "suppressed " + suppressed + " of 1000");
  }

  @Test
  public void allowMostWords() throws Exception {
    // Check that majority (> 95%) of actual words are allowed. Results could potentially
    // vary if the words file is different on other machines.
    Path file = Paths.get("/usr/share/dict/words");
    if (Files.isRegularFile(file)) {
      List<String> words = Files.readAllLines(file, StandardCharsets.UTF_8);
      int suppressed = 0;
      int total = words.size();
      for (String word : words) {
        String path = "/" + word;
        if ("_-".equals(sanitize(path))) {
          ++suppressed;
        }
      }
      Assertions.assertTrue(
          suppressed < 0.05 * total,
          "suppressed " + suppressed + " of " + total);
    }
  }
}
