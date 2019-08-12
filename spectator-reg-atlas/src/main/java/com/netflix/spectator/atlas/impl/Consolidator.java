/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.impl.Preconditions;

/**
 * Consolidates a set of measurements collected at a smaller step size to a set a measurement
 * at a larger step size that is an even multiple of the primary.
 */
public interface Consolidator {

  /**
   * Update the state with a new primary datapoint.
   *
   * @param t
   *     Timestamp for the new value.
   * @param v
   *     Value to include in the consolidated aggregate. If there is no new measurement, then
   *     {@code NaN} can be used to force the completion of the consolidated value.
   */
  void update(long t, double v);

  /**
   * Update the state with a new primary datapoint. See {@link #update(long, double)} for more
   * details.
   */
  default void update(Measurement m) {
    update(m.timestamp(), m.value());
  }

  /**
   * Return the consolidated value for the specified timestamp. The timestamp should be for the
   * last completed interval.
   */
  double value(long t);

  /**
   * Returns true if the state is the same as it would be if a new instance of the consolidator
   * was created. This can be used to check if the current instance can be garbage collected.
   */
  boolean isEmpty();

  /**
   * Create a new consolidator instance based on the statistic in the id. If not statistic tag
   * is present, then it will be treated as a gauge.
   *
   * @param id
   *     Id for the measurement to consolidate.
   * @param step
   *     Consolidated step size.
   * @param multiple
   *     Multiple for the consolidate step size. The primary step is {@code step / multiple}.
   * @return
   *     A new consolidator instance.
   */
  static Consolidator create(Id id, long step, int multiple) {
    return create(Utils.getTagValue(id, "statistic"), step, multiple);
  }

  /**
   * Create a new consolidator instance based on the specified statistic.
   *
   * @param statistic
   *     Statistic used to determine which consolidation function to use.
   * @param step
   *     Consolidated step size.
   * @param multiple
   *     Multiple for the consolidate step size. The primary step is {@code step / multiple}.
   * @return
   *     A new consolidator instance.
   */
  static Consolidator create(Statistic statistic, long step, int multiple) {
    return create(statistic.name(), step, multiple);
  }

  /**
   * Create a new consolidator instance based on the specified statistic.
   *
   * @param statistic
   *     Statistic used to determine which consolidation function to use. If the statistic is
   *     null or unknown, the it will be treated as a gauge.
   * @param step
   *     Consolidated step size.
   * @param multiple
   *     Multiple for the consolidate step size. The primary step is {@code step / multiple}.
   * @return
   *     A new consolidator instance.
   */
  static Consolidator create(String statistic, long step, int multiple) {
    if (multiple == 1) {
      return new None();
    }
    Consolidator consolidator;
    switch (statistic == null ? "gauge" : statistic) {
      case "count":
      case "totalAmount":
      case "totalTime":
      case "totalOfSquares":
      case "percentile":
        consolidator = new Avg(step, multiple);
        break;
      default:
        consolidator = new Max(step, multiple);
        break;
    }
    return consolidator;
  }

  /**
   * Placeholder implementation used when the the primary and consolidated step sizes are
   * the same.
   */
  final class None implements Consolidator {

    private long timestamp;
    private double value;

    None() {
      this.timestamp = -1L;
      this.value = Double.NaN;
    }

    @Override public void update(long t, double v) {
      if (t > timestamp) {
        timestamp = t;
        value = v;
      }
    }

    @Override public double value(long t) {
      return timestamp == t ? value : Double.NaN;
    }

    @Override public boolean isEmpty() {
      return Double.isNaN(value);
    }
  }

  /** Base class for consolidator implementations. */
  abstract class AbstractConsolidator implements Consolidator {
    /** Consolidated step size. */
    protected final long step;

    /** Multiple from primary to consolidated step. */
    protected final int multiple;

    private long timestamp;

    private double current;
    private double previous;

    AbstractConsolidator(long step, int multiple) {
      Preconditions.checkArg(step > 0L, "step must be > 0");
      Preconditions.checkArg(multiple > 0L, "multiple must be > 0");
      this.step = step;
      this.multiple = multiple;
      this.timestamp = -1L;
      this.current = Double.NaN;
      this.previous = Double.NaN;
    }

    private long roundToConsolidatedStep(long t) {
      return (t % step == 0L) ? t : t / step * step + step;
    }

    /** Combines two values to create an aggregate used as the consolidated value. */
    protected abstract double aggregate(double v1, double v2);

    /** Performs any final computation on the aggregated value. */
    protected abstract double complete(double v);

    @Override public void update(long rawTimestamp, double value) {
      long t = roundToConsolidatedStep(rawTimestamp);
      if (timestamp < 0) {
        timestamp = t;
      }
      if (t == timestamp) {
        // Updating the same datapoint, just apply the update
        current = aggregate(current, value);
        if (rawTimestamp == timestamp) {
          // On the boundary, roll the value
          previous = complete(current);
          current = Double.NaN;
          timestamp = t + step;
        }
      } else if (t > timestamp) {
        if (t - timestamp == step) {
          // Previous time interval
          previous = complete(current);
        } else {
          // Gap in the data, clear out the previous sample
          previous = Double.NaN;
        }
        current = value;
        timestamp = t;
      }
    }

    @Override public double value(long t) {
      return (timestamp - t == step) ? previous : Double.NaN;
    }

    @Override public boolean isEmpty() {
      return Double.isNaN(previous) && Double.isNaN(current);
    }
  }

  /**
   * Averages the raw values. The denominator will always be the multiple so missing data
   * for some intervals will be treated as a zero. For counters this should give an accurate
   * average rate per second across the consolidated interval.
   */
  final class Avg extends AbstractConsolidator {

    Avg(long step, int multiple) {
      super(step, multiple);
    }

    @Override protected double aggregate(double v1, double v2) {
      return Double.isNaN(v1) ? v2 : Double.isNaN(v2) ? v1 : v1 + v2;
    }

    @Override protected double complete(double v) {
      return v / multiple;
    }
  }

  /**
   * Selects the maximum value that is reported. Used for max gauges and similar types to
   * preserve the overall max.
   */
  final class Max extends AbstractConsolidator {

    Max(long step, int multiple) {
      super(step, multiple);
    }

    @Override protected double aggregate(double v1, double v2) {
      return Double.isNaN(v1) ? v2 : Double.isNaN(v2) ? v1 : Math.max(v1, v2);
    }

    @Override protected double complete(double v) {
      return v;
    }
  }
}
