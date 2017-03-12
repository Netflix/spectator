/*
 * Copyright 2014-2017 Netflix, Inc.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Compares methods for creating a string based on an existing character array that we
 * know will not get modified after the string is created.
 *
 * <pre>
 * Benchmark                          Mode  Cnt          Score         Error  Units
 * StringCreate.methodHandle         thrpt   10  176057226.160 ± 5069495.131  ops/s
 * StringCreate.naive                thrpt   10   58188947.972 ± 1284066.194  ops/s
 * StringCreate.reflection           thrpt   10   75278354.480 ± 9173452.172  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class StringCreate {

  private final String str = UUID.randomUUID().toString();
  private final char[] arr = str.toCharArray();

  private static final Constructor<String> STRING_CONSTRUCTOR;
  private static final MethodHandle STRING_CONSTRUCTOR_HANDLE;
  static {
    Constructor<String> constructor;
    MethodHandle handle;
    try {
      constructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
      constructor.setAccessible(true);

      handle = MethodHandles.lookup().unreflectConstructor(constructor);
    } catch (Exception e) {
      constructor = null;
      handle = null;
    }
    STRING_CONSTRUCTOR = constructor;
    STRING_CONSTRUCTOR_HANDLE = handle;
  }


  /**
   * Creates a new string without copying the buffer if possible. The String class has a
   * package private constructor that allows the buffer to be shared.
   */
  private static String newStringReflection(char[] buf) {
    if (STRING_CONSTRUCTOR != null) {
      try {
        return STRING_CONSTRUCTOR.newInstance(buf, true);
      } catch (Exception e) {
        return new String(buf);
      }
    } else {
      return new String(buf);
    }
  }

  /**
   * Creates a new string without copying the buffer if possible. The String class has a
   * package private constructor that allows the buffer to be shared.
   */
  private static String newStringMethodHandle(char[] buf) {
    if (STRING_CONSTRUCTOR_HANDLE != null) {
      try {
        return (String) STRING_CONSTRUCTOR_HANDLE.invokeExact(buf, true);
      } catch (Throwable t) {
        return new String(buf);
      }
    } else {
      return new String(buf);
    }
  }

  @Threads(1)
  @Benchmark
  public void naive(Blackhole bh) {
    bh.consume(new String(arr));
  }

  @Threads(1)
  @Benchmark
  public void reflection(Blackhole bh) {
    bh.consume(newStringReflection(arr));
  }

  @Threads(1)
  @Benchmark
  public void methodHandle(Blackhole bh) {
    bh.consume(newStringMethodHandle(arr));
  }

}
