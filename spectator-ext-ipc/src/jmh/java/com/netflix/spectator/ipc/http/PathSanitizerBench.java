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

import com.netflix.spectator.impl.PatternMatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * <pre>
 * Benchmark                     Mode  Cnt        Score        Error   Units
 * javaRegex                    thrpt    5  1991933.684 ±  40277.438   ops/s
 * customRegex                  thrpt    5  3647568.873 ± 130042.092   ops/s
 * handwritten                  thrpt    5  7345612.822 ± 385125.354   ops/s
 *
 * Benchmark                     Mode  Cnt        Score        Error   Units
 * javaRegex                    alloc    5      688.509 ±      0.009    B/op
 * customRegex                  alloc    5      514.248 ±      0.008    B/op
 * handwritten                  alloc    5      280.584 ±      0.005    B/op
 * </pre>
 */
@State(Scope.Thread)
public class PathSanitizerBench {

  private final String regex = "^(v\\d+|[a-zA-Z]{1}|((?![b-df-hj-np-tv-xzB-DF-HJ-NP-TV-XZ]{4})[a-zA-Z]){3,})$";

  private final Pattern javaPattern = Pattern.compile(regex);

  private final PatternMatcher customPattern = PatternMatcher.compile(regex);

  private final Sanitizer javaSanitizer = new Sanitizer(s -> javaPattern.matcher(s).matches());

  private final Sanitizer customSanitizer = new Sanitizer(customPattern::matches);

  private static class Sanitizer {

    private final Predicate<String> shouldSuppressSegment;

    Sanitizer(Predicate<String> shouldSuppressSegment) {
      this.shouldSuppressSegment = shouldSuppressSegment;
    }

    private String removeMatixParameters(String path) {
      int i = path.indexOf(';');
      return i > 0 ? path.substring(0, i) : path;
    }

    private String sanitizeSegments(String path) {
      StringBuilder builder = new StringBuilder();
      int length = path.length();
      int pos = path.charAt(0) == '/' ? 1 : 0;
      int segmentsAdded = 0;

      while (pos < length && segmentsAdded < 5) {
        String segment = null;
        int e = path.indexOf('/', pos);
        if (e > 0) {
          segment = path.substring(pos, e);
          pos = e + 1;
        } else {
          segment = path.substring(pos);
          pos = length;
        }

        if (!segment.isEmpty()) {
          if (shouldSuppressSegment.test(segment))
            builder.append("_-");
          else
            builder.append('_').append(segment);
          ++segmentsAdded;
        }
      }

      return builder.toString();
    }

    String sanitize(String path) {
      return sanitizeSegments(removeMatixParameters(path));
    }
  }

  @State(Scope.Thread)
  public static class Paths {

    private int pos;
    private String[] paths;

    public Paths() {
      pos = 0;
      paths = new String[10_000];

      Random random = new Random();
      for (int i = 0; i < paths.length; ++i) {
        paths[i] = randomPath(random);
      }
    }

    private String randomPath(Random random) {
      StringBuilder builder = new StringBuilder();
      builder.append('/');

      int n = random.nextInt(10);
      for (int i = 0; i < n; ++i) {
        switch (random.nextInt(10)) {
          case 0:
            builder.append(random.nextLong());
            break;
          case 1:
            builder.append(random.nextDouble());
            break;
          case 2:
            builder.append(UUID.randomUUID());
            break;
          case 3:
            builder.append("en-US");
            break;
          case 4:
            builder.append(String.format("%x", random.nextLong()));
            break;
          default:
            builder.append("api");
            break;
        }
      }

      if (random.nextBoolean()) {
        builder.append(";q=").append(UUID.randomUUID());
      }

      return builder.toString();
    }

    public String next() {
      int i = Integer.remainderUnsigned(pos++, paths.length);
      return paths[i];
    }
  }

  @Benchmark
  public void javaRegex(Paths paths, Blackhole bh) {
    bh.consume(javaSanitizer.sanitize(paths.next()));
  }

  @Benchmark
  public void customRegex(Paths paths, Blackhole bh) {
    bh.consume(customSanitizer.sanitize(paths.next()));
  }

  @Benchmark
  public void handwritten(Paths paths, Blackhole bh) {
    bh.consume(PathSanitizer.sanitize(paths.next()));
  }
}
