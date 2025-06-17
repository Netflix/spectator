/*
 * Copyright 2014-2024 Netflix, Inc.
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
package com.netflix.spectator.ipc.http;

import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.IpcLoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default instances for IPC loggers used with HTTP client wrapper. */
final class Defaults {

  private Defaults() {
  }

  /** Use HttpClient for the slf4j logger instance. */
  static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

  /** Logger config that delegates to system properties. */
  static final IpcLoggerConfig IPC_LOGGER_CONFIG = Defaults::getProperty;

  /** IPC logger using global registry and with default config. */
  static final IpcLogger IPC_LOGGER =
      new IpcLogger(Spectator.globalRegistry(), LOGGER, IPC_LOGGER_CONFIG);

  /** Delegate to system properties and change default to disable inflight metrics. */
  private static String getProperty(String key) {
    String dflt = "spectator.ipc.inflight-metrics-enabled".equals(key) ? "false" : null;
    return System.getProperty(key, dflt);
  }
}
