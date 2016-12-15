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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.Scheduler;
import com.netflix.spectator.sandbox.HttpClient;
import com.netflix.spectator.sandbox.HttpResponse;

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

  private static final String CLOCK_SKEW_TIMER = "spectator.atlas.clockSkew";

  private final Clock clock;

  private final boolean enabled;
  private final Duration step;
  private final long stepMillis;
  private final URI uri;

  private final boolean lwcEnabled;
  private final Duration configRefreshFrequency;
  private final URI configUri;
  private final URI evalUri;

  private final int connectTimeout;
  private final int readTimeout;
  private final int batchSize;
  private final int numThreads;
  private final Map<String, String> commonTags;

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;

  private Scheduler scheduler;

  private volatile List<Subscription> subscriptions;

  /** Create a new instance. */
  public AtlasRegistry(Clock clock, AtlasConfig config) {
    super(new StepClock(clock, config.step().toMillis()), config);
    this.clock = clock;

    this.enabled = config.enabled();
    this.step = config.step();
    this.stepMillis = step.toMillis();
    this.uri = URI.create(config.uri());

    this.lwcEnabled = config.lwcEnabled();
    this.configRefreshFrequency = config.configRefreshFrequency();
    this.configUri = URI.create(config.configUri());
    this.evalUri = URI.create(config.evalUri());

    this.connectTimeout = (int) config.connectTimeout().toMillis();
    this.readTimeout = (int) config.readTimeout().toMillis();
    this.batchSize = config.batchSize();
    this.numThreads = config.numThreads();
    this.commonTags = new TreeMap<>(config.commonTags());

    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer());
    this.jsonMapper = new ObjectMapper(new JsonFactory()).registerModule(module);
    this.smileMapper = new ObjectMapper(new SmileFactory()).registerModule(module);
  }

  /**
   * Start the scheduler to collect metrics data.
   */
  public void start() {
    if (scheduler == null) {
      // Setup main collection for publishing to Atlas
      if (enabled || lwcEnabled) {
        Scheduler.Options options = new Scheduler.Options()
            .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
            .withInitialDelay(Duration.ofMillis(getInitialDelay(stepMillis)))
            .withStopOnFailure(false);
        scheduler = new Scheduler(this, "spectator-reg-atlas", numThreads);
        scheduler.schedule(options, this::collectData);
        logger.info("started collecting metrics every {} reporting to {}", step, uri);
        logger.info("common tags: {}", commonTags);
      } else {
        logger.info("publishing is not enabled");
      }

      // Setup collection for subscriptions
      if (lwcEnabled) {
        Scheduler.Options options = new Scheduler.Options()
            .withFrequency(Scheduler.Policy.FIXED_DELAY, configRefreshFrequency)
            .withStopOnFailure(false);
        scheduler.schedule(options, this::fetchSubscriptions);
      } else {
        logger.info("subscriptions are not enabled");
      }
    } else {
      logger.warn("registry already started, ignoring duplicate request");
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
      logger.info("stopped collecting metrics every {}ms reporting to {}", step, uri);
    } else {
      logger.warn("registry stopped, but was never started");
    }
  }

  private void collectData() {
    // Send data for any subscriptions
    if (lwcEnabled) {
      try {
        handleSubscriptions();
      } catch (Exception e) {
        logger.warn("failed to handle subscriptions", e);
      }
    }

    // Publish to Atlas
    if (enabled) {
      try {
        for (List<Measurement> batch : getBatches()) {
          PublishPayload p = new PublishPayload(commonTags, batch);
          HttpResponse res = HttpClient.DEFAULT.newRequest("spectator-reg-atlas", uri)
              .withMethod("POST")
              .withConnectTimeout(connectTimeout)
              .withReadTimeout(readTimeout)
              .withContent("application/x-jackson-smile", smileMapper.writeValueAsBytes(p))
              .send();
          Instant date = res.dateHeader("Date");
          recordClockSkew((date == null) ? 0L : date.toEpochMilli());
        }
      } catch (Exception e) {
        logger.warn("failed to send metrics", e);
      }
    }
  }

  private void handleSubscriptions() {
    List<Subscription> subs = subscriptions;
    if (!subs.isEmpty()) {
      List<TagsValuePair> ms = getMeasurements().stream()
          .map(m -> TagsValuePair.from(commonTags, m))
          .collect(Collectors.toList());
      List<EvalPayload.Metric> metrics = new ArrayList<>();
      for (Subscription s : subs) {
        DataExpr expr = Parser.parseDataExpr(s.getExpression());
        for (TagsValuePair pair : expr.eval(ms)) {
          EvalPayload.Metric m = new EvalPayload.Metric(s.getId(), pair.tags(), pair.value());
          metrics.add(m);
        }
      }
      try {
        String json = jsonMapper.writeValueAsString(new EvalPayload(clock().wallTime(), metrics));
        HttpClient.DEFAULT.newRequest("spectator-lwc-eval", evalUri)
            .withMethod("POST")
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withJsonContent(json)
            .send()
            .decompress();
      } catch (Exception e) {
        logger.warn("failed to send metrics for subscriptions", e);
      }
    }
  }

  private void fetchSubscriptions() {
    try {
      HttpResponse res = HttpClient.DEFAULT.newRequest("spectator-lwc-subs", configUri)
          .withMethod("GET")
          .withConnectTimeout(connectTimeout)
          .withReadTimeout(readTimeout)
          .send()
          .decompress();
      if (res.status() != 200) {
        logger.warn("failed to update subscriptions, received status {}", res.status());
      } else {
        Subscriptions subs = jsonMapper.readValue(res.entity(), Subscriptions.class);
        subscriptions = subs.validated();
      }
    } catch (Exception e) {
      logger.warn("failed to send metrics", e);
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
      logger.debug("no date timestamp on response, cannot record skew");
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
      logger.debug("clock skew between client and server: {}ms", delta);
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

  @Override protected Gauge newGauge(Id id) {
    // Be sure to get StepClock so the measurements will have step aligned
    // timestamps.
    return new AtlasGauge(id, clock());
  }
}
