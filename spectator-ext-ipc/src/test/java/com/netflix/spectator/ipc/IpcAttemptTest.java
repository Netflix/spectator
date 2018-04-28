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
package com.netflix.spectator.ipc;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IpcAttemptTest {

  @Test
  public void forAttemptNegative() {
    Assert.assertEquals(IpcAttempt.unknown, IpcAttempt.forAttemptNumber(-1));
  }

  @Test
  public void forAttemptZero() {
    Assert.assertEquals(IpcAttempt.unknown, IpcAttempt.forAttemptNumber(0));
  }

  @Test
  public void forAttemptOne() {
    Assert.assertEquals(IpcAttempt.initial, IpcAttempt.forAttemptNumber(1));
  }

  @Test
  public void forAttemptTwo() {
    Assert.assertEquals(IpcAttempt.second, IpcAttempt.forAttemptNumber(2));
  }

  @Test
  public void forAttemptThree() {
    Assert.assertEquals(IpcAttempt.third_up, IpcAttempt.forAttemptNumber(3));
  }

  @Test
  public void forAttemptGreaterThanThree() {
    for (int i = 4; i < 1000; ++i) {
      Assert.assertEquals(IpcAttempt.third_up, IpcAttempt.forAttemptNumber(i));
    }
  }
}
