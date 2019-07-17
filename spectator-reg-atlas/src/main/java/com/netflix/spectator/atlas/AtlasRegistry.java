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
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.atlas.impl.Consolidator;
import com.netflix.spectator.atlas.impl.EvalPayload;
import com.netflix.spectator.atlas.impl.Evaluator;
import com.netflix.spectator.atlas.impl.MeasurementSerializer;
import com.netflix.spectator.atlas.impl.PublishPayload;
import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.Scheduler;
import com.netflix.spectator.ipc.http.HttpClient;
import com.netflix.spectator.ipc.http.HttpResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/**
 * Registry for reporting metrics to Atlas.
 */
@Singleton
public final class AtlasRegistry extends AbstractRegistry implements AutoCloseable {

  private static final String CLOCK_SKEW_TIMER = "spectator.atlas.clockSkew";

  private final Clock stepClock;

  private final AtlasConfig config;

  private final Duration step;
  private final long stepMillis;
  private final long meterTTL;
  private final URI uri;

  private final Duration lwcStep;
  private final long lwcStepMillis;
  private final Duration configRefreshFrequency;
  private final URI evalUri;

  private final int connectTimeout;
  private final int readTimeout;
  private final int batchSize;
  private final int numThreads;
  private final Map<String, String> commonTags;

  private final AsciiSet charset;
  private final Map<String, AsciiSet> overrides;

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;

  private final Registry debugRegistry;

  private final RollupPolicy rollupPolicy;

  private final HttpClient client;

  private Scheduler scheduler;

  private final SubscriptionManager subManager;
  private final Evaluator evaluator;

  private long lastPollTimestamp = -1L;
  private final Map<Id, Consolidator> atlasMeasurements = new HashMap<>();

  /** Create a new instance. */
  @Inject
  public AtlasRegistry(Clock clock, AtlasConfig config) {
    super(clock, config);
    this.config = config;
    this.stepClock = new StepClock(clock, config.lwcStep().toMillis());

    this.step = config.step();
    this.stepMillis = step.toMillis();
    this.meterTTL = config.meterTTL().toMillis();
    this.uri = URI.create(config.uri());

    this.lwcStep = config.lwcStep();
    this.lwcStepMillis = lwcStep.toMillis();
    if (lwcStepMillis > stepMillis) {
      throw new IllegalArgumentException(
          "lwcStep cannot be larger than step (" + lwcStep + " > " + step + ")");
    }
    if (stepMillis % lwcStepMillis != 0) {
      throw new IllegalArgumentException(
          "step is not an even multiple of lwcStep (" + step + " % " + lwcStep + " != 0)");
    }

    this.configRefreshFrequency = config.configRefreshFrequency();
    this.evalUri = URI.create(config.evalUri());

    this.connectTimeout = (int) config.connectTimeout().toMillis();
    this.readTimeout = (int) config.readTimeout().toMillis();
    this.batchSize = config.batchSize();
    this.numThreads = config.numThreads();
    this.commonTags = new TreeMap<>(config.commonTags());

    this.charset = AsciiSet.fromPattern(config.validTagCharacters());
    this.overrides = config.validTagValueCharacters()
        .keySet().stream()
        .collect(Collectors.toMap(k -> k, AsciiSet::fromPattern));
    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer(charset, overrides));
    this.jsonMapper = new ObjectMapper(new JsonFactory()).registerModule(module);
    this.smileMapper = new ObjectMapper(new SmileFactory()).registerModule(module);

    this.debugRegistry = Optional.ofNullable(config.debugRegistry()).orElse(this);

    this.rollupPolicy = config.rollupPolicy();

    this.client = HttpClient.create(debugRegistry);

    this.subManager = new SubscriptionManager(jsonMapper, client, clock, config);
    this.evaluator = new Evaluator(commonTags, this::toMap, lwcStepMillis);

