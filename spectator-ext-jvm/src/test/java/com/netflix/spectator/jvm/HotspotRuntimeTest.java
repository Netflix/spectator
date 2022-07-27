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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class HotspotRuntimeTest {

  private void triggerSafepoint() {
    // May not always work, but seems to work for the tests...
    System.gc();
  }

  @Test
  public void safepointCount() {
    Assumptions.assumeTrue(HotspotRuntime.isSupported());
    long before = HotspotRuntime.getSafepointCount();
    triggerSafepoint();
    long after = HotspotRuntime.getSafepointCount();
    Assertions.assertTrue(after > before);
  }

  @Test
  public void safepointTime() {
    Assumptions.assumeTrue(HotspotRuntime.isSupported());
    HotspotRuntime.getSafepointTime();
  }

  @Test
  public void safepointSyncTime() {
    Assumptions.assumeTrue(HotspotRuntime.isSupported());
    HotspotRuntime.getSafepointSyncTime();
  }
}
