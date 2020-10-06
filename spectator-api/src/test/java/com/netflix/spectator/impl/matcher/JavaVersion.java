/*
 * Copyright 2014-2020 Netflix, Inc.
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
package com.netflix.spectator.impl.matcher;

import java.lang.reflect.Method;

/** Helper for accessing the major Java version. */
class JavaVersion {

  private JavaVersion() {
  }

  /**
   * Return the major java version.
   */
  static int major() {
    try {
      Method versionMethod = Runtime.class.getMethod("version");
      Object version = versionMethod.invoke(null);
      Method majorMethod = version.getClass().getMethod("major");
      return (Integer) majorMethod.invoke(version);
    } catch (Exception e) {
      // The Runtime.version() method was added in jdk9 and spectator has a minimum
      // version of 8.
      return 8;
    }
  }
}
