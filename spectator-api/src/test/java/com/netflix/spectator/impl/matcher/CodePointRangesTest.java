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
package com.netflix.spectator.impl.matcher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class CodePointRangesTest {

  private static final int MAX = CodePointRanges.MAX;

  @Test
  public void range() {
    Assertions.assertSame(CodePointRanges.NONE, CodePointRanges.range(0, 0x7F));
    Assertions.assertSame(CodePointRanges.ALL, CodePointRanges.range(0, MAX));
    Assertions.assertEquals(CodePointRanges.range(0x80, 0x100), CodePointRanges.range(0, 0x100));
    Assertions.assertEquals("\\x{e9}", CodePointRanges.of(0xE9).toPattern());
    Assertions.assertEquals(0xE9, CodePointRanges.of(0xE9).single());
    Assertions.assertEquals(-1, CodePointRanges.range(0xE9, 0xEA).single());
  }

  @Test
  public void unionJoinsAdjacent() {
    CodePointRanges r = CodePointRanges.of(0xE9)
        .union(CodePointRanges.of(0xEB))
        .union(CodePointRanges.of(0xEA));
    Assertions.assertEquals(CodePointRanges.range(0xE9, 0xEB), r);
    Assertions.assertEquals("\\x{e9}-\\x{eb}", r.toPattern());
  }

  @Test
  public void invert() {
    CodePointRanges r = CodePointRanges.of(0x1F600);
    Assertions.assertEquals("\\x{80}-\\x{1f5ff}\\x{1f601}-\\x{10ffff}", r.invert().toPattern());
    Assertions.assertEquals(r, r.invert().invert());
    Assertions.assertSame(CodePointRanges.ALL, CodePointRanges.NONE.invert());
    Assertions.assertSame(CodePointRanges.NONE, CodePointRanges.ALL.invert());
    Assertions.assertSame(CodePointRanges.ALL, CodePointRanges.range(0x80, 0x100)
        .union(CodePointRanges.range(0x101, MAX)));
  }

  private static CodePointRanges random(Random r) {
    CodePointRanges set = CodePointRanges.NONE;
    int n = r.nextInt(4);
    for (int i = 0; i < n; ++i) {
      // Small span so that ranges often overlap or touch
      int s = 0x80 + r.nextInt(40);
      set = set.union(CodePointRanges.range(s, s + r.nextInt(8)));
    }
    if (r.nextInt(5) == 0) {
      set = set.union(CodePointRanges.range(0x80 + r.nextInt(60), MAX));
    }
    return set;
  }

  private static boolean[] bits(CodePointRanges set, int limit) {
    boolean[] bs = new boolean[limit];
    for (int i = 0; i < limit; ++i) {
      bs[i] = set.contains(i);
    }
    return bs;
  }

  @Test
  public void operationsMatchBitSets() {
    Random r = new Random(42);
    int limit = 0x100;
    for (int k = 0; k < 2000; ++k) {
      CodePointRanges a = random(r);
      CodePointRanges b = random(r);
      boolean[] x = bits(a, limit);
      boolean[] y = bits(b, limit);
      boolean[] u = bits(a.union(b), limit);
      boolean[] n = bits(a.intersection(b), limit);
      boolean[] v = bits(a.invert(), limit);
      boolean overlaps = false;
      boolean containsAll = true;
      for (int i = 0; i < limit; ++i) {
        boolean inRange = i >= 0x80;
        Assertions.assertEquals(x[i] || y[i], u[i]);
        Assertions.assertEquals(x[i] && y[i], n[i]);
        Assertions.assertEquals(inRange && !x[i], v[i]);
        overlaps |= x[i] && y[i];
        containsAll &= !y[i] || x[i];
      }
      // Sets only differ above the limit when both have a range to the max code point,
      // which is then in both
      Assertions.assertEquals(overlaps || (a.contains(MAX) && b.contains(MAX)), a.overlaps(b));
      Assertions.assertEquals(containsAll && (a.contains(MAX) || !b.contains(MAX)),
          a.containsAll(b));
      Assertions.assertEquals(a, a.union(a));
      Assertions.assertEquals(a.union(b), b.union(a));
    }
  }
}
