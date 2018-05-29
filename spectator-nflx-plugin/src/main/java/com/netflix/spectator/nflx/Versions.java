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
package com.netflix.spectator.nflx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks that the versions for servo and atlas-client are new enough to support using
 * the AtlasRegistry and avoiding servo. Most apps should pick up the new versions via
 * recommendations, but there is likely to be some mismatch for a bit. This should allow
 * a more graceful transition as it will fallback to the previous behavior if the other
 * associated libraries are not new enough.
 */
final class Versions {

  private static final Logger LOGGER = LoggerFactory.getLogger(Versions.class);

  /**
   * If the atlas-client version is new enough, then this class will be present in the
   * classpath to poll the data from Spectator rather than Servo.
   */
  private static final String ATLAS_CLIENT_CLASS = "com.netflix.atlas.plugin.SpectatorMetricPoller";

  /** Check if a class is on the classpath. */
  @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.UseProperClassLoader"})
  private static boolean isClassPresent(String className) {
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = Versions.class.getClassLoader();
      }
      Class.forName(className, false, cl);
      LOGGER.debug("class {} found in classpath", className);
      return true;
    } catch (Throwable t) {
      LOGGER.debug("failed to load class: {}", className, t);
      return false;
    }
  }

  /**
   * Returns true if the atlas-client version is new enough to use AtlasRegistry instead
   * of ServoRegistry.
   */
  static boolean useAtlasRegistry() {
    return isClassPresent(ATLAS_CLIENT_CLASS);
  }

  private Versions() {
  }
}
