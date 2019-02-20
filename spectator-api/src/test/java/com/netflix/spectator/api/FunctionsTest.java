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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

public class FunctionsTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry();

  @Test
  public void ageFunction() {
    clock.setWallTime(5000L);
    final DoubleFunction f = Functions.age(clock);
    Assertions.assertEquals(f.apply(1000L), 4.0, 1e-12);
  }

  private byte byteMethod() {
    return (byte) 1;
  }

  @Test
  public void invokeMethodByte() throws Exception {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(Utils.getMethod(getClass(), "byteMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), 1.0, 1e-12);
  }

  private short shortMethod() {
    return (short) 2;
  }

  @Test
  public void invokeMethodShort() throws Exception  {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(Utils.getMethod(getClass(), "shortMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), 2.0, 1e-12);
  }

  private int intMethod() {
    return 3;
  }

  @Test
  public void invokeMethodInt() throws Exception  {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(Utils.getMethod(getClass(), "intMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), 3.0, 1e-12);
  }

  private long longMethod() {
    return 4L;
  }

  @Test
  public void invokeMethodLong() throws Exception  {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(Utils.getMethod(getClass(), "longMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), 4.0, 1e-12);
  }

  private Long wrapperLongMethod() {
    return 5L;
  }

  @Test
  public void invokeMethodWrapperLong() throws Exception  {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(
        Utils.getMethod(getClass(), "wrapperLongMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), 5.0, 1e-12);
  }

  private Long throwsMethod() {
    throw new IllegalStateException("fubar");
  }

  @Test
  public void invokeBadMethod() throws Exception  {
    final ToDoubleFunction<FunctionsTest> f = Functions.invokeMethod(Utils.getMethod(getClass(), "throwsMethod"));
    Assertions.assertEquals(f.applyAsDouble(this), Double.NaN, 1e-12);
  }

  @Test
  public void invokeNoSuchMethod() throws Exception  {
    Assertions.assertThrows(NoSuchMethodException.class,
        () -> Functions.invokeMethod(Utils.getMethod(getClass(), "unknownMethod")));
  }

  @Test
  public void invokeOnSubclass() throws Exception  {
    final ToDoubleFunction<B> f = Functions.invokeMethod(Utils.getMethod(B.class, "two"));
    Assertions.assertEquals(f.applyAsDouble(new B()), 2.0, 1e-12);
  }

  @Test
  public void invokeOneA() throws Exception  {
    final ToDoubleFunction<A> f = Functions.invokeMethod(Utils.getMethod(A.class, "one"));
    Assertions.assertEquals(f.applyAsDouble(new A()), 1.0, 1e-12);
  }

  @Test
  public void invokeOneB() throws Exception  {
    final ToDoubleFunction<B> f = Functions.invokeMethod(Utils.getMethod(B.class, "one"));
    Assertions.assertEquals(f.applyAsDouble(new B()), -1.0, 1e-12);
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
