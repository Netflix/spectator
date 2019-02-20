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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.Scheduler;
import com.netflix.spectator.ipc.http.HttpClient;
import com.netflix.spectator.ipc.http.HttpResponse;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;

/**
 * Registry for reporting deltas to an aggregation service. This registry is intended for
 * use-cases where the instance cannot maintain state over the step interval. For example,
 * if running via a FaaS system like AWS Lambda, the lifetime of an invocation can be quite
 * small. Thus this registry would track the deltas and rely on a separate service to
 * consolidate the state over time if needed.
 *
 * The registry should be tied to the lifecyle of the container to ensure that the last set
 * of deltas are flushed properly. This will happen automatically when calling {@link #stop()}.
 */
public final class StatelessRegistry extends AbstractRegistry {

  private final boolean enabled;
  private final Duration frequency;
  private final long meterTTL;
  private final int connectTimeout;
  private final int readTimeout;
  private final URI uri;
  private final int batchSize;
  private final Map<String, String> commonTags;

  private final HttpClient client;

  private Scheduler scheduler;

  /** Create a new instance. */
  public StatelessRegistry(Clock clock, StatelessConfig config) {
    super(clock, config);
    this.enabled = config.enabled();
    this.frequency = config.frequency();
    this.meterTTL = config.meterTTL().toMillis();
    this.connectTimeout = (int) config.connectTimeout().toMillis();
    this.readTimeout = (int) config.readTimeout().toMillis();
    this.uri = URI.create(config.uri());
    this.batchSize = config.batchSize();
    this.commonTags = config.commonTags();
    this.client = HttpClient.create(this);
  }

  /**
   * Start the scheduler to collect metrics data.
   */
  public void start() {
    if (scheduler == null) {
      if (enabled) {
        Scheduler.Options options = new Scheduler.Options()
            .withFrequency(Scheduler.Policy.FIXED_DELAY, frequency)
            .withInitialDelay(frequency)
            .withStopOnFailure(false);
        scheduler = new Scheduler(this, "spectator-reg-stateless", 1);
        scheduler.schedule(options, this::collectData);
        logger.info("started collecting metrics every {} reporting to {}", frequency, uri);
        logger.info("common tags: {}", commonTags);
      } else {
        logger.info("publishing is not enabled");
      }
    } else {
      logger.warn("registry already started, ignoring duplicate request");
    }
  }

  /**
   * Stop the scheduler reporting data.
   */
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdown();
      scheduler = null;
      logger.info("flushing metrics before stopping the registry");
      collectData();
      logger.info("stopped collecting metrics every {} reporting to {}", frequency, uri);
    } else {
      logger.warn("registry stopped, but was never started");
    }
  }

  private void collectData() {
    try {
      for (List<Measurement> batch : getBatches()) {
        byte[] payload = JsonUtils.encode(commonTags, batch);
        HttpResponse res = client.post(uri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withContent("application/json", payload)
            .compress(Deflater.BEST_SPEED)
            .send();
        if (res.status() != 200) {
          logger.warn("failed to send metrics, status {}: {}", res.status(), res.entityAsString());
        }
      }
      removeExpiredMeters();
    } catch (Exception e) {
      logger.warn("failed to send metrics", e);
    }
  }

  /** Get a list of all measurements from the registry. */
  List<Measurement> getMeasurements() {
    return stream()
        .filter(m -> !m.hasExpired())
        .flatMap(m -> StreamSupport.stream(m.measure().spliterator(), false))
        .collect(Collectors.toList());
  }

  /** Get a list of all measurements and break them into batches. */
  List<List<Measurement>> getBatches() {
    List<List<Measurement>> batches = new ArrayList<>();
    List<Measurement> ms = getMeasurements();
    for (int i = 0; i < ms.size(); i += batchSize) {
      List<Measurement> batch = ms.subList(i, Math.min(ms.size(), i + batchSize));
      batches.add(batch);
    }
    return batches;
  }

  @Override protected Counter newCounter(Id id) {
    return new StatelessCounter(id, clock(), meterTTL);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new StatelessDistributionSummary(id, clock(), meterTTL);
  }

  @Override protected Timer newTimer(Id id) {
    return new StatelessTimer(id, clock(), meterTTL);
  }

  @Override protected Gauge newGauge(Id id) {
    return new StatelessGauge(id, clock(), meterTTL);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return new StatelessMaxGauge(id, clock(), meterTTL);
  }

}
