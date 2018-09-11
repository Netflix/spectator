/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.metrics3;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Utils;

/**
 * Utility class with static methods for creating names to Metrics based on Spectator
 * {@link com.netflix.spectator.api.Id}.
 *
 * @author Kennedy Oliveira
 */
final class DefaultNameUtils implements NameUtils {

  /**
   * Utility Class.
   */
  private DefaultNameUtils() { }

  /**
   * Convert an Spectator {@link Id} to metrics3 name.
   *
   * @param id Spectator {@link Id}
   * @return Metrics3 name
   */
  public String toMetricName(Id id) {
    Id normalized = Utils.normalize(id);
    StringBuilder buf = new StringBuilder();
    buf.append(normalized.name());
    for (Tag t : normalized.tags()) {
      buf.append('.').append(t.key()).append('-').append(t.value());
    }
    return buf.toString();
  }

  /**
   * Default instance.
   */
  static NameUtils INSTANCE = new DefaultNameUtils();
}
