/*
 * Copyright 2014-2020 Netflix, Inc.
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
import com.netflix.spectator.api.Clock;

final class Servo {

  static {
    System.setProperty(
        "com.netflix.servo.DefaultMonitorRegistry.registryClass",
        "com.netflix.servo.jmx.JmxMonitorRegistry");
  }

  static MonitorRegistry getInstance() {
    return DefaultMonitorRegistry.getInstance();
  }

  static ServoRegistry newRegistry() {
    return new ServoRegistry();
  }

  static ServoRegistry newRegistry(Clock clock) {
    return new ServoRegistry(clock);
  }
}
