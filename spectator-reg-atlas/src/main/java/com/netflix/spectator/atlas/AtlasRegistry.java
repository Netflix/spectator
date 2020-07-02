/*
 * Copyright 2014-2020 Netflix, Inc.
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
import com.netflix.spectator.api.Meter;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Registry for reporting metrics to Atlas.
 */
@Singleton
public final class AtlasRegistry extends AbstractRegistry implements AutoCloseable {

  private static final String CLOCK_SKEW_TIMER = "spectator.atlas.clockSkew";
  private static final String PUBLISH_TASK_TIMER = "spectator.atlas.publishTaskTime";

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

  private final Function<String, String> fixTagString;

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;

  private final Registry debugRegistry;

  private final RollupPolicy rollupPolicy;

  private final HttpClient client;

  private Scheduler scheduler;
  private ExecutorService senderPool;

  private final SubscriptionManager subManager;
  private final Evaluator evaluator;

  private long lastPollTimestamp = -1L;
  private final Map<Id, Consolidator> atlasMeasurements = new LinkedHashMap<>();

  private final StreamHelper streamHelper = new StreamHelper();

  /** Create a new instance. */
  @Inject
  public AtlasRegistry(Clock clock, AtlasConfig config) {
    super(new OverridableClock(clock), config);
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

    this.fixTagString = createReplacementFunction(config.validTagCharacters());
    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer(fixTagString));
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

  private Function<String, String> createReplacementFunction(String pattern) {
    if (pattern == null) {
      return Function.identity();
    } else {
      AsciiSet set = AsciiSet.fromPattern(pattern);
      return s -> set.replaceNonMembers(s, '_');
    }
  }

