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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.impl.AsciiSet;

import java.util.Collections;

/** Base class for core meter types used by {@link SidecarRegistry}. */
abstract class SidecarMeter implements Meter {

  private static final AsciiSet ALLOWED_CHARS = AsciiSet.fromPattern("-._A-Za-z0-9~^");

  /** Base identifier for all measurements supplied by this meter. */
  protected final Id id;

  /** Prefix string for line to output to SpectatorD. */
  protected final String idString;

  /** Create a new instance. */
  SidecarMeter(Id id, char type) {
    this.id = id;
    this.idString = createIdString(id, type);
  }

  private String replaceInvalidChars(String s) {
    return ALLOWED_CHARS.replaceNonMembers(s, '_');
  }

  private String createIdString(Id id, char type) {
    StringBuilder builder = new StringBuilder();
    builder.append(type).append(':').append(replaceInvalidChars(id.name()));
    int n = id.size();
    for (int i = 1; i < n; ++i) {
      String k = replaceInvalidChars(id.getKey(i));
      String v = replaceInvalidChars(id.getValue(i));
      builder.append(',').append(k).append('=').append(v);
    }
    return builder.append(':').toString();
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }
}
