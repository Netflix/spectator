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
package com.netflix.spectator.impl;

/**
 * Internal convenience methods that help a method or constructor check whether it was invoked
 * correctly.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class Preconditions {
  private Preconditions() {
  }

  /**
   * Ensures the object reference is not null.
   */
  public static <T> T checkNotNull(T obj, String name) {
    if (obj == null) {
      String msg = String.format("parameter '%s' cannot be null", name);
      throw new NullPointerException(msg);
    }
    return obj;
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   */
  public static void checkArg(boolean expression, String errMsg) {
    if (!expression) {
      throw new IllegalArgumentException(errMsg);
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   */
  public static void checkState(boolean expression, String errMsg) {
    if (!expression) {
      throw new IllegalStateException(errMsg);
    }
  }
}
