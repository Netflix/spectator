package com.netflix.spectator.servo;

import com.netflix.spectator.api.Tag;

/**
 * The valid set of statistics that can be reported by timers and distribution summaries.
 */
enum Statistic implements Tag {
  /** Rate per second for calls to record. */
  count,

  /** The maximum amount recorded. */
  max,

  /** The sum of the amounts recorded. */
  totalAmount,

  /** The sum of the squares of the amounts recorded. */
  totalOfSquares,

  /** The sum of the times recorded. */
  totalTime;

  @Override public String key() {
    return "statistic";
  }

  @Override public String value() {
    return name();
  }
}
