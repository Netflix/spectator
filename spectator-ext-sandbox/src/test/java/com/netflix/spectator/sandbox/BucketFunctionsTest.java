/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class BucketFunctionsTest {

  @Test
  public void age60s() {
    BucketFunction f = BucketFunctions.age(60, TimeUnit.SECONDS);
    Assertions.assertEquals("future", f.apply(TimeUnit.SECONDS.toNanos(-1)));
    Assertions.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(1)));
    Assertions.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(6)));
    Assertions.assertEquals("07s", f.apply(TimeUnit.SECONDS.toNanos(7)));
    Assertions.assertEquals("15s", f.apply(TimeUnit.SECONDS.toNanos(10)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(20)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(30)));
    Assertions.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(42)));
    Assertions.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(60)));
    Assertions.assertEquals("old", f.apply(TimeUnit.SECONDS.toNanos(61)));
  }

  @Test
  public void age60sBiasOld() {
    BucketFunction f = BucketFunctions.ageBiasOld(60, TimeUnit.SECONDS);
    Assertions.assertEquals("future", f.apply(TimeUnit.SECONDS.toNanos(-1)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(1)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(6)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(7)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(10)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(20)));
    Assertions.assertEquals("30s", f.apply(TimeUnit.SECONDS.toNanos(30)));
    Assertions.assertEquals("45s", f.apply(TimeUnit.SECONDS.toNanos(42)));
    Assertions.assertEquals("52s", f.apply(TimeUnit.SECONDS.toNanos(48)));
    Assertions.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(59)));
    Assertions.assertEquals("60s", f.apply(TimeUnit.SECONDS.toNanos(60)));
    Assertions.assertEquals("old", f.apply(TimeUnit.SECONDS.toNanos(61)));
  }

  @Test
  public void latency100ms() {
    BucketFunction f = BucketFunctions.latency(100, TimeUnit.MILLISECONDS);
    Assertions.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assertions.assertEquals("012ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1)));
    Assertions.assertEquals("025ms", f.apply(TimeUnit.MILLISECONDS.toNanos(13)));
    Assertions.assertEquals("025ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assertions.assertEquals("100ms", f.apply(TimeUnit.MILLISECONDS.toNanos(99)));
    Assertions.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(101)));
  }

  @Test
  public void latency100msBiasSlow() {
    BucketFunction f = BucketFunctions.latencyBiasSlow(100, TimeUnit.MILLISECONDS);
    Assertions.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assertions.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1)));
    Assertions.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(13)));
    Assertions.assertEquals("050ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assertions.assertEquals("075ms", f.apply(TimeUnit.MILLISECONDS.toNanos(74)));
    Assertions.assertEquals("075ms", f.apply(TimeUnit.MILLISECONDS.toNanos(75)));
    Assertions.assertEquals("100ms", f.apply(TimeUnit.MILLISECONDS.toNanos(99)));
    Assertions.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(101)));
  }

  @Test
  public void latency3s() {
    BucketFunction f = BucketFunctions.latency(3, TimeUnit.SECONDS);
    Assertions.assertEquals("negative_latency", f.apply(TimeUnit.MILLISECONDS.toNanos(-1)));
    Assertions.assertEquals("0375ms", f.apply(TimeUnit.MILLISECONDS.toNanos(25)));
    Assertions.assertEquals("0750ms", f.apply(TimeUnit.MILLISECONDS.toNanos(740)));
    Assertions.assertEquals("1500ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1000)));
    Assertions.assertEquals("3000ms", f.apply(TimeUnit.MILLISECONDS.toNanos(1567)));
    Assertions.assertEquals("slow", f.apply(TimeUnit.MILLISECONDS.toNanos(3001)));
  }
}
