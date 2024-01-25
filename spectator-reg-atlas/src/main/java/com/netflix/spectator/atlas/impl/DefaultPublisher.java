/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.netflix.spectator.atlas.Publisher;
import com.netflix.spectator.impl.StreamHelper;
import com.netflix.spectator.ipc.http.HttpClient;
import com.netflix.spectator.ipc.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class DefaultPublisher implements Publisher {

  private static final String CLOCK_SKEW_TIMER = "spectator.atlas.clockSkew";

  private final Logger logger = LoggerFactory.getLogger(AtlasRegistry.class);

  private final StreamHelper streamHelper = new StreamHelper();

  private final URI uri;
  private final URI evalUri;

  private final int connectTimeout;
  private final int readTimeout;
  private final int numThreads;

  private final Registry debugRegistry;

  private final HttpClient client;

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;

  private final ValidationHelper validationHelper;

  private ExecutorService senderPool;

  public DefaultPublisher(final AtlasConfig config) {
    this(config, null);
  }

  public DefaultPublisher(final AtlasConfig config, final HttpClient client) {
    this(config, client, config.debugRegistry());
  }

  public DefaultPublisher(
      final AtlasConfig config, final HttpClient client, final Registry registry) {

    this.uri = URI.create(config.uri());
    this.evalUri = URI.create(config.evalUri());
    this.connectTimeout = (int) config.connectTimeout().toMillis();
    this.readTimeout = (int) config.readTimeout().toMillis();
    this.numThreads = config.numThreads();
    this.debugRegistry = Optional.ofNullable(registry).orElse(new NoopRegistry());

    this.client = client != null ? client : HttpClient.create(debugRegistry);

    Function<String, String> replacementFunc =
        JsonUtils.createReplacementFunction(config.validTagCharacters());
    this.jsonMapper = JsonUtils.createMapper(new JsonFactory(), replacementFunc);
    this.smileMapper = JsonUtils.createMapper(new SmileFactory(), replacementFunc);

    this.validationHelper = new ValidationHelper(logger, jsonMapper, debugRegistry);
  }

  @Override
  public void init() {
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
      final long delta = debugRegistry.clock().wallTime() - responseTimestamp;
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

  @Override
  public CompletableFuture<Void> publish(PublishPayload payload) {
    Runnable task = () -> {
      try {
        if (logger.isTraceEnabled()) {
          logger.trace("publish payload: {}", jsonMapper.writeValueAsString(payload));
        }
        HttpResponse res = client.post(uri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .addHeader("Content-Encoding", "gzip")
            .withContent("application/x-jackson-smile", encodeBatch(payload))
            .send();
        Instant date = res.dateHeader("Date");
        recordClockSkew((date == null) ? 0L : date.toEpochMilli());
        validationHelper.recordResults(payload.getMetrics().size(), res);
      } catch (Exception e) {
        logger.error("failed to send metrics (uri={})", uri, e);
        validationHelper.incrementDroppedHttp(payload.getMetrics().size());
      }
    };
    return CompletableFuture.runAsync(task, senderPool);
  }

  @Override
  public CompletableFuture<Void> publish(EvalPayload payload) {
    Runnable task = () -> {
      try {
        String json = jsonMapper.writeValueAsString(payload);
        if (logger.isTraceEnabled()) {
          logger.trace("eval payload: {}", json);
        }
        client.post(evalUri)
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout)
            .withJsonContent(json)
            .send();
      } catch (Exception e) {
        logger.error("failed to send metrics for subscriptions (uri={})", evalUri, e);
      }
    };
    return CompletableFuture.runAsync(task, senderPool);
  }

  @Override
  public void close() throws IOException {
    if (senderPool != null) {
      senderPool.shutdown();
      senderPool = null;
    }
  }
}