  /**
   * Start the scheduler to collect metrics data.
   */
  public void start() {
    if (scheduler == null) {
      logger.info("common tags: {}", commonTags);

      // Thread pool for encoding the requests and sending
      ThreadFactory factory = new ThreadFactory() {
        private final AtomicInteger next = new AtomicInteger();

        @Override public Thread newThread(Runnable r) {
          final String name = "spectator-atlas-publish-" + next.getAndIncrement();
          final Thread t = new Thread(r, name);
          t.setDaemon(true);
          return t;
        }
      };
      senderPool = Executors.newFixedThreadPool(numThreads, factory);

      // Setup main collection for publishing to Atlas
      Scheduler.Options options = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
          .withInitialDelay(Duration.ofMillis(config.initialPollingDelay(clock(), stepMillis)))
          .withStopOnFailure(false);
      scheduler = new Scheduler(debugRegistry, "spectator-reg-atlas", numThreads);
      scheduler.schedule(options, this::sendToAtlas);
      logger.info("started collecting metrics every {} reporting to {}", step, uri);

      // Setup collection for LWC
      Scheduler.Options lwcOptions = new Scheduler.Options()
          .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, lwcStep)
          .withInitialDelay(Duration.ofMillis(config.initialPollingDelay(clock(), lwcStepMillis)))
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
   * Stop the scheduler reporting Atlas data.
   */
  public void stop() {
    // Shutdown backround tasks to collect data
    if (scheduler != null) {
      scheduler.shutdown();
      scheduler = null;
      logger.info("stopped collecting metrics every {}ms reporting to {}", step, uri);
    } else {
      logger.warn("registry stopped, but was never started");
    }

    // Flush data to Atlas
    try {
      logger.info("flushing data for final interval to Atlas");
      OverridableClock overridableClock = (OverridableClock) clock();
      long now = clock().wallTime();
      overridableClock.setWallTime(now / lwcStepMillis * lwcStepMillis + lwcStepMillis);
      pollMeters(overridableClock.wallTime());
      overridableClock.setWallTime(now / stepMillis * stepMillis + stepMillis);
      sendToAtlas();
    } catch (Exception e) {
      logger.warn("failed to flush data to Atlas", e);
    }

    // Shutdown pool used for sending metrics
    if (senderPool != null) {
      senderPool.shutdown();
      senderPool = null;
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

  private Timer publishTaskTimer(String id) {
    return debugRegistry.timer(PUBLISH_TASK_TIMER, "id", id);
  }

  /**
   * Optimization to reduce the allocations for encoding the payload. The ByteArrayOutputStreams
   * get reused to avoid the allocations for growing the buffer. In addition, the data is gzip
   * compressed inline rather than relying on the HTTP client to do it. This reduces the buffer
   * sizes and avoids another copy step and allocation for creating the compressed buffer.
   */
  private byte[] encodeBatch(PublishPayload payload) throws IOException {
    ByteArrayOutputStream baos = streamHelper.getOrCreateStream();
    try (GzipLevelOutputStream out = new GzipLevelOutputStream(baos)) {
      smileMapper.writeValue(out, payload);
    }
    return baos.toByteArray();
  }

  private void sendBatch(List<Measurement> batch) {
    publishTaskTimer("sendBatch").record(() -> {
      try {
        PublishPayload p = new PublishPayload(commonTags, batch);
        if (logger.isTraceEnabled()) {
          logger.trace("publish payload: {}", jsonMapper.writeValueAsString(p));
        }
        HttpResponse res = client.post(uri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .addHeader("Content-Encoding", "gzip")
            .withContent("application/x-jackson-smile", encodeBatch(p))
            .send();
        Instant date = res.dateHeader("Date");
        recordClockSkew((date == null) ? 0L : date.toEpochMilli());
      } catch (Exception e) {
        logger.warn("failed to send metrics (uri={})", uri, e);
      }
    });
  }

  void sendToAtlas() {
    publishTaskTimer("sendToAtlas").record(() -> {
      if (config.enabled() && senderPool != null) {
        long t = lastCompletedTimestamp(stepMillis);
        pollMeters(t);
        logger.debug("sending to Atlas for time: {}", t);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Measurement> batch : getBatches(t)) {
          CompletableFuture<Void> future = CompletableFuture.runAsync(
              () -> sendBatch(batch), senderPool);
          futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      } else {
        logger.debug("publishing is disabled, skipping collection");
      }

      // Clean up any expired meters, do this regardless of whether it is enabled to avoid
      // a memory leak
      removeExpiredMeters();
    });
  }

  private void sendBatchLWC(EvalPayload batch) {
    publishTaskTimer("sendBatchLWC").record(() -> {
      try {
        String json = jsonMapper.writeValueAsString(batch);
        if (logger.isTraceEnabled()) {
          logger.trace("eval payload: {}", json);
        }
        client.post(evalUri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withJsonContent(json)
            .send();
      } catch (Exception e) {
        logger.warn("failed to send metrics for subscriptions (uri={})", evalUri, e);
      }
    });
  }

  void sendToLWC() {
    publishTaskTimer("sendToLWC").record(() -> {
      long t = lastCompletedTimestamp(lwcStepMillis);
      //if (config.enabled() || config.lwcEnabled()) {
      // If either are enabled we poll the meters for each step interval to flush the
      // data into the consolidator
      // NOTE: temporarily to avoid breaking some internal use-cases, the meters will always
      // be polled to ensure the consolidated values for the main publishing path will be
      // correct. Once those use-cases have been transitioned the condition should be enabled
      // again.
      pollMeters(t);
      //}
      if (config.lwcEnabled()) {
        logger.debug("sending to LWC for time: {}", t);
        try {
          EvalPayload payload = evaluator.eval(t);
          if (!payload.getMetrics().isEmpty()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (EvalPayload batch : payload.toBatches(batchSize)) {
              CompletableFuture<Void> future = CompletableFuture.runAsync(
                  () -> sendBatchLWC(batch), senderPool);
              futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
          }
        } catch (Exception e) {
          logger.warn("failed to send metrics for subscriptions (uri={})", evalUri, e);
        }
      } else {
        logger.debug("lwc is disabled, skipping subscriptions");
      }
    });
  }

  /** Collect measurements from all of the meters in the registry. */
  synchronized void pollMeters(long t) {
    publishTaskTimer("pollMeters").record(() -> {
      if (t > lastPollTimestamp) {
        MeasurementConsumer consumer = (id, timestamp, value) -> {
          // Update the map for data to go to the Atlas storage layer
          Consolidator consolidator = atlasMeasurements.get(id);
          if (consolidator == null) {
            int multiple = (int) (stepMillis / lwcStepMillis);
            consolidator = Consolidator.create(id, stepMillis, multiple);
            atlasMeasurements.put(id, consolidator);
          }
          consolidator.update(timestamp, value);

          // Update aggregators for streaming
          evaluator.update(id, timestamp, value);
        };
        logger.debug("collecting measurements for time: {}", t);
        publishTaskTimer("pollMeasurements").record(() -> {
          for (Meter meter : this) {
            ((AtlasMeter) meter).measure(consumer);
          }
        });
        lastPollTimestamp = t;
      }
    });
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
      String k = fixTagString.apply(t.key());
      String v = fixTagString.apply(t.value());
      tags.put(k, v);
    }

    String name = fixTagString.apply(id.name());
    tags.put("name", name);

    return tags;
  }

  /**
   * Get a list of all consolidated measurements intended to be sent to Atlas and break them
   * into batches.
   */
  synchronized List<List<Measurement>> getBatches(long t) {
    List<List<Measurement>> batches = new ArrayList<>();
    publishTaskTimer("getBatches").record(() -> {
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

      for (int i = 0; i < ms.size(); i += batchSize) {
        List<Measurement> batch = ms.subList(i, Math.min(ms.size(), i + batchSize));
        batches.add(batch);
      }
    });
    return batches;
  }

  @Override public Stream<Measurement> measurements() {
    long t = lastCompletedTimestamp(stepMillis);
    pollMeters(t);
    removeExpiredMeters();
    return getBatches(t).stream().flatMap(List::stream);
  }

  @Override protected Counter newCounter(Id id) {
    return new AtlasCounter(this, id, clock(), meterTTL, lwcStepMillis);
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
    return new AtlasGauge(this, id, stepClock, meterTTL);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return new AtlasMaxGauge(this, id, clock(), meterTTL, lwcStepMillis);
  }
}
