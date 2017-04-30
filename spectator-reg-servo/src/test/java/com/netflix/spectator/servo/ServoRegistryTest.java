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
package com.netflix.spectator.servo;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.MonitorRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServoRegistryTest {

  @Test
  public void multiRegistration() {
    // Servo uses statics internally and the indended use of ServoRegistry
    // is there would be one in use at a given time. We don't want to make
    // it a singleton because that would break some existing unit tests that
    // expect isolated counts from the spectator api. This test just verifies
    // that multiple registrations can coexist in servo and will not clobber
    // each other.
    MonitorRegistry mr = DefaultMonitorRegistry.getInstance();

    ServoRegistry r1 = new ServoRegistry();
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r1));

    ServoRegistry r2 = new ServoRegistry();
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r1));
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r2));

    ServoRegistry r3 = new ServoRegistry();
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r1));
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r2));
    Assert.assertTrue(mr.getRegisteredMonitors().contains(r3));
  }

}
