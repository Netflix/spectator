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
package com.netflix.spectator.log4j;

import com.netflix.spectator.api.Tag;
import org.apache.log4j.Level;

/**
 * Tags based on the standard log4j levels. The key will be {@code loglevel} and the value will
 * be the level name prefixed with the order so it will sort correctly.
 */
enum LevelTag implements Tag {
  /** 0_OFF. */
  OFF(Level.OFF),

  /** 1_FATAL. */
  FATAL(Level.FATAL),

  /** 2_ERROR. */
  ERROR(Level.ERROR),

  /** 3_WARN. */
  WARN(Level.WARN),

  /** 4_INFO. */
  INFO(Level.INFO),

  /** 5_DEBUG. */
  DEBUG(Level.DEBUG),

  /** 6_TRACE. */
  TRACE(Level.TRACE),

  /** 7_ALL. */
  ALL(Level.ALL);

  private final Level std;
  private final String value;

  /** Create a new instance based on a standard log4j level. */
  LevelTag(Level std) {
    this.std = std;
    this.value = String.format("%d_%s", ordinal(), std.toString());
  }

  @Override public String key() {
    return "loglevel";
  }

  @Override public String value() {
    return value;
  }

  /** Return the corresponding standard log4j level. */
  Level standardLevel() {
    return std;
  }

  /** Get the tag corresponding to the log4j level. */
  static LevelTag get(Level level) {
    LevelTag tag = OFF;
    switch (level.toInt()) {
      case Level.OFF_INT:   tag = OFF;   break;
      case Level.FATAL_INT: tag = FATAL; break;
      case Level.ERROR_INT: tag = ERROR; break;
      case Level.WARN_INT:  tag = WARN;  break;
      case Level.INFO_INT:  tag = INFO;  break;
      case Level.DEBUG_INT: tag = DEBUG; break;
      case Level.TRACE_INT: tag = TRACE; break;
      case Level.ALL_INT:   tag = ALL;   break;
      default:                           break;
    }
    return tag;
  }
}
