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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.impl.AtomicDouble;
import com.netflix.spectator.impl.StepDouble;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Wraps a Micrometer MeterRegistry to make it conform to the Spectator API.
 */
public final class MicrometerRegistry implements Registry {

  private final MeterRegistry impl;

  private final ConcurrentHashMap<Id, Object> state = new ConcurrentHashMap<>();

  /** Create a new instance. */
  public MicrometerRegistry(MeterRegistry impl) {
    this.impl = impl;
  }

  private io.micrometer.core.instrument.Tag convert(Tag t) {
    return io.micrometer.core.instrument.Tag.of(t.key(), t.value());
  }

  private Iterable<io.micrometer.core.instrument.Tag> convert(Iterable<Tag> tags) {
    List<io.micrometer.core.instrument.Tag> micrometerTags = new ArrayList<>();
    for (Tag t : tags) {
      micrometerTags.add(convert(t));
    }
    return micrometerTags;
  }

  private Id convert(io.micrometer.core.instrument.Meter.Id id) {
    List<Tag> tags = id.getTags()
        .stream()
        .map(t -> Tag.of(t.getKey(), t.getValue()))
        .collect(Collectors.toList());
    return Id.create(id.getName()).withTags(tags);
  }

  private Meter convert(io.micrometer.core.instrument.Meter meter) {
    Id id = convert(meter.getId());
    switch (meter.getId().getType()) {
      case COUNTER:              return counter(id);
      case TIMER:                return timer(id);
      case DISTRIBUTION_SUMMARY: return distributionSummary(id);
      case GAUGE:                return gauge(id);
      default:                   return null;
    }
  }

  @Override public Clock clock() {
    return new MicrometerClock(impl.config().clock());
  }

  @Override public Id createId(String name) {
    return Id.create(name);
  }

  @Override public Id createId(String name, Iterable<Tag> tags) {
    return createId(name).withTags(tags);
  }

  @Override public void register(Meter meter) {
    PolledMeter.monitorMeter(this, meter);
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  @Override public Counter counter(Id id) {
    return new MicrometerCounter(id, impl.counter(id.name(), convert(id.tags())));
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    return new MicrometerDistributionSummary(id, impl.summary(id.name(), convert(id.tags())));
  }

  @Override public Timer timer(Id id) {
    return new MicrometerTimer(id, impl.timer(id.name(), convert(id.tags())));
  }

  @Override public Gauge gauge(Id id) {
    AtomicDouble value = new AtomicDouble(Double.NaN);
    io.micrometer.core.instrument.Gauge gauge = io.micrometer.core.instrument.Gauge
        .builder(id.name(), value, AtomicDouble::get)
        .tags(convert(id.tags()))
        .register(impl);
    return new MicrometerGauge(id, value::set, gauge);
  }

  @Override public Gauge maxGauge(Id id) {
    // Note: micrometer doesn't support this type directly so it uses an arbitrary
    // window of 1m
    StepDouble value = new StepDouble(Double.NaN, clock(), 60000L);
    io.micrometer.core.instrument.Gauge gauge = io.micrometer.core.instrument.Gauge
        .builder(id.name(), value, StepDouble::poll)
        .tags(convert(id.tags()))
        .register(impl);
    return new MicrometerGauge(id, v -> value.getCurrent().max(v), gauge);
  }

  @Override public Meter get(Id id) {
    try {
      return impl.get(id.name())
          .tags(convert(id.tags()))
          .meters()
          .stream()
          .filter(m -> id.equals(convert(m.getId())))
          .map(this::convert)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    } catch (MeterNotFoundException e) {
      return null;
    }
  }

  @Override public Iterator<Meter> iterator() {
    return impl.getMeters()
        .stream()
        .map(this::convert)
        .filter(Objects::nonNull)
        .iterator();
  }
}
