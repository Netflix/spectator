/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.api.histogram;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

@RunWith(JUnit4.class)
public class BucketFunctionsTest {

  @Test
  public void age60s() {
    LongFunction<String> f = BucketFunctions.age(60, TimeUnit.SECONDS);
    Assert.assertEquals("future", f.apply(TimeUnit.SECONDS.toNanos(-1)));
    Assert.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(0)));
    Assert.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(1)));
    Assert.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(6)));
    Assert.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(7)));
    Assert.assertEquals("15s", f.apply(TimeUnit.SECONDS.toNanos(8)));
    Assert.assertEquals("15s", f.apply(TimeUnit.SECONDS.toNanos(10)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(20)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(30)));
    Assert.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(31)));
    Assert.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(42)));
    Assert.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(60)));
    Assert.assertEquals("old", f.apply(TimeUnit.SECONDS.toNanos(61)));
  }

  @Test
  public void age60sBiasOld() {
    LongFunction<String> f = BucketFunctions.ageBiasOld(60, TimeUnit.SECONDS);
    Assert.assertEquals("future", f.apply(TimeUnit.SECONDS.toNanos(-1)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(0)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(1)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(6)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(7)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(10)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(20)));
    Assert.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(30)));
    Assert.assertEquals("45s", f.apply(TimeUnit.SECONDS.toNanos(42)));
    Assert.assertEquals("52s", f.apply(TimeUnit.SECONDS.toNanos(48)));
    Assert.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(59)));
    Assert.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(60)));
    Assert.assertEquals("old", f.apply(TimeUnit.SECONDS.toNanos(61)));
  }

  @Test
  public void latency100ms() {
    LongFunction<String> f = BucketFunctions.latency(100, TimeUnit.MILLISECONDS);
    Assert.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assert.assertEquals("012ms", f.apply(TimeUnit.MILLISECONDS.toNanos(0)));
    Assert.assertEquals("012ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1)));
    Assert.assertEquals("025ms", f.apply(TimeUnit.MILLISECONDS.toNanos(13)));
    Assert.assertEquals("025ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assert.assertEquals("100ms", f.apply(TimeUnit.MILLISECONDS.toNanos(99)));
    Assert.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(101)));
  }

  @Test
  public void latency100msBiasSlow() {
    LongFunction<String> f = BucketFunctions.latencyBiasSlow(100, TimeUnit.MILLISECONDS);
    Assert.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assert.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(0)));
    Assert.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1)));
    Assert.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(13)));
    Assert.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assert.assertEquals("075ms", f.apply(TimeUnit.MILLISECONDS.toNanos(74)));
    Assert.assertEquals("075ms", f.apply(TimeUnit.MILLISECONDS.toNanos(75)));
    Assert.assertEquals("087ms", f.apply(TimeUnit.MILLISECONDS.toNanos(76)));
    Assert.assertEquals("100ms", f.apply(TimeUnit.MILLISECONDS.toNanos(99)));
    Assert.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(101)));
  }

  @Test
  public void latency3s() {
    LongFunction<String> f = BucketFunctions.latency(3, TimeUnit.SECONDS);
    Assert.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assert.assertEquals("0375ms", f.apply(TimeUnit.MILLISECONDS.toNanos(0)));
    Assert.assertEquals("0375ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assert.assertEquals("0750ms", f.apply(TimeUnit.MILLISECONDS.toNanos(740)));
    Assert.assertEquals("1500ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1000)));
    Assert.assertEquals("3000ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1567)));
    Assert.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(3001)));
  }

  @Test
  public void latencyRange() {
    for (BucketFunctions.ValueFormatter fmt : BucketFunctions.TIME_FORMATTERS) {
      final long max = fmt.max();
      LongFunction<String> f = BucketFunctions.latency(max, TimeUnit.NANOSECONDS);
      Set<String> keys = new HashSet<>();
      final long step = (max > 37) ? max / 37 : 1;
      for (long j = 0L; max - j > step; j += step) {
        keys.add(f.apply(j));
      }
      keys.add(f.apply(max));
      Assert.assertEquals(4, keys.size());
    }
  }

  @Test
  public void latencyBiasSlowRange() {
    for (BucketFunctions.ValueFormatter fmt : BucketFunctions.TIME_FORMATTERS) {
      final long max = fmt.max();
      LongFunction<String> f = BucketFunctions.latencyBiasSlow(max, TimeUnit.NANOSECONDS);
      Set<String> keys = new HashSet<>();
      final long step = (max > 37) ? max / 37 : 1;
      for (long j = 0L; max - j >= step; j += step) {
        keys.add(f.apply(j));
      }
      keys.add(f.apply(max));
      Assert.assertEquals(4, keys.size());
    }
  }

  @Test
  public void bytes1K() {
    LongFunction<String> f = BucketFunctions.bytes(1024);
    Assert.assertEquals("negative", f.apply(-1L));
    Assert.assertEquals("0256_B", f.apply(212));
    Assert.assertEquals("0512_B", f.apply(512));
    Assert.assertEquals("1024_B", f.apply(761));
    Assert.assertEquals("large", f.apply(20001));
  }

  @Test
  public void bytes20K() {
    LongFunction<String> f = BucketFunctions.bytes(20000);
    Assert.assertEquals("negative", f.apply(-1L));
    Assert.assertEquals("02_KiB", f.apply(761));
    Assert.assertEquals("04_KiB", f.apply(4567));
    Assert.assertEquals("19_KiB", f.apply(15761));
    Assert.assertEquals("large", f.apply(20001));
  }

  @Test
  public void bytesMaxValue() {
    LongFunction<String> f = BucketFunctions.bytes(Long.MAX_VALUE);
    Assert.assertEquals("negative", f.apply(-1L));
    Assert.assertEquals("1023_PiB", f.apply(761));
    Assert.assertEquals("2047_PiB", f.apply(Long.MAX_VALUE / 4));
    Assert.assertEquals("4095_PiB", f.apply(Long.MAX_VALUE / 2));
    Assert.assertEquals("8191_PiB", f.apply(Long.MAX_VALUE));
  }

  @Test
  public void decimal20K() {
    LongFunction<String> f = BucketFunctions.decimal(20000);
    Assert.assertEquals("negative", f.apply(-1L));
    Assert.assertEquals("02_k", f.apply(761));
    Assert.assertEquals("05_k", f.apply(4567));
    Assert.assertEquals("20_k", f.apply(15761));
    Assert.assertEquals("large", f.apply(20001));
  }

  @Test
  public void decimalMaxValue() {
    LongFunction<String> f = BucketFunctions.decimal(Long.MAX_VALUE);
    Assert.assertEquals("negative", f.apply(-1L));
    Assert.assertEquals("1_E", f.apply(761));
    Assert.assertEquals("2_E", f.apply(Long.MAX_VALUE / 4));
    Assert.assertEquals("4_E", f.apply(Long.MAX_VALUE / 2));
    Assert.assertEquals("9_E", f.apply(Long.MAX_VALUE));
  }

}
