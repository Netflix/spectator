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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.impl.Hash64;
import net.openhft.hashing.LongHashFunction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JDK 25, M1 Mac, fork=1, 2 warmup × 5 iter. All ops/s.
 *
 * Three versions of Hash64.updateString compared:
 *   - Before:  String.getBytes(UTF_8) + Unsafe long-stride updateBytes (always).
 *   - Inline:  inline UTF-8 char loop (always); no allocation.
 *   - Hybrid:  inline loop for length ≤ 128 or non-String CharSequence;
 *              falls back to getBytes path above the threshold.
 *
 *   Benchmark              Before      Inline      Hybrid   Notes
 *   hash64              1,251,106   2,080,744   2,045,902   per-tag Id hashing
 *   shortAsciiString   19,988,867  22,450,113  22,488,509   18 chars
 *   unicodeString      11,785,066  13,864,127  13,991,804   17 chars, mixed UTF-8
 *   hash64_2            2,984,101   1,478,570   2,729,695   ~310 chars (id.toString)
 *   longAsciiString     4,149,357   1,306,378   4,199,309   368 chars
 *   openhft             4,401,361   4,401,036   4,349,151   reference impl
 *
 * Hybrid keeps the small-string wins (+64% on hash64, +13% on shortAsciiString,
 * +19% on unicodeString) and recovers essentially all of the long-string perf
 * (longAsciiString back to baseline; hash64_2 within ~9%). Below the threshold the
 * inline path is structurally non-allocating; above it the getBytes byte[] is
 * typically JIT-scalar-replaced and gc.alloc.rate stays ~zero in JMH.
 */
@State(Scope.Thread)
public class IdHash {

  private final Id id = Id.create("http.req.complete")
      .withTag(    "nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag(    "nf.asg", "test_app-main-v042")
      .withTag(  "nf.stack", "main")
      .withTag(    "nf.ami", "ami-0987654321")
      .withTag( "nf.region", "us-east-1")
      .withTag(   "nf.zone", "us-east-1e")
      .withTag(   "nf.node", "i-1234567890")
      .withTag(   "country", "US")
      .withTag(    "device", "xbox")
      .withTag(    "status", "200")
      .withTag(    "client", "ab");

  private final Hash64 h64 = new Hash64();
  private final LongHashFunction xx = LongHashFunction.xx();

  // Short ASCII tag-style string (~16 chars) — common metric tag case.
  private final String shortAscii = "i-0123456789abcdef";

  // Long ASCII string (~256 chars) — stresses the long-string path.
  private final String longAscii;
  {
    StringBuilder sb = new StringBuilder(256);
    for (int j = 0; j < 16; ++j) {
      sb.append("/api/v1/users/abcd1234/");
    }
    longAscii = sb.toString();
  }

  // Mixed ASCII + Latin-1 + CJK to exercise the 2-byte and 3-byte UTF-8 branches.
  private final String unicode = "user-42-héllo-世界";

  @Benchmark
  public void hash64(Blackhole bh) {
    h64.updateString(id.name());
    for (int i = 1; i < id.size(); ++i) {
      h64.updateChar(':');
      h64.updateString(id.getKey(i));
      h64.updateChar('=');
      h64.updateString(id.getValue(i));
    }
    bh.consume(h64.computeAndReset());
  }

  @Benchmark
  public void hash64_2(Blackhole bh) {
    bh.consume(h64.updateString(id.toString()).computeAndReset());
  }

  @Benchmark
  public void openhft(Blackhole bh) {
    bh.consume(xx.hashChars(id.toString()));
  }

  @Benchmark
  public void shortAsciiString(Blackhole bh) {
    bh.consume(h64.updateString(shortAscii).computeAndReset());
  }

  @Benchmark
  public void longAsciiString(Blackhole bh) {
    bh.consume(h64.updateString(longAscii).computeAndReset());
  }

  @Benchmark
  public void unicodeString(Blackhole bh) {
    bh.consume(h64.updateString(unicode).computeAndReset());
  }
}
