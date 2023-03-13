/*
 * Copyright 2014-2023 Netflix, Inc.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;


public class StreamHelperTest {

  @Test
  public void getOrCreateStream() {
    StreamHelper helper = new StreamHelper();
    ByteArrayOutputStream o1 = helper.getOrCreateStream();
    ByteArrayOutputStream o2 = helper.getOrCreateStream();
    Assertions.assertSame(o1, o2);
  }

  @Test
  public void streamIsResetBeforeNextUse() {
    StreamHelper helper = new StreamHelper();
    ByteArrayOutputStream out = helper.getOrCreateStream();
    out.write(42);
    Assertions.assertEquals(1, out.size());
    helper.getOrCreateStream();
    Assertions.assertEquals(0, out.size());
  }
}
