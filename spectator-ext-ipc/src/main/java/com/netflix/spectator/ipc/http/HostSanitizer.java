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

import com.netflix.spectator.impl.AsciiSet;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper for sanitizing a host name for including as a tag value. Makes a best effort to
 * try and remove segments that tend to be variable like numbers, UUIDs, etc.
 */
public final class HostSanitizer {

  private static final int MAX_LENGTH = 120;

  private static final int MAX_SEGMENTS = 4;

  private static final AsciiSet ALPHA_CHARS = AsciiSet.fromPattern("a-zA-Z");

  private static final AsciiSet DIGITS = AsciiSet.fromPattern("0-9");

  private static final AsciiSet CONSONANTS = AsciiSet.fromPattern("b-df-hj-np-tv-xzB-DF-HJ-NP-TV-XZ");

  private static final Set<String> ALLOWED = new HashSet<>();
  static {
    ALLOWED.add("dbms");
    ALLOWED.add("ftlbknd");
    ALLOWED.add("graphql");
    ALLOWED.add("http");
    ALLOWED.add("http1");
    ALLOWED.add("http2");
    ALLOWED.add("mtls");
    ALLOWED.add("nccp");
    ALLOWED.add("nflx");
    ALLOWED.add("nrdp");
    ALLOWED.add("nrdpv6");
    ALLOWED.add("www2");

    ALLOWED.add("af-south-1");
    ALLOWED.add("ap-east-1");
    ALLOWED.add("ap-northeast-1");
    ALLOWED.add("ap-northeast-2");
    ALLOWED.add("ap-northeast-3");
    ALLOWED.add("ap-south-1");
    ALLOWED.add("ap-south-2");
    ALLOWED.add("ap-southeast-1");
    ALLOWED.add("ap-southeast-2");
    ALLOWED.add("ap-southeast-3");
    ALLOWED.add("ap-southeast-4");
    ALLOWED.add("ca-central-1");
    ALLOWED.add("ca-west-1");
    ALLOWED.add("eu-central-1");
    ALLOWED.add("eu-central-2");
    ALLOWED.add("eu-north-1");
    ALLOWED.add("eu-south-1");
    ALLOWED.add("eu-south-2");
    ALLOWED.add("eu-west-1");
    ALLOWED.add("eu-west-2");
    ALLOWED.add("eu-west-3");
    ALLOWED.add("fips.ca-central-1");
    ALLOWED.add("fips.ca-west-1");
    ALLOWED.add("fips.us-east-1");
    ALLOWED.add("fips.us-east-2");
    ALLOWED.add("fips.us-west-1");
    ALLOWED.add("fips.us-west-2");
    ALLOWED.add("il-central-1");
    ALLOWED.add("me-central-1");
    ALLOWED.add("me-south-1");
    ALLOWED.add("sa-east-1");
    ALLOWED.add("us-east-1");
    ALLOWED.add("us-east-2");
    ALLOWED.add("us-gov-east-1");
    ALLOWED.add("us-gov-west-1");
    ALLOWED.add("us-west-1");
    ALLOWED.add("us-west-2");
  }

  private HostSanitizer() {
  }

  /** Returns a sanitized host string for use as a tag value. */
  public static String sanitize(String host) {
    return sanitize(host, Collections.emptySet());
  }

  /**
   * Returns a sanitized host string for use as a tag value.
   *
   * @param host
   *     Host name that should be sanitized.
   * @param allowed
   *     Set of allowed segment strings. This can be used to override the default rules for
   *     a set of known good values.
   * @return
   *     Sanitized host that can be used as an endpoint tag value.
   */
  public static String sanitize(String host, Set<String> allowed) {
    String tmp = sanitizeSegments(removePort(host), allowed);
    return tmp.isEmpty() ? "none" : tmp;
  }

  private static String removePort(String host) {
    int pos = host.lastIndexOf(':');
    return pos >= 0 ? host.substring(0, pos) : host;
  }

  @SuppressWarnings("PMD")
  private static String sanitizeSegments(String host, Set<String> allowed) {
    HostStringBuilder builder = new HostStringBuilder();

    int last = host.length();
    int pos = host.lastIndexOf('.');
    while (pos >= 0) {
      String segment = host.substring(pos + 1, last);
      last = pos;
      pos = host.lastIndexOf('.', last - 1);

      if (!segment.isEmpty()) {
        if (shouldSuppressSegment(segment, allowed))
          segment = "_";
        if (!builder.prepend(segment))
          break;
      }
    }
    if (pos == -1) {
      String segment = host.substring(0, last);
      if (!segment.isEmpty()) {
        if (shouldSuppressSegment(segment, allowed))
          segment = "_";
        builder.prepend(segment);
      }
    }

    return builder.toString();
  }

  private static boolean shouldSuppressSegment(String segment, Set<String> allowed) {
    if (ALLOWED.contains(segment) || allowed.contains(segment)) {
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

    return false;
  }

  private static class HostStringBuilder {

    private final Deque<String> segments;
    private int length;
    private boolean lastSuppressed;

    HostStringBuilder() {
      this.segments = new ArrayDeque<>();
      this.length = 0;
      this.lastSuppressed = false;
    }

    boolean prepend(String segment) {
      if (segments.size() < MAX_SEGMENTS && segment.length() < MAX_LENGTH - length - 1) {
        boolean suppressed = "_".equals(segment);
        if (!lastSuppressed || !suppressed) {
          lastSuppressed = suppressed;
          segments.offer(segment);
          length += segments.isEmpty() ? segment.length() : segment.length() + 1;
        }
        return true;
      } else {
        if (!lastSuppressed)
          segments.offer("_");
        lastSuppressed = true;
        return false;
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(length);
      if (!segments.isEmpty())
        builder.append(segments.pollLast());
      while (!segments.isEmpty())
        builder.append('.').append(segments.pollLast());
      return builder.toString();
    }
  }
}
