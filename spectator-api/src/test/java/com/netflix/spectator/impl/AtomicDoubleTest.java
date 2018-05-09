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
package com.netflix.spectator.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtomicDoubleTest {

  @Test
  public void init() {
    AtomicDouble v = new AtomicDouble();
    Assert.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void initWithValue() {
    AtomicDouble v = new AtomicDouble(42.0);
    Assert.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void set() {
    AtomicDouble v = new AtomicDouble(13.0);
    v.set(42.0);
    Assert.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void getAndSet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assert.assertEquals(13.0, v.getAndSet(42.0), 1e-12);
    Assert.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void compareAndSet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assert.assertTrue(v.compareAndSet(13.0, 42.0));
    Assert.assertEquals(42.0, v.get(), 1e-12);
  }

  @Test
  public void compareAndSetFail() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assert.assertFalse(v.compareAndSet(12.0, 42.0));
    Assert.assertEquals(13.0, v.get(), 1e-12);
  }

  @Test
  public void addAndGet() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assert.assertEquals(55.0, v.addAndGet(42.0), 1e-12);
    Assert.assertEquals(55.0, v.get(), 1e-12);
  }

  @Test
  public void getAndAdd() {
    AtomicDouble v = new AtomicDouble(13.0);
    Assert.assertEquals(13.0, v.getAndAdd(42.0), 1e-12);
    Assert.assertEquals(55.0, v.get(), 1e-12);
  }

  @Test
  public void maxGt() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(2.0);
    Assert.assertEquals(2.0, v.get(), 1e-12);
  }

  @Test
  public void maxLt() {
    AtomicDouble v = new AtomicDouble(2.0);
    v.max(0.0);
    Assert.assertEquals(2.0, v.get(), 1e-12);
  }

  @Test
  public void maxNegative() {
    AtomicDouble v = new AtomicDouble(-42.0);
    v.max(-41.0);
    Assert.assertEquals(-41.0, v.get(), 1e-12);
  }

  @Test
  public void maxNaN() {
    AtomicDouble v = new AtomicDouble(Double.NaN);
    v.max(0.0);
    Assert.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void maxValueNaN() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(Double.NaN);
    Assert.assertEquals(0.0, v.get(), 1e-12);
  }

  @Test
  public void maxValueInfinity() {
    AtomicDouble v = new AtomicDouble(0.0);
    v.max(Double.POSITIVE_INFINITY);
    Assert.assertEquals(0.0, v.get(), 1e-12);
  }
}
