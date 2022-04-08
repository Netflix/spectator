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
package com.netflix.spectator.sidecar;

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;

/**
 * Max gauge that writes updates in format compatible with SpectatorD.
 */
class SidecarMaxGauge extends SidecarMeter implements Gauge {

  private final SidecarWriter writer;

  /** Create a new instance. */
  SidecarMaxGauge(Id id, SidecarWriter writer) {
    super(id, 'm');
    this.writer = writer;
  }

  @Override public void set(double v) {
    writer.write(idString, v);
  }

  @Override public double value() {
    return Double.NaN;
  }
}
