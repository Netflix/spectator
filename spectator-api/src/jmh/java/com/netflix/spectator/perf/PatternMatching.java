/*
 * Copyright 2014-2018 Netflix, Inc.
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

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * <h2>Results on JDK 8</h2>
 *
 * <p>Throughput:</p>
 *
 * <pre>
 * Benchmark                   Mode  Cnt            Score         Error   Units
 * listCustom                 thrpt   10    1,184,089.314 ±    4676.725   ops/s
 * listJava                   thrpt   10            0.413 ±       0.003   ops/s
 * listRe2j                   thrpt   10       58,429.201 ±     694.491   ops/s
 * multiIndexOfCustom         thrpt   10   31,317,074.616 ±  705692.514   ops/s
 * multiIndexOfJava           thrpt   10    5,435,456.864 ±  555843.469   ops/s
 * multiIndexOfRe2j           thrpt   10   10,192,683.276 ±   61955.336   ops/s
 * orCustom                   thrpt   10    2,551,727.306 ±   38841.717   ops/s
 * orJava                     thrpt   10      531,411.598 ±    7586.830   ops/s
 * orRe2j                     thrpt   10       37,810.449 ±     730.444   ops/s
 * prefixCustom               thrpt   10   30,195,549.356 ±  568710.896   ops/s
 * prefixJava                 thrpt   10   12,893,094.010 ±  270093.866   ops/s
 * prefixRe2j                 thrpt   10      554,569.782 ±   16160.948   ops/s
 * prefixString               thrpt   10   33,232,503.056 ±  228206.044   ops/s
 * startIndexOfCustom         thrpt   10  112,775,842.584 ±  215497.926   ops/s
 * startIndexOfJava           thrpt   10   17,022,118.214 ±   84772.399   ops/s
 * startIndexOfRe2j           thrpt   10    6,581,951.985 ±   25517.793   ops/s
 * substrCustom               thrpt   10   26,131,737.230 ± 2912006.864   ops/s
 * substrJava                 thrpt   10   10,513,474.004 ±  840593.331   ops/s
 * substrRe2j                 thrpt   10    1,018,590.095 ±   16433.087   ops/s
 * substrString               thrpt   10   56,422,980.648 ±  436020.762   ops/s
 * </pre>
 *
 * <p>Normalized allocation rate:</p>
 *
 * <pre>
 * Benchmark                   Mode  Cnt            Score         Error   Units
 * listCustom                 alloc   10            0.349 ±       0.002    B/op
 * listJava                   alloc   10    1,448,867.200 ±   15217.592    B/op
 * listRe2j                   alloc   10          342.870 ±       0.117    B/op
 * multiIndexOfCustom         alloc   10            0.012 ±       0.001    B/op
 * multiIndexOfJava           alloc   10          176.074 ±       0.007    B/op
 * multiIndexOfRe2j           alloc   10          152.039 ±       0.001    B/op
 * orCustom                   alloc   10            0.153 ±       0.003    B/op
 * orJava                     alloc   10          184.716 ±       0.011    B/op
 * orRe2j                     alloc   10          651.068 ±       0.343    B/op
 * prefixCustom               alloc   10            0.013 ±       0.001    B/op
 * prefixJava                 alloc   10          176.031 ±       0.001    B/op
 * prefixRe2j                 alloc   10          120.711 ±       0.029    B/op
 * prefixString               alloc   10            0.012 ±       0.001    B/op
 * startIndexOfCustom         alloc   10            0.004 ±       0.001    B/op
 * startIndexOfJava           alloc   10          176.022 ±       0.001    B/op
 * startIndexOfRe2j           alloc   10          144.054 ±       0.001    B/op
 * substrCustom               alloc   10            0.015 ±       0.002    B/op
 * substrJava                 alloc   10          176.038 ±       0.004    B/op
 * substrRe2j                 alloc   10          192.386 ±       0.007    B/op
 * substrString               alloc   10            0.007 ±       0.001    B/op
 * </pre>
 *
 * <h2>Results on JDK 11</h2>
 *
 * <p>Throughput:</p>
 *
 * <pre>
 * Benchmark                   Mode  Cnt            Score         Error   Units
 * listCustom                 thrpt   10    1,105,261.438 ±   13049.048   ops/s
 * listJava                   thrpt   10       50,259.108 ±    1570.899   ops/s
 * listRe2j                   thrpt   10       54,232.563 ±    2421.550   ops/s
 * multiIndexOfCustom         thrpt   10   48,693,040.830 ± 1106840.217   ops/s
 * multiIndexOfJava           thrpt   10    4,914,153.254 ±  765062.246   ops/s
 * multiIndexOfRe2j           thrpt   10   18,065,868.666 ±   82044.767   ops/s
 * orCustom                   thrpt   10    5,248,226.617 ±   15211.949   ops/s
 * orJava                     thrpt   10      506,365.079 ±    1997.129   ops/s
 * orRe2j                     thrpt   10       34,912.433 ±     911.282   ops/s
 * prefixCustom               thrpt   10   48,187,119.321 ±  223668.543   ops/s
 * prefixJava                 thrpt   10   11,970,315.701 ±  660308.379   ops/s
 * prefixRe2j                 thrpt   10      612,792.800 ±    2379.586   ops/s
 * prefixString               thrpt   10   49,950,762.117 ±   94219.634   ops/s
 * startIndexOfCustom         thrpt   10   96,287,237.291 ±  353185.161   ops/s
 * startIndexOfJava           thrpt   10   16,658,780.670 ±   84494.346   ops/s
 * startIndexOfRe2j           thrpt   10    8,508,848.674 ±   60718.796   ops/s
 * substrCustom               thrpt   10   72,951,403.975 ± 1641257.446   ops/s
 * substrJava                 thrpt   10   10,459,477.330 ±  176745.602   ops/s
 * substrRe2j                 thrpt   10    1,030,064.491 ±    3626.560   ops/s
 * substrString               thrpt   10   89,500,023.812 ± 3758203.795   ops/s
 * </pre>
 *
 * <p>Normalized allocation rate:</p>
 *
 * <pre>
 * Benchmark                   Mode  Cnt            Score         Error   Units
 * listCustom                 alloc   10            1.419 ±       0.028    B/op
 * listJava                   alloc   10        2,855.522 ±       1.760    B/op
 * listRe2j                   alloc   10          333.558 ±       1.505    B/op
 * multiIndexOfCustom         alloc   10            0.030 ±       0.001    B/op
 * multiIndexOfJava           alloc   10          200.300 ±       0.047    B/op
 * multiIndexOfRe2j           alloc   10           72.084 ±       0.001    B/op
 * orCustom                   alloc   10            0.284 ±       0.002    B/op
 * orJava                     alloc   10          211.120 ±       0.030    B/op
 * orRe2j                     alloc   10          608.525 ±       1.269    B/op
 * prefixCustom               alloc   10            0.030 ±       0.001    B/op
 * prefixJava                 alloc   10          200.125 ±       0.007    B/op
 * prefixRe2j                 alloc   10          122.511 ±       0.021    B/op
 * prefixString               alloc   10            0.029 ±       0.001    B/op
 * startIndexOfCustom         alloc   10            0.015 ±       0.001    B/op
 * startIndexOfJava           alloc   10          200.088 ±       0.001    B/op
 * startIndexOfRe2j           alloc   10          144.180 ±       0.002    B/op
 * substrCustom               alloc   10            0.020 ±       0.001    B/op
 * substrJava                 alloc   10          200.143 ±       0.004    B/op
 * substrRe2j                 alloc   10          217.494 ±       0.012    B/op
 * substrString               alloc   10            0.016 ±       0.001    B/op
 * </pre>
 */
