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
package com.netflix.spectator.spark;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.impl.AtomicDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Reporter for mapping data in a metrics3 registry to spectator.
 */
public final class SpectatorReporter extends ScheduledReporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpectatorReporter.class);

  /**
   * Return a builder for creating a spectator reported based on {@code registry}.
   */
  public static Builder forRegistry(MetricRegistry registry) {
    return new Builder(registry);
  }

  /**
   * Builder for configuring the spectator reporter.
   */
  public static final class Builder {
    private final MetricRegistry registry;
    private Registry spectatorRegistry;
    private NameFunction nameFunction = new NameFunction() {
      @Override public Id apply(String name) {
        return spectatorRegistry.createId(name);
      }
    };
    private ValueFunction valueFunction = new ValueFunction() {
      @Override public double convert(String name, double v) {
        return v;
      }
    };

    /**
     * By default none should match. Note the '$' sign is regex end of line so it cannot be
     * followed by characters and thus the pattern will not match anything.
     */
    private Pattern gaugeCounters = Pattern.compile("$NONE");

    /** Create a new instance. */
    Builder(MetricRegistry registry) {
      this.registry = registry;
    }

    /** Set the spectator registry to use. */
    public Builder withSpectatorRegistry(Registry r) {
      spectatorRegistry = r;
      return this;
    }

    /** Set the name mapping function to use. */
    public Builder withNameFunction(NameFunction f) {
      nameFunction = f;
      return this;
    }

    /** Set the value mapping function to use. */
    public Builder withValueFunction(ValueFunction f) {
      valueFunction = f;
      return this;
    }

    /** Set the regex for matching names of gauges that should be treated as counters. */
    public Builder withGaugeCounters(Pattern pattern) {
      gaugeCounters = pattern;
      return this;
    }

    /** Create a new instance of the reporter. */
    public SpectatorReporter build() {
      if (spectatorRegistry == null) {
        spectatorRegistry = Spectator.globalRegistry();
      }
      return new SpectatorReporter(
          registry, spectatorRegistry, nameFunction, valueFunction, gaugeCounters);
    }
  }

  private final Registry spectatorRegistry;
  private final NameFunction nameFunction;
  private final ValueFunction valueFunction;
  private final Pattern gaugeCounters;

  private final ConcurrentHashMap<String, AtomicDouble> gaugeDoubles = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> previousValues = new ConcurrentHashMap<>();

  /** Create a new instance. */
  SpectatorReporter(
      MetricRegistry metricRegistry,
      Registry spectatorRegistry,
      NameFunction nameFunction,
      ValueFunction valueFunction,
      Pattern gaugeCounters) {
    super(metricRegistry,
        "spectator",       // name
        MetricFilter.ALL,  // filter
        TimeUnit.SECONDS,  // rateUnit
        TimeUnit.SECONDS); // durationUnit
    this.spectatorRegistry = spectatorRegistry;
    this.nameFunction = nameFunction;
    this.valueFunction = valueFunction;
    this.gaugeCounters = gaugeCounters;
  }

  @SuppressWarnings("PMD.NPathComplexity")
  @Override public void report(
      SortedMap<String, Gauge> gauges,
      SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms,
      SortedMap<String, Meter> meters,
      SortedMap<String, Timer> timers) {
    LOGGER.debug("gauges {}, counters {}, histograms {}, meters {}, timers {}",
        gauges.size(), counters.size(), histograms.size(), meters.size(), timers.size());

    for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
      final Object obj = entry.getValue().getValue();
      if (obj instanceof Number) {
        if (gaugeCounters.matcher(entry.getKey()).matches()) {
          final long v = ((Number) obj).longValue();
          updateCounter(entry.getKey(), v);
        } else {
          final double v = ((Number) obj).doubleValue();
          setGaugeValue(entry.getKey(), v);
        }
      }
    }

    for (Map.Entry<String, Counter> entry : counters.entrySet()) {
      if (gaugeCounters.matcher(entry.getKey()).matches()) {
        updateCounter(entry.getKey(), entry.getValue().getCount());
      } else {
        setGaugeValue(entry.getKey(), entry.getValue().getCount());
      }
    }

    for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
      final Id id = nameFunction.apply(entry.getKey());
      if (id != null) {
        final DistributionSummary sHisto = spectatorRegistry.distributionSummary(id);
        final Histogram mHisto = entry.getValue();
        final long[] vs = mHisto.getSnapshot().getValues();
        for (long v : vs) {
          sHisto.record(v);
        }
      }
    }

    for (Map.Entry<String, Meter> entry : meters.entrySet()) {
      updateCounter(entry.getKey(), entry.getValue().getCount());
    }

    for (Map.Entry<String, Timer> entry : timers.entrySet()) {
      final Id id = nameFunction.apply(entry.getKey());
      if (id != null) {
        final com.netflix.spectator.api.Timer sTimer = spectatorRegistry.timer(id);
        final Timer mTimer = entry.getValue();
        final long[] vs = mTimer.getSnapshot().getValues();
        for (long v : vs) {
          sTimer.record(v, TimeUnit.NANOSECONDS);
        }
      }
    }
  }

  private void setGaugeValue(String name, double v) {
    AtomicDouble value = gaugeDoubles.get(name);
    if (value == null) {
      AtomicDouble tmp = new AtomicDouble(v);
      value = gaugeDoubles.putIfAbsent(name, tmp);
      if (value == null) {
        value = tmp;
        register(name, value);
      }
    }
    final double cv = valueFunction.convert(name, v);
    LOGGER.debug("setting gauge {} to {}", name, cv);
    value.set(cv);
  }

  private Id register(String name, AtomicDouble value) {
    Id id = nameFunction.apply(name);
    if (id != null) {
      spectatorRegistry.gauge(id, value);
    }
    return id;
  }

  private long getAndSetPrevious(String name, long newValue) {
    AtomicLong prev = previousValues.get(name);
    if (prev == null) {
      AtomicLong tmp = new AtomicLong(0L);
      prev = previousValues.putIfAbsent(name, tmp);
      prev = (prev == null) ? tmp : prev;
    }
    return prev.getAndSet(newValue);
  }

  private void updateCounter(String k, long curr) {
    final long prev = getAndSetPrevious(k, curr);
    final Id id = nameFunction.apply(k);
    if (id != null) {
      spectatorRegistry.counter(id).increment(curr - prev);
    }
  }

}
