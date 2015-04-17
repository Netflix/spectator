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

import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.Spectator;
import com.typesafe.config.ConfigFactory;
import org.apache.spark.metrics.sink.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Sink for exporting spark metrics to a prana sidecar.
 */
public class SparkSink implements Sink {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkSink.class);

  private static final String DEFAULT_URL = "http://localhost:8078/metrics";

  private final SpectatorReporter reporter;
  private final SidecarRegistry sidecarRegistry;

  private final long pollPeriod;
  private final TimeUnit pollUnit;

  private final URL url;

  /**
   * Create a new instance. Spark looks for a constructor with all three parameters, so the
   * {@code SecurityManager} needs to be in the signature even though it isn't used.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter")
  public SparkSink(
      Properties properties,
      MetricRegistry registry,
      org.apache.spark.SecurityManager manager) throws MalformedURLException {
    reporter = SpectatorReporter.forRegistry(registry)
        .withNameFunction(SparkNameFunction.fromConfig(ConfigFactory.load()))
        .withValueFunction(SparkValueFunction.fromConfig(ConfigFactory.load()))
        .build();
    pollPeriod = getPeriod(properties);
    pollUnit = getUnit(properties);
    url = URI.create(properties.getProperty("url", DEFAULT_URL)).toURL();
    sidecarRegistry = Spectator.registry().underlying(SidecarRegistry.class);
  }

  private long getPeriod(Properties properties) {
    final String v = properties.getProperty("period");
    return (v == null) ? 10L : Long.parseLong(v);
  }

  private TimeUnit getUnit(Properties properties) {
    final String v = properties.getProperty("unit");
    return (v == null) ? TimeUnit.SECONDS : TimeUnit.valueOf(v.toUpperCase(Locale.US));
  }

  @Override public void start() {
    LOGGER.info("starting poller");
    reporter.start(pollPeriod, pollUnit);
    if (sidecarRegistry != null) {
      sidecarRegistry.start(url, pollPeriod, pollUnit);
    }
  }

  @Override public void stop() {
    LOGGER.info("stopping poller");
    reporter.stop();
    if (sidecarRegistry != null) {
      sidecarRegistry.stop();
    }
  }

  @Override public void report() {
    LOGGER.info("reporting values");
    reporter.report();
  }
}