@State(Scope.Thread)
public class PatternMatching {

  private final String example = UUID.randomUUID().toString();

  // Needs to be a separate String instance to avoid short circuiting for the
  // String.{startsWith, contains} tests
  private final String exampleCopy = new String(example);

  private final String substr = example.substring(example.length() - 17, example.length() - 4);

  private final Pattern javaMatcherPrefix = Pattern.compile("^" + example);
  private final com.google.re2j.Pattern re2jMatcherPrefix = com.google.re2j.Pattern.compile("^" + example);
  private final PatternMatcher customMatcherPrefix = PatternMatcher.compile("^" + example);

  private final Pattern javaMatcherSubstr = Pattern.compile(substr);
  private final com.google.re2j.Pattern re2jMatcherSubstr = com.google.re2j.Pattern.compile(substr);
  private final PatternMatcher customMatcherSubstr = PatternMatcher.compile(substr);

  private final String startIndexOf = "^ab.*123";
  private final Pattern javaMatcherStartIndexOf = Pattern.compile(startIndexOf);
  private final com.google.re2j.Pattern re2jMatcherStartIndexOf =
      com.google.re2j.Pattern.compile(startIndexOf);
  private final PatternMatcher customMatcherStartIndexOf = PatternMatcher.compile(startIndexOf);

