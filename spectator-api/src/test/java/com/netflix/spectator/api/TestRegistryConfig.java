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
package com.netflix.spectator.api;

class TestRegistryConfig implements RegistryConfig {

  private final boolean warnings;
  private final int numberOfMeters;

  TestRegistryConfig(boolean warnings, int numberOfMeters) {
    this.warnings = warnings;
    this.numberOfMeters = numberOfMeters;
  }

  @Override
  public String get(String k) {
    switch (k) {
      case "propagateWarnings": return Boolean.toString(warnings);
      case "maxNumberOfMeters": return Integer.toString(numberOfMeters);
      default:                  return null;
    }
  }
}
