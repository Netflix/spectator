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
package com.netflix.spectator.nflx;

import com.netflix.archaius.api.Config;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Plugin for setting up spectator to report correctly into the standard Netflix stack.
 */
@Singleton
final class Plugin {

  private static final String ENABLED_PROP = "spectator.nflx.enabled";

  private static final GcLogger GC_LOGGER = new GcLogger();

  private static final Logger LOGGER = LoggerFactory.getLogger(Plugin.class);

  /** Create a new instance. */
  @Inject
  Plugin(Registry registry, Config config) {

    final boolean enabled = config.getBoolean(ENABLED_PROP, true);
    if (enabled) {
      if (config.getBoolean("spectator.gc.loggingEnabled", true)) {
        GC_LOGGER.start(null);
        LOGGER.info("gc logging started");
      } else {
        LOGGER.info("gc logging is not enabled");
      }

      Jmx.registerStandardMXBeans(registry);
    } else {
      LOGGER.debug("plugin not enabled, set " + ENABLED_PROP + "=true to enable");
    }
  }
}
