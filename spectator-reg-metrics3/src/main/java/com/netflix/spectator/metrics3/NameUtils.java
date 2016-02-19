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
final class NameUtils {

  /**
   * Utility Class.
   * @deprecated
   */
  private NameUtils() { }

  /**
   * Convert an Spectator {@link Id} to metrics3 name.
   *
   * @param id Spectator {@link Id}
   * @return Metrics3 name
   */
  static String toMetricName(Id id) {
    Id normalized = Utils.normalize(id);
    StringBuilder buf = new StringBuilder();
    buf.append(normalized.name());
    for (Tag t : normalized.tags()) {
      buf.append('.').append(t.key()).append('-').append(t.value());
    }
    return buf.toString();
  }
}
