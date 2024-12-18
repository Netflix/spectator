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
package com.netflix.spectator.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AtomicDoubleTest {

  @Test
  public void init() {
    AtomicDouble v = new AtomicDouble();
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void initWithValue() {
    AtomicDouble v = new AtomicDouble(42.0);
    Assertions.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void set() {
    AtomicDouble v = new AtomicDouble(13.0);
    v.set(42.0);
    Assertions.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void getAndSet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assertions.assertEquals(13.0, v.getAndSet(42.0), 1e-12);
    Assertions.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void compareAndSet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assertions.assertTrue(v.compareAndSet(13.0, 42.0));
    Assertions.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void compareAndSetFail() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assertions.assertFalse(v.compareAndSet(12.0, 42.0));
    Assertions.assertEquals(13.0, v.get(), 1e-12);
  }

  @Test
  public void addAndGet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assertions.assertEquals(55.0, v.addAndGet(42.0), 1e-12);
    Assertions.assertEquals(55.0, v.get(), 1e-12);
  }

  @Test
  public void getAndAdd() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assertions.assertEquals(13.0, v.getAndAdd(42.0), 1e-12);
    Assertions.assertEquals(55.0, v.get(), 1e-12);
  }

  @Test
  public void minGt() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.min(2.0);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void minLt() {
    AtomicDouble v = new AtomicDouble(2.0);
    v.min(0.0);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void minNegative() {
    AtomicDouble v = new AtomicDouble(-42.0);
    v.min(-41.0);
    Assertions.assertEquals(-42.0, v.get(), 1e-12);
  }

  @Test
  public void minNaN() {
    AtomicDouble v = new AtomicDouble(Double.NaN);
    v.min(0.0);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void minValueNaN() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.min(Double.NaN);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void minNegativeNaN() {
    AtomicDouble v = new AtomicDouble(Double.NaN);
    v.min(-42.0);
    Assertions.assertEquals(-42.0, v.get(), 1e-12);
  }

  @Test
  public void minValueInfinity() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.min(Double.NEGATIVE_INFINITY);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void maxGt() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(2.0);
    Assertions.assertEquals(2.0, v.get(), 1e-12);
  }

  @Test
  public void maxLt() {
    AtomicDouble v = new AtomicDouble(2.0);
    v.max(0.0);
    Assertions.assertEquals(2.0, v.get(), 1e-12);
  }

  @Test
  public void maxNegative() {
    AtomicDouble v = new AtomicDouble(-42.0);
    v.max(-41.0);
    Assertions.assertEquals(-41.0, v.get(), 1e-12);
  }

  @Test
  public void maxNaN() {
    AtomicDouble v = new AtomicDouble(Double.NaN);
    v.max(0.0);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void maxValueNaN() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(Double.NaN);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void maxNegativeNaN() {
    AtomicDouble v = new AtomicDouble(Double.NaN);
    v.max(-42.0);
    Assertions.assertEquals(-42.0, v.get(), 1e-12);
  }

  @Test
  public void maxValueInfinity() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(Double.POSITIVE_INFINITY);
    Assertions.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void testToString() {
    AtomicDouble v = new AtomicDouble(0.0);
    Assertions.assertEquals("0.0", v.toString());
    v.set(-100.5);
    Assertions.assertEquals("-100.5", v.toString());
  }
}
