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

import org.apache.log4j.Level;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LevelTagTest {

  @Test
  public void levels() {
    Level[] levels = {
        Level.OFF,
        Level.FATAL,
        Level.ERROR,
        Level.WARN,
        Level.INFO,
        Level.DEBUG,
        Level.TRACE,
        Level.ALL
    };
    for (Level level : levels) {
      Assert.assertEquals(level, LevelTag.get(level).standardLevel());
    }
  }

  @Test
  public void values() {
    for (LevelTag tag : LevelTag.values()) {
      String v = tag.ordinal() + "_" + tag.name();
      Assert.assertEquals(v, tag.value());
    }
  }

}
