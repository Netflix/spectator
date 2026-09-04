/*
 * Copyright 2014-2026 Netflix, Inc.
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

import com.netflix.spectator.api.Meter;

/**
 * Meter that can be told when the registry has removed it, so that code holding a reference can
 * find out cheaply. The registry marks the meter as part of the removal, which is off the update
 * path, leaving the read to be a plain field access.
 *
 * <p>The alternative signal is {@code hasExpired()}, which for a meter with a TTL means reading
 * the wall clock. That is fine on a cleanup pass and expensive on a path that runs for every
 * update.</p>
 *
 * <p>Implementing this is optional. {@code AbstractRegistry} marks the meters it removes when
 * they support it and leaves the rest alone, so a registry can adopt it independently.</p>
 *
 * <p><b>This class is an internal implementation detail only intended for use within
 * spectator. It is subject to change without notice.</b></p>
 */
public interface RemovableMeter extends Meter {

  /**
   * Whether the registry has removed this meter. Treat it as a hint that the meter should be
   * looked up again rather than as proof it is gone: nothing stops a registry from handing the
   * same instance back out, and a marked meter that is still reachable is wasteful but not
   * wrong.
   */
  boolean isRemoved();

  /**
   * Record that the registry has removed this meter. Called after the entry is gone, so anything
   * that sees the mark can no longer find the meter registered.
   *
   * <p>May be called more than once for the same meter, and is not a lifecycle callback: it must
   * stay cheap and must not release anything the meter still needs, since a reference held
   * elsewhere can keep being updated afterwards.</p>
   */
  void markRemoved();
}
