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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper functions for working with exceptions.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class Throwables {

  private static final Logger LOGGER = LoggerFactory.getLogger(Throwables.class);

  /**
   * Log a warning using the message from the exception and if enabled propagate the
   * exception {@code t}.
   *
   * @param t
   *     Exception to log and optionally propagate.
   */
  public static void propagate(Throwable t) {
    propagate(t.getMessage(), t);
  }

  /**
   * Log a warning and if enabled propagate the exception {@code t}. The propagation is controlled
   * by the system property {@code spectator.api.propagateWarnings}.
   *
   * @param msg
   *     Message written out to the log.
   * @param t
   *     Exception to log and optionally propagate.
   */
  public static void propagate(String msg, Throwable t) {
    LOGGER.warn(msg, t);
    if (Config.propagateWarnings()) {
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      } else {
        throw new RuntimeException(t);
      }
    }
  }

  private Throwables() {
  }
}