  private final String multiIndexOf = "ab.*45.*123";
  private final Pattern javaMatcherMultiIndexOf = Pattern.compile(multiIndexOf);
  private final com.google.re2j.Pattern re2jMatcherMultiIndexOf =
      com.google.re2j.Pattern.compile(multiIndexOf);
  private final PatternMatcher customMatcherMultiIndexOf = PatternMatcher.compile(multiIndexOf);

  private final String or = "(abc|bcd|cde|def|ef0|f01|012|123|234|456|567|678|789|890|90a|0ab)";
  private final Pattern javaMatcherOr = Pattern.compile(or);
  private final com.google.re2j.Pattern re2jMatcherOr = com.google.re2j.Pattern.compile(or);
  private final PatternMatcher customMatcherOr = PatternMatcher.compile(or);

  // Example of really expensive case for java pattern matcher
  private final String list =
      "[1234567],[89023432],[124534543],[4564362],[1234543],[12234567],[124567],[1234567],[1234567]]";
  private final String listPattern = "^\\[((\\d*\\]\\,\\[\\d*)*|\\d*)\\]$";
  private final Pattern javaMatcherList = Pattern.compile(listPattern);
  private final com.google.re2j.Pattern re2jMatcherList =
      com.google.re2j.Pattern.compile(listPattern);
  private final PatternMatcher customMatcherList = PatternMatcher.compile(listPattern);

  @Benchmark
  public void prefixString(Blackhole bh) {
    bh.consume(example.startsWith(exampleCopy));
  }

  @Benchmark
  public void prefixJava(Blackhole bh) {
    bh.consume(javaMatcherPrefix.matcher(exampleCopy).find());
  }

  @Benchmark
  public void prefixRe2j(Blackhole bh) {
    bh.consume(re2jMatcherPrefix.matcher(exampleCopy).find());
  }

  @Benchmark
  public void prefixCustom(Blackhole bh) {
    bh.consume(customMatcherPrefix.matches(exampleCopy));
  }

  @Benchmark
  public void substrString(Blackhole bh) {
    bh.consume(exampleCopy.contains(substr));
  }

  @Benchmark
  public void substrJava(Blackhole bh) {
    bh.consume(javaMatcherSubstr.matcher(exampleCopy).find());
  }

  @Benchmark
  public void substrRe2j(Blackhole bh) {
    bh.consume(re2jMatcherSubstr.matcher(exampleCopy).find());
  }

  @Benchmark
  public void substrCustom(Blackhole bh) {
    bh.consume(customMatcherSubstr.matches(exampleCopy));
  }

  @Benchmark
  public void startIndexOfJava(Blackhole bh) {
    bh.consume(javaMatcherStartIndexOf.matcher(exampleCopy).find());
  }

  @Benchmark
  public void startIndexOfRe2j(Blackhole bh) {
    bh.consume(re2jMatcherStartIndexOf.matcher(exampleCopy).find());
  }

  @Benchmark
  public void startIndexOfCustom(Blackhole bh) {
    bh.consume(customMatcherStartIndexOf.matches(exampleCopy));
  }

  @Benchmark
  public void multiIndexOfJava(Blackhole bh) {
    bh.consume(javaMatcherMultiIndexOf.matcher(exampleCopy).find());
  }

  @Benchmark
  public void multiIndexOfRe2j(Blackhole bh) {
    bh.consume(re2jMatcherMultiIndexOf.matcher(exampleCopy).find());
  }

  @Benchmark
  public void multiIndexOfCustom(Blackhole bh) {
    bh.consume(customMatcherMultiIndexOf.matches(exampleCopy));
  }

  @Benchmark
  public void orJava(Blackhole bh) {
    bh.consume(javaMatcherOr.matcher(exampleCopy).find());
  }

  @Benchmark
  public void orRe2j(Blackhole bh) {
    bh.consume(re2jMatcherOr.matcher(exampleCopy).find());
  }

  @Benchmark
  public void orCustom(Blackhole bh) {
    bh.consume(customMatcherOr.matches(exampleCopy));
  }

  @Benchmark
  public void listJava(Blackhole bh) {
    bh.consume(javaMatcherList.matcher(list).find());
  }

  @Benchmark
  public void listRe2j(Blackhole bh) {
    bh.consume(re2jMatcherList.matcher(list).find());
  }

  @Benchmark
  public void listCustom(Blackhole bh) {
    bh.consume(customMatcherList.matches(list));
  }
}
