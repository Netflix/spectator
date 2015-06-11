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
package com.netflix.spectator.tdigest;

import com.netflix.spectator.api.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Plugin for managing the collection of digest measurements.
 */
@Singleton
class TDigestPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(TDigestPlugin.class);

  private final TDigestRegistry registry;
  private final TDigestWriter writer;
  private final TDigestConfig config;

  private ScheduledExecutorService executor;

  /** Create a new instance. */
  @Inject
  public TDigestPlugin(TDigestRegistry registry, TDigestWriter writer, TDigestConfig config) {
    this.registry = registry;
    this.writer = writer;
    this.config = config;
  }

  /**
   * Setup the background threads for collecting the digest measurements and sending to the
   * writer. The thread names will start with {@code TDigestPlugin}.
   */
  @PostConstruct
  public void init() {
    executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
          return new Thread(r, "TDigestPlugin");
        }
      });

    Runnable task = new Runnable() {
      @Override public void run() {
        try {
          writeData();
        } catch (Exception e) {
          LOGGER.error("failed to publish percentile data", e);
        }
      }
    };

    executor.scheduleAtFixedRate(task, 0L, config.getPollingFrequency(), TimeUnit.SECONDS);
  }

  /**
   * Shutdown the thread pools for collecting digest measurements.
   */
  @PreDestroy
  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
    try {
      writer.close();
    } catch (IOException e) {
      LOGGER.error("failed to close writer", e);
    }
  }

  /** Collect the data for the current interval and send to the writer. */
  void writeData() {
    LOGGER.debug("starting collection of digests");
    List<TDigestMeasurement> ms = new ArrayList<>();
    for (Meter m : registry) {
      if (m instanceof TDigestMeter) {
        TDigestMeasurement measurement = ((TDigestMeter) m).measureDigest();
        ms.add(measurement);
      }
    }
    if (!ms.isEmpty()) {
      LOGGER.debug("writing {} measurements", ms.size());
      try {
        writer.write(ms);
      } catch (IOException e) {
        LOGGER.error("failed to write measurements", e);
      }
    } else {
      LOGGER.debug("no digest measurements found, nothing to do");
    }
  }
}
