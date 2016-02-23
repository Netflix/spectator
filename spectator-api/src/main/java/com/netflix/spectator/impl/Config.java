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
package com.netflix.spectator.impl;

/**
 * Helper methods for accessing configuration settings.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class Config {

  private static final String PREFIX = "spectator.api.";

  private Config() {
  }

  private static String get(String k) {
    return System.getProperty(k);
  }

  private static String get(String k, String dflt) {
    final String v = get(k);
    return (v == null) ? dflt : v;
  }

  /** Should an exception be thrown for warnings? */
  public static boolean propagateWarnings() {
    return Boolean.valueOf(get(PREFIX + "propagateWarnings", "false"));
  }

  /**
   * For classes based on {@link com.netflix.spectator.api.AbstractRegistry} this setting is used
   * to determine the maximum number of registered meters permitted. This limit is used to help
   * protect the system from a memory leak if there is a bug or irresponsible usage of registering
   * meters.
   *
   * @return
   *     Maximum number of distinct meters that can be registered at a given time. The default is
   *     {@link java.lang.Integer#MAX_VALUE}.
   */
  public static int maxNumberOfMeters() {
    final String v = get(PREFIX + "maxNumberOfMeters");
    return (v == null) ? Integer.MAX_VALUE : Integer.parseInt(v);
  }
}
