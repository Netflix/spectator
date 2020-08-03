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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Tag;

/**
 * Data source type for Atlas. See the
 * <a href="https://github.com/Netflix/atlas/wiki/Concepts#normalization">normalization</a>
 * docs for more information.
 */
enum DsType implements Tag {
  /** Sampled value that should be used as is without weighting. */
  gauge,

  /** Rate per second that should use weighted averaging during normalization. */
  rate,

  /**
   * Sum type used for inline aggregations on the backend. Sender must be careful to avoid
   * overcounting since the backend cannot dedup.
   */
  sum;

  @Override public String key() {
    return "atlas.dstype";
  }

  @Override public String value() {
    return name();
  }
}
