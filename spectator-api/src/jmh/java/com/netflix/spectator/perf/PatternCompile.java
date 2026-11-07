/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.perf;

import com.netflix.spectator.impl.PatternMatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Time to compile patterns. Each iteration compiles a set of patterns with shapes that are
 * common for tag values, e.g. prefixes, substrings, alternatives and repeated classes.
 */
@State(Scope.Thread)
public class PatternCompile {

  private final String[] patterns = {
      "^foo",
      "^nccp-.*-(prod|test)$",
      ".*substring.*",
      "^(abc|bcd|cde|def|ef0|f01|012|123|234|456|567|678|789|890|90a|0ab)",
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
      "^api-v\\d+$",
      "^(?:i-[0-9a-f]+|[a-z]+-\\d+)$",
      "^[a-z0-9]+(?:-[a-z0-9]+)*$",
      "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$",
      "^(?!test).*-prod$",
      "^[^.]+\\.example\\.com$",
      "^aws_.*_.*v\\d+",
      "^(us-east-1|us-west-2|eu-west-1)$",
      "^status_(2|3|4|5)xx$"
  };

  @Benchmark
  public void compile(Blackhole bh) {
    for (String p : patterns) {
      bh.consume(PatternMatcher.compile(p));
    }
  }

  @Benchmark
  public void compileIgnoreCase(Blackhole bh) {
    for (String p : patterns) {
      bh.consume(PatternMatcher.compile(p).ignoreCase());
    }
  }
}
