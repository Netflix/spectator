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
package com.netflix.spectator.ipc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IpcAttemptFinalTest {

  @Test
  public void forValueTrue() {
    Assertions.assertEquals(IpcAttemptFinal.is_true, IpcAttemptFinal.forValue(true));
  }

  @Test
  public void forValueFalse() {
    Assertions.assertEquals(IpcAttemptFinal.is_false, IpcAttemptFinal.forValue(false));
  }
}
