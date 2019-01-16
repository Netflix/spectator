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
package com.netflix.spectator.log4j;

import com.netflix.spectator.api.Tag;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.StandardLevel;

/**
 * Tags based on the standard log4j levels. The key will be {@code loglevel} and the value will
 * be the level name prefixed with the order so it will sort correctly.
 */
enum LevelTag implements Tag {
  /** 0_OFF. */
  OFF(StandardLevel.OFF),

  /** 1_FATAL. */
  FATAL(StandardLevel.FATAL),

  /** 2_ERROR. */
  ERROR(StandardLevel.ERROR),

  /** 3_WARN. */
  WARN(StandardLevel.WARN),

  /** 4_INFO. */
  INFO(StandardLevel.INFO),

  /** 5_DEBUG. */
  DEBUG(StandardLevel.DEBUG),

  /** 6_TRACE. */
  TRACE(StandardLevel.TRACE),

  /** 7_ALL. */
  ALL(StandardLevel.ALL);

  private final StandardLevel std;
  private final String value;

  /** Create a new instance based on a standard log4j level. */
  LevelTag(StandardLevel std) {
    this.std = std;
    this.value = String.format("%d_%s", ordinal(), std.name());
  }

  @Override public String key() {
    return "loglevel";
  }

  @Override public String value() {
    return value;
  }

  /** Return the corresponding standard log4j level. */
  StandardLevel standardLevel() {
    return std;
  }

  private static final LevelTag[] LEVELS = new LevelTag[8];

  static {
    for (LevelTag level : LevelTag.values()) {
      LEVELS[level.ordinal()] = level;
    }
  }

  /** Get the tag corresponding to the log4j level. */
  static LevelTag get(Level level) {
    final StandardLevel stdLevel = level.getStandardLevel();
    return LEVELS[stdLevel.ordinal()];
  }
}
