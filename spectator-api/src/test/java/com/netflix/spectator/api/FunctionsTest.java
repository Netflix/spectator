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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.function.ToDoubleFunction;

@RunWith(JUnit4.class)
public class FunctionsTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry();

  @Test
  public void ageFunction() {
    clock.setWallTime(5000L);
    final DoubleFunction f = Functions.age(clock);
    Assert.assertEquals(f.apply(1000L), 4.0, 1e-12);
  }

  private byte byteMethod() {
    return (byte) 1;
  }

  @Test
  public void invokeMethodByte() throws Exception {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(getClass(), "byteMethod"));
    Assert.assertEquals(f.applyAsDouble(this), 1.0, 1e-12);
  }

  private short shortMethod() {
    return (short) 2;
  }

  @Test
  public void invokeMethodShort() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(getClass(), "shortMethod"));
    Assert.assertEquals(f.applyAsDouble(this), 2.0, 1e-12);
  }

  private int intMethod() {
    return 3;
  }

  @Test
  public void invokeMethodInt() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(getClass(), "intMethod"));
    Assert.assertEquals(f.applyAsDouble(this), 3.0, 1e-12);
  }

  private long longMethod() {
    return 4L;
  }

  @Test
  public void invokeMethodLong() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(getClass(), "longMethod"));
    Assert.assertEquals(f.applyAsDouble(this), 4.0, 1e-12);
  }

  private Long wrapperLongMethod() {
    return 5L;
  }

  @Test
  public void invokeMethodWrapperLong() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(
        Utils.getMethod(getClass(), "wrapperLongMethod"));
    Assert.assertEquals(f.applyAsDouble(this), 5.0, 1e-12);
  }

  private Long throwsMethod() {
    throw new IllegalStateException("fubar");
  }

  @Test
  public void invokeBadMethod() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(getClass(), "throwsMethod"));
    Assert.assertEquals(f.applyAsDouble(this), Double.NaN, 1e-12);
  }

  @Test(expected = NoSuchMethodException.class)
  public void invokeNoSuchMethod() throws Exception  {
    Functions.invokeMethod(Utils.getMethod(getClass(), "unknownMethod"));
  }

  @Test
  public void invokeOnSubclass() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(B.class, "two"));
    Assert.assertEquals(f.applyAsDouble(new B()), 2.0, 1e-12);
  }

  @Test
  public void invokeOneA() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(A.class, "one"));
    Assert.assertEquals(f.applyAsDouble(new A()), 1.0, 1e-12);
  }

  @Test
  public void invokeOneB() throws Exception  {
    final ToDoubleFunction f = Functions.invokeMethod(Utils.getMethod(B.class, "one"));
    Assert.assertEquals(f.applyAsDouble(new B()), -1.0, 1e-12);
  }

  private static class A {
    public int one() {
      return 1;
    }
  }

  private static class B extends A {
    public int one() {
      return -1;
    }

    public int two() {
      return 2;
    }
  }
}
