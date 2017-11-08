/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;
import com.typesafe.config.Config;
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
import java.util.regex.Pattern;

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

  private GcLogger gcLogger;

  /**
   * Create a new instance. Spark looks for a constructor with all three parameters, so the
   * {@code SecurityManager} needs to be in the signature even though it isn't used.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter")
  public SparkSink(
      Properties properties,
      MetricRegistry registry,
      org.apache.spark.SecurityManager manager) throws MalformedURLException {
    final Config config = loadConfig();
    sidecarRegistry = new SidecarRegistry();
    reporter = SpectatorReporter.forRegistry(registry)
        .withSpectatorRegistry(sidecarRegistry)
        .withNameFunction(SparkNameFunction.fromConfig(config, sidecarRegistry))
        .withValueFunction(SparkValueFunction.fromConfig(config))
        .withGaugeCounters(Pattern.compile(config.getString("spectator.spark.gauge-counters")))
        .build();
    pollPeriod = getPeriod(properties);
    pollUnit = getUnit(properties);
    url = URI.create(properties.getProperty("url", DEFAULT_URL)).toURL();

    // If there is a need to collect application metrics from jobs running on Spark, then
    // this should be enabled. The apps can report to the global registry and it will get
    // picked up by the Spark integration.
    if (shouldAddToGlobal(properties)) {
      Spectator.globalRegistry().add(sidecarRegistry);
    }
  }

  private Config loadConfig() {
    return ConfigFactory.load(pickClassLoader());
  }

  @SuppressWarnings("PMD.UseProperClassLoader")
  private ClassLoader pickClassLoader() {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      return getClass().getClassLoader();
    } else {
      return cl;
    }
  }

  private void startJvmCollection() {
    try {
      Jmx.registerStandardMXBeans(sidecarRegistry);
      gcLogger = new GcLogger();
      gcLogger.start(null);
    } catch (Exception e) {
      LOGGER.error("failed to start collection of jvm stats", e);
      throw e;
    }
  }

  private long getPeriod(Properties properties) {
    final String v = properties.getProperty("period");
    return (v == null) ? 10L : Long.parseLong(v);
  }

  private TimeUnit getUnit(Properties properties) {
    final String v = properties.getProperty("unit");
    return (v == null) ? TimeUnit.SECONDS : TimeUnit.valueOf(v.toUpperCase(Locale.US));
  }

  private boolean shouldAddToGlobal(Properties properties) {
    final String v = properties.getProperty("addToGlobalRegistry");
    return (v == null) || Boolean.valueOf(v);
  }

  @Override public void start() {
    LOGGER.info("starting poller");
    reporter.start(pollPeriod, pollUnit);
    startJvmCollection();
    if (sidecarRegistry != null) {
      sidecarRegistry.start(url, pollPeriod, pollUnit);
    }
  }

  @Override public void stop() {
    LOGGER.info("stopping poller");
    reporter.stop();
    gcLogger.stop();
    if (sidecarRegistry != null) {
      sidecarRegistry.stop();
    }
  }

  @Override public void report() {
    LOGGER.info("reporting values");
    reporter.report();
  }
}
