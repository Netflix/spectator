/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.impl.AsciiSet;

import java.util.Collections;
import java.util.Set;

/**
 * Helper for sanitizing a URL path for including as the {@code ipc.endpoint} value. Makes
 * a best effort to try and remove segments that tend to be variable like numbers, UUIDs,
 * etc.
 */
public final class PathSanitizer {

  private static final int MAX_LENGTH = 120;

  private static final AsciiSet ALPHA_CHARS = AsciiSet.fromPattern("a-zA-Z");

  private static final AsciiSet DIGITS = AsciiSet.fromPattern("0-9");

  private static final AsciiSet CONSONANTS = AsciiSet.fromPattern("b-df-hj-np-tv-xzB-DF-HJ-NP-TV-XZ");

  private PathSanitizer() {
  }

  /** Returns a sanitized path string for use as an endpoint tag value. */
  public static String sanitize(String path) {
    return sanitizeSegments(removeParameters(path), Collections.emptySet());
  }

  /**
   * Returns a sanitized path string for use as an endpoint tag value.
   *
   * @param path
   *     Path from a URI that should be sanitized.
   * @param allowed
   *     Set of allowed segment strings. This can be used to override the default rules for
   *     a set of known good values.
   * @return
   *     Sanitized path that can be used as an endpoint tag value.
   */
  public static String sanitize(String path, Set<String> allowed) {
    return sanitizeSegments(removeParameters(path), allowed);
  }

  private static String removeParameters(String path) {
    return removeParameters(removeParameters(path, '?'), ';');
  }

  private static String removeParameters(String path, char c) {
    int i = path.indexOf(c);
    return i > 0 ? path.substring(0, i) : path;
  }

  private static String sanitizeSegments(String path, Set<String> allowed) {
    StringBuilder builder = new StringBuilder();
    int length = path.length();
    int pos = path.charAt(0) == '/' ? 1 : 0;
    int segmentsAdded = 0;

    while (pos < length && segmentsAdded < 5) {
      String segment;
      int e = path.indexOf('/', pos);
      if (e > 0) {
        segment = path.substring(pos, e);
        pos = e + 1;
      } else {
        segment = path.substring(pos);
        pos = length;
      }

      if (!segment.isEmpty()) {
        if (shouldSuppressSegment(segment, allowed))
          appendIfSpaceAvailable(builder, "-");
        else
          appendIfSpaceAvailable(builder, segment);
        ++segmentsAdded;
      }
    }

    return builder.toString();
  }

  private static boolean shouldSuppressSegment(String segment, Set<String> allowed) {
    // GraphQL is a common endpoint, but hits the consonants check to detect strings
    // that are likely to be random. Special case it for now.
    if ("graphql".equals(segment) || allowed.contains(segment)) {
      return false;
    }

    final int maxSequentialConsonants = 4;
    int sequentialConsonants = 0;

    boolean version = false;
    boolean allAlpha = true;

    int n = segment.length();
    for (int i = 0; i < n; ++i) {
      char c = segment.charAt(i);
      if (CONSONANTS.contains(c)) {
        ++sequentialConsonants;
        if (sequentialConsonants >= maxSequentialConsonants)
          return true;
      } else {
        sequentialConsonants = 0;
      }
      if (i == 0 && c == 'v') {
        version = true;
      } else {
        version &= DIGITS.contains(c);
      }
      allAlpha &= ALPHA_CHARS.contains(c);
      if (!version && !allAlpha) {
        return true;
      }
    }

    return !version && n == 2;
  }

  private static void appendIfSpaceAvailable(StringBuilder builder, String segment) {
    int spaceRemaining = MAX_LENGTH - builder.length() - 1;
    if (segment.length() < spaceRemaining) {
      builder.append('_').append(segment);
    } else if (spaceRemaining >= 2) {
      builder.append("_-");
    }
  }
}