    if (config.autoStart()) {
      start();
    }
  }

  /**
   * Start the scheduler to collect metrics data.
   */
  public void start() {
    if (scheduler == null) {
      logger.info("common tags: {}", commonTags);

      // Setup main collection for publishing to Atlas
      Scheduler.Options options = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
          .withInitialDelay(Duration.ofMillis(getInitialDelay(stepMillis)))
          .withStopOnFailure(false);
      scheduler = new Scheduler(debugRegistry, "spectator-reg-atlas", numThreads);
      scheduler.schedule(options, this::sendToAtlas);
      logger.info("started collecting metrics every {} reporting to {}", step, uri);

      // Setup collection for LWC
      Scheduler.Options lwcOptions = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, lwcStep)
          .withInitialDelay(Duration.ofMillis(getInitialDelay(lwcStepMillis)))
          .withStopOnFailure(false);
      scheduler.schedule(lwcOptions, this::sendToLWC);

      // Setup refresh of LWC subscription data
      Scheduler.Options subOptions = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_DELAY, configRefreshFrequency)
          .withStopOnFailure(false);
      scheduler.schedule(subOptions, this::fetchSubscriptions);
    } else {
      logger.warn("registry already started, ignoring duplicate request");
    }
  }

  /**
   * Avoid collecting right on boundaries to minimize transitions on step longs
   * during a collection. Randomly distribute across the middle of the step interval.
   */
  long getInitialDelay(long stepSize) {
    long now = clock().wallTime();
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

  /**
   * Stop the scheduler reporting Atlas data. This is the same as calling {@link #stop()} and
   * is included to allow the registry to be stopped correctly when used with DI frameworks that
   * support lifecycle management.
   */
  @Override public void close() {
    stop();
  }

  /** Returns the timestamp of the last completed interval for the specified step size. */
  private long lastCompletedTimestamp(long s) {
    long now = clock().wallTime();
    return now / s * s;
  }

  void sendToAtlas() {
    if (config.enabled()) {
      long t = lastCompletedTimestamp(stepMillis);
      pollMeters(t);
      logger.debug("sending to Atlas for time: {}", t);
      try {
        for (List<Measurement> batch : getBatches(t)) {
          PublishPayload p = new PublishPayload(commonTags, batch);
          if (logger.isTraceEnabled()) {
            logger.trace("publish payload: {}", jsonMapper.writeValueAsString(p));
          }
          HttpResponse res = client.post(uri)
              .withConnectTimeout(connectTimeout)
              .withReadTimeout(readTimeout)
              .withContent("application/x-jackson-smile", smileMapper.writeValueAsBytes(p))
              .compress(Deflater.BEST_SPEED)
              .send();
          Instant date = res.dateHeader("Date");
          recordClockSkew((date == null) ? 0L : date.toEpochMilli());
        }
      } catch (Exception e) {
        logger.warn("failed to send metrics (uri={})", uri, e);
      }
    } else {
      logger.debug("publishing is disabled, skipping collection");
    }

    // Clean up any expired meters, do this regardless of whether it is enabled to avoid
    // a memory leak
    removeExpiredMeters();
  }

  void sendToLWC() {
    long t = lastCompletedTimestamp(lwcStepMillis);
    if (config.enabled() || config.lwcEnabled()) {
      // If either are enabled we poll the meters for each step interval to flush the
      // data into the consolidator
      pollMeters(t);
    }
    if (config.lwcEnabled()) {
      logger.debug("sending to LWC for time: {}", t);
      try {
        EvalPayload payload = evaluator.eval(t);
        String json = jsonMapper.writeValueAsString(payload);
        if (logger.isTraceEnabled()) {
          logger.trace("eval payload: {}", json);
        }
        client.post(evalUri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withJsonContent(json)
            .send()
            .decompress();
      } catch (Exception e) {
        logger.warn("failed to send metrics for subscriptions (uri={})", evalUri, e);
      }
    } else {
      logger.debug("lwc is disabled, skipping subscriptions");
    }
  }

  /** Collect measurements from all of the meters in the registry. */
  synchronized void pollMeters(long t) {
    if (t > lastPollTimestamp) {
      logger.debug("collecting data for time: {}", t);
      // Be sure to call measurements() on the superclass. The overridden version for the
      // registry is for public consumption to get consolidated values for the publish step
      // rather than the step for the meters which is needed here.
      super.measurements().forEach(m -> {
        if ("jvm.gc.pause".equals(m.id().name())) {
          logger.trace("received measurement for time: {}: {}", t, m);
        }

        // Update the map for data to go to the Atlas storage layer
        Consolidator consolidator = atlasMeasurements.get(m.id());
        if (consolidator == null) {
          int multiple = (int) (stepMillis / lwcStepMillis);
          consolidator = Consolidator.create(m.id(), stepMillis, multiple);
          atlasMeasurements.put(m.id(), consolidator);
        }
        consolidator.update(m);

        // Update aggregators for streaming
        evaluator.update(m);
      });
      lastPollTimestamp = t;
    }
  }

  /**
   * Removes expired meters from the registry. This is public to allow some integration
   * from third parties. Behavior may change in the future. It is strongly advised to only
   * interact with AtlasRegistry using the interface provided by Registry.
   */
  @SuppressWarnings("PMD.UselessOverridingMethod")
  @Override public void removeExpiredMeters() {
    // Overridden to increase visibility from protected in base class to public
    super.removeExpiredMeters();
  }

  private void fetchSubscriptions() {
    if (config.lwcEnabled()) {
      subManager.refresh();
      evaluator.sync(subManager.subscriptions());
    } else {
      logger.debug("lwc is disabled, skipping subscription config refresh");
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
      final long delta = clock().wallTime() - responseTimestamp;
      if (delta >= 0L) {
        // Local clock is running fast compared to the server. Note this should also be the
        // common case for if the clocks are in sync as there will be some delay for the server
        // response to reach this node.
        debugRegistry.timer(CLOCK_SKEW_TIMER, "id", "fast").record(delta, TimeUnit.MILLISECONDS);
      } else {
        // Local clock is running slow compared to the server. This means the response timestamp
        // appears to be after the current time on this node. The timer will ignore negative
        // values so we negate and record it with a different id.
        debugRegistry.timer(CLOCK_SKEW_TIMER, "id", "slow").record(-delta, TimeUnit.MILLISECONDS);
      }
      logger.debug("clock skew between client and server: {}ms", delta);
    }
  }

  private Map<String, String> toMap(Id id) {
    Map<String, String> tags = new HashMap<>();

    for (Tag t : id.tags()) {
      String k = charset.replaceNonMembers(t.key(), '_');
      String v = overrides.getOrDefault(k, charset).replaceNonMembers(t.value(), '_');
      tags.put(k, v);
    }

    String name = overrides.getOrDefault("name", charset).replaceNonMembers(id.name(), '_');
    tags.put("name", name);

    return tags;
  }

  /**
   * Get a list of all consolidated measurements intended to be sent to Atlas and break them
   * into batches.
   */
  synchronized List<List<Measurement>> getBatches(long t) {
    int n = atlasMeasurements.size();
    debugRegistry.distributionSummary("spectator.registrySize").record(n);
    List<Measurement> input = new ArrayList<>(n);
    Iterator<Map.Entry<Id, Consolidator>> it = atlasMeasurements.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Id, Consolidator> entry = it.next();
      Consolidator consolidator = entry.getValue();

      // Ensure it has been updated for this interval
      consolidator.update(t, Double.NaN);

      // Add the measurement to the list
      double v = consolidator.value(t);
      if (!Double.isNaN(v)) {
        input.add(new Measurement(entry.getKey(), t, v));
      }

      // Clean up if there is no longer a need to preserve the state for this id
      if (consolidator.isEmpty()) {
        it.remove();
      }
    }

    List<Measurement> ms = rollupPolicy.apply(input);
    debugRegistry.distributionSummary("spectator.rollupResultSize").record(ms.size());

    List<List<Measurement>> batches = new ArrayList<>();
    for (int i = 0; i < ms.size(); i += batchSize) {
      List<Measurement> batch = ms.subList(i, Math.min(ms.size(), i + batchSize));
      batches.add(batch);
    }
    return batches;
  }

  @Override public Stream<Measurement> measurements() {
    long t = lastCompletedTimestamp(stepMillis);
    pollMeters(t);
    return getBatches(t).stream().flatMap(List::stream);
  }

  @Override protected Counter newCounter(Id id) {
    return new AtlasCounter(id, clock(), meterTTL, lwcStepMillis);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new AtlasDistributionSummary(id, clock(), meterTTL, lwcStepMillis);
  }

  @Override protected Timer newTimer(Id id) {
    return new AtlasTimer(id, clock(), meterTTL, lwcStepMillis);
  }

  @Override protected Gauge newGauge(Id id) {
    // Be sure to get StepClock so the measurements will have step aligned
    // timestamps.
    return new AtlasGauge(id, stepClock, meterTTL);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return new AtlasMaxGauge(id, clock(), meterTTL, lwcStepMillis);
  }
}
