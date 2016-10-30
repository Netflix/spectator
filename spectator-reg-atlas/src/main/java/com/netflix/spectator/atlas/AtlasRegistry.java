/*
 * Copyright 2014-2016 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.Scheduler;
import com.netflix.spectator.sandbox.HttpClient;
import com.netflix.spectator.sandbox.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Registry for reporting metrics to Atlas.
 */
public final class AtlasRegistry extends AbstractRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(AtlasRegistry.class);

  private static final String CLOCK_SKEW_TIMER = "spectator.atlas.clockSkew";

  private final Clock clock;
  private final Duration step;
  private final long stepMillis;
  private final URI uri;
  private final int connectTimeout;
  private final int readTimeout;
  private final int batchSize;
  private final int numThreads;
  private final Map<String, String> commonTags;

  private Scheduler scheduler;

  private final ObjectMapper mapper;

  /** Create a new instance. */
  public AtlasRegistry(Clock clock, AtlasConfig config) {
    super(new StepClock(clock, config.step().toMillis()), config);
    this.clock = clock;
    this.step = config.step();
    this.stepMillis = step.toMillis();
    this.uri = URI.create(config.uri());
    this.connectTimeout = (int) config.connectTimeout().toMillis();
    this.readTimeout = (int) config.readTimeout().toMillis();
    this.batchSize = config.batchSize();
    this.numThreads = config.numThreads();
    this.commonTags = new TreeMap<>(config.commonTags());

    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer());
    this.mapper = new ObjectMapper(new SmileFactory()).registerModule(module);
  }

  /**
   * Start the scheduler to collect metrics data.
   */
  public void start() {
    if (scheduler == null) {
      Scheduler.Options options = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
          .withInitialDelay(Duration.ofMillis(getInitialDelay(stepMillis)))
          .withStopOnFailure(false);

      scheduler = new Scheduler(this, "atlas-registry", numThreads);
      scheduler.schedule(options, this::collectData);
      LOGGER.info("started collecting metrics every {} reporting to {}", step, uri);
      LOGGER.info("common tags: {}", commonTags);
    } else {
      LOGGER.warn("registry already started, ignoring duplicate request");
    }
  }

  /**
   * Avoid collecting right on boundaries to minimize transitions on step longs
   * during a collection. Randomly distribute across the middle of the step interval.
   */
  long getInitialDelay(long stepSize) {
    long now = clock.wallTime();
    long stepBoundary = now / stepSize * stepSize;

    // Buffer by 10% of the step interval on either side
    long offset = stepSize / 10;

    // Check if the current delay is within the acceptable range
    long delay = now - stepBoundary;
    if (delay < offset) {
      return delay + offset;
    } else if (delay > stepSize - offset) {
      return stepSize - offset;
    } else {
      return delay;
    }
  }

  /**
   * Stop the scheduler reporting Atlas data.
   */
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdown();
      scheduler = null;
      LOGGER.info("stopped collecting metrics every {}ms reporting to {}", step, uri);
    } else {
      LOGGER.warn("registry stopped, but was never started");
    }
  }

  private void collectData() {
    try {
      for (List<Measurement> batch : getBatches()) {
        PublishPayload p = new PublishPayload(commonTags, batch);
        HttpResponse res = HttpClient.DEFAULT.newRequest("spectator-reg-atlas", uri)
            .withMethod("POST")
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withContent("application/x-jackson-smile", mapper.writeValueAsBytes(p))
            .send();
        Instant date = res.dateHeader("Date");
        recordClockSkew((date == null) ? 0L : date.toEpochMilli());
      }
    } catch (Exception e) {
      LOGGER.warn("failed to send metrics", e);
    }
  }

  /**
   * Record the difference between the date response time and the local time on the server.
   * This is used to get a rough idea of the amount of skew in the environment. Ideally it
   * should be fairly small. The date header will only have seconds so we expect to regularly
   * have differences of up to 1 second. Note, that it is a rough estimate and could be
   * elevated because of unrelated problems like GC or network delays.
   */
  private void recordClockSkew(long responseTimestamp) {
    if (responseTimestamp == 0L) {
      LOGGER.debug("no date timestamp on response, cannot record skew");
    } else {
      final long delta = clock.wallTime() - responseTimestamp;
      if (delta >= 0L) {
        // Local clock is running fast compared to the server. Note this should also be the
        // common case for if the clocks are in sync as there will be some delay for the server
        // response to reach this node.
        timer(CLOCK_SKEW_TIMER, "id", "fast").record(delta, TimeUnit.MILLISECONDS);
      } else {
        // Local clock is running slow compared to the server. This means the response timestamp
        // appears to be after the current time on this node. The timer will ignore negative
        // values so we negate and record it with a different id.
        timer(CLOCK_SKEW_TIMER, "id", "slow").record(-delta, TimeUnit.MILLISECONDS);
      }
      LOGGER.debug("clock skew between client and server: {}ms", delta);
    }
  }

  /** Get a list of all measurements from the registry. */
  List<Measurement> getMeasurements() {
    return stream()
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
    return new AtlasCounter(id, clock, stepMillis);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new AtlasDistributionSummary(id, clock, stepMillis);
  }

  @Override protected Timer newTimer(Id id) {
    return new AtlasTimer(id, clock, stepMillis);
  }
}
