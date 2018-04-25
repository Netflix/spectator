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

import java.util.EnumSet;

/**
 * IPC metric names and associated metadata.
 */
public enum IpcMetric {
  /**
   * Timer recording the number and latency of outbound requests.
   */
  clientCall("ipc.client.call", EnumSet.noneOf(IpcTagKey.class)),

  /**
   * Timer recording the number and latency of inbound requests.
   */
  serverCall("ipc.server.call", EnumSet.noneOf(IpcTagKey.class)),

  /**
   * Number of outbound requests that are currently in flight.
   */
  clientInflight("ipc.client.inflight", EnumSet.noneOf(IpcTagKey.class)),

  /**
   * Number of inbound requests that are currently in flight.
   */
  serverInflight("ipc.server.inflight", EnumSet.noneOf(IpcTagKey.class));

  private final String metricName;
  private final EnumSet<IpcTagKey> requiredDimensions;

  /** Create a new instance. */
  IpcMetric(String metricName, EnumSet<IpcTagKey> requiredDimensions) {
    this.metricName = metricName;
    this.requiredDimensions = requiredDimensions;
  }

  /** Returns the metric name to use in the meter id. */
  public String metricName() {
    return metricName;
  }

  /** Returns the set of dimensions that are required for this metrics. */
  public EnumSet<IpcTagKey> requiredDimensions() {
    return requiredDimensions;
  }
}
