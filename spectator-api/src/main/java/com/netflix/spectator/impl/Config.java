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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.RegistryConfig;

/**
 * Helper methods for accessing configuration settings.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class Config {

  private static final String PREFIX = "spectator.api.";

  private static final RegistryConfig DEFAULT_CONFIG = k -> System.getProperty(PREFIX + k);

  private Config() {
  }

  /**
   * Returns a default implementation of the registry config backed by system properties.
   */
  public static RegistryConfig defaultConfig() {
    return DEFAULT_CONFIG;
  }
}
