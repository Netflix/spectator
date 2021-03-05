/*
 * Copyright 2014-2021 Netflix, Inc.
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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses reflection to call all public methods of Registry with various values including
 * {@code null} to try and ensure that exceptions are not propagated unless that is explicitly
 * enabled.
 */
public class RegistryExceptionsTest {

  private static final Object[] FALLBACK = new Object[] {null};

  private static final Map<Class<?>, Object[]> SAMPLE_VALUES = new HashMap<>();
  static {
    SAMPLE_VALUES.put(
        String.class,
        new Object[] {"foo", "", null}
    );
    SAMPLE_VALUES.put(
        String[].class,
        new Object[] {
            new String[] {"a", "1", "b", "2"},
            new String[] {"a", "1", "b"},
            new String[] {"a", "1", "b", null},
            new String[] {"a", "1", null, "2"},
            null
        }
    );
    SAMPLE_VALUES.put(
        Iterable.class,
        new Object[] {
            Arrays.asList("a", "1", "b", "2"),
            Arrays.asList("a", "1", "b"),
            null
        }
    );
    SAMPLE_VALUES.put(
        Id.class,
        new Object[] {
            Id.create("foo"),
            Id.create(""),
            null
        }
    );
  }

  @Test
  public void exceptionsNotPropagated() throws Exception {
    Method[] methods = DefaultRegistry.class.getMethods();
    for (Method method : methods) {
      if (Registry.class.isAssignableFrom(method.getDeclaringClass())) {
        invokeWithSampleValues(method);
      }
    }
  }

  private void invokeWithSampleValues(Method method) throws Exception {
    Object[] params = new Object[method.getParameterCount()];
    Class<?>[] ptypes = method.getParameterTypes();
    invoke(method, params, ptypes, 0);
  }

  private void invoke(Method method, Object[] params, Class<?>[] ptypes, int i) throws Exception {
    if (i == params.length) {
      method.invoke(new DefaultRegistry(Clock.SYSTEM, p -> null), params);
    } else {
      Object[] values = SAMPLE_VALUES.getOrDefault(ptypes[i], FALLBACK);
      for (Object value : values) {
        params[i] = value;
        invoke(method, params, ptypes, i + 1);
      }
    }
  }
}
