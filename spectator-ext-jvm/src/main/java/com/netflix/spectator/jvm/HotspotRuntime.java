/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Helper for accessing the HotspotRuntimeMBean in order to get information about the amount
 * of time spent in safepoints.
 */
final class HotspotRuntime {

  private static final Logger LOGGER = LoggerFactory.getLogger(HotspotRuntime.class);

  private HotspotRuntime() {
  }

  private static Class<?> runtimeMBeanType;
  private static Object runtimeMBean;

  private static Method safepointCount;
  private static Method safepointTime;
  private static Method safepointSyncTime;

  static {
    try {
      // The implementation class, sun.management.HotspotRuntime, is package private and
      // thus the methods cannot be accessed without setAccessible(true). Use the interface
      // type for getting the method handles so all reflective access is via public classes and
      // methods. That allows this approach will work with either:
      //
      // --add-exports java.management/sun.management=ALL-UNNAMED OR
      // --add-opens java.management/sun.management=ALL-UNNAMED
      runtimeMBeanType = Class.forName("sun.management.HotspotRuntimeMBean");
      runtimeMBean = Class.forName("sun.management.ManagementFactoryHelper")
          .getMethod("getHotspotRuntimeMBean")
          .invoke(null);

      safepointCount = getMethod("getSafepointCount");
      safepointTime = getMethod("getTotalSafepointTime");
      safepointSyncTime = getMethod("getSafepointSyncTime");
    } catch (Exception e) {
      // Not Hotspot or IllegalAccessError from JDK 16+ due sun.management package being inaccessible
      LOGGER.debug("unable to access HotspotRuntimeMBean", e);
      runtimeMBean = null;
    }
  }

  /** Get method and double check that we have permissions to invoke it. */
  private static Method getMethod(String name) throws Exception {
    Method method = runtimeMBeanType.getMethod(name);
    method.invoke(runtimeMBean); // ignore result, just checking access
    return method;
  }

  private static long getValue(Method method) {
    if (runtimeMBean == null) {
      throw new UnsupportedOperationException("HotspotRuntime is not supported");
    }

    try {
      return (Long) method.invoke(runtimeMBean);
    } catch (Exception e) {
      throw new IllegalStateException("failed to invoke " + method, e);
    }
  }

  /** Returns the HotspotRuntimeMBean instance. */
  static Object getRuntimeMBean() {
    return runtimeMBean;
  }

  /** Returns true if the safepoint checks are supported. */
  static boolean isSupported() {
    return runtimeMBean != null;
  }

  /** Total number of safepoints since the JVM was started. */
  static long getSafepointCount() {
    return getValue(safepointCount);
  }

  /** Total time in milliseconds spent in safepoints since the JVM was started. */
  static long getSafepointTime() {
    return getValue(safepointTime);
  }

  /**
   * Total time in milliseconds spent synchronizing in order to get to safepoints since the
   * JVM was started.
   */
  static long getSafepointSyncTime() {
    return getValue(safepointSyncTime);
  }
}
