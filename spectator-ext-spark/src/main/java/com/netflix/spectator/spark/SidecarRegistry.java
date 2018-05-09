/*
 * Copyright 2014-2106 Netflix, Inc.
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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkEnv;
import org.apache.spark.SparkEnv$;
import scala.Option;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registry that reports values to a sidecar process via an HTTP call.
 */
public class SidecarRegistry extends AbstractRegistry {

  private static final Callable<Map<String, String>> SPARK = new Callable<Map<String, String>>() {
    @Override public Map<String, String> call() throws Exception {
      SparkEnv env = SparkEnv$.MODULE$.get();
      if (env == null) {
        return Collections.emptyMap();
      } else {
        Map<String, String> tagMap = new HashMap<>();
        SparkConf conf = env.conf();
        put(tagMap, conf, "spark.app.name", "appName");
        return tagMap;
      }
    }

    private void put(Map<String, String> tags, SparkConf conf, String key, String tagName) {
      Option<String> opt = conf.getOption(key);
      if (opt.isDefined() && !"".equals(opt.get())) {
        tags.put(tagName, opt.get());
      }
    }
  };

  private ScheduledExecutorService executor;

  private final Counter numMessages;
  private final Counter numMeasurements;

  private final Callable<Map<String, String>> commonTags;

  /** Create a new instance. */
  public SidecarRegistry() {
    this(Clock.SYSTEM);
  }

  /** Create a new instance. */
  public SidecarRegistry(Clock clock) {
    this(clock, SPARK);
  }

  /** Create a new instance. */
  public SidecarRegistry(Clock clock, Callable<Map<String, String>> commonTags) {
    super(clock);
    numMessages = counter(createId("spectator.sidecar.numMessages"));
    numMeasurements = counter(createId("spectator.sidecar.numMeasurements"));
    this.commonTags = commonTags;
  }

  /**
   * Start sending data to the sidecar.
   *
   * @param url
   *     Location of the sidecar endpoint.
   * @param pollPeriod
   *     How frequently to poll the data and send to the sidecar.
   * @param pollUnit
   *     Unit for the {@code pollPeriod}.
   */
  public void start(final URL url, long pollPeriod, TimeUnit pollUnit) {
    logger.info("starting sidecar registry with url {} and poll period {} {}",
        url, pollPeriod, pollUnit);
    executor = Executors.newSingleThreadScheduledExecutor(
        r -> {
          final Thread t = new Thread(r, "spectator-sidecar");
          t.setDaemon(true);
          return t;
        }
    );

    final SidecarRegistry self = this;
    Runnable task = () -> {
      try {
        List<Measurement> ms = new ArrayList<>();
        for (Meter meter : self) {
          for (Measurement m : meter.measure()) {
            ms.add(m);
          }
        }
        postJson(url, ms);
      } catch (Exception e) {
        logger.error("failed to send data to sidecar", e);
      }
    };
    executor.scheduleWithFixedDelay(task, pollPeriod, pollPeriod, pollUnit);
  }

  /**
   * Stop sending data to the sidecar and shutdown the executor.
   */
  public void stop() {
    executor.shutdown();
    executor = null;
  }

  private String toJson(List<Measurement> ms, Map<String, String> tags) {
    final JsonArray items = new JsonArray();
    for (Measurement m : ms) {
      if (!Double.isNaN(m.value()) && !Double.isInfinite(m.value())) {
        items.add(toJson(m, tags));
      }
    }
    return items.toString();
  }

  private JsonObject toJson(Measurement m, Map<String, String> tags) {
    Map<String, String> tagMap = new HashMap<>();
    for (Tag t : m.id().tags()) {
      tagMap.put(t.key(), t.value());
    }
    tagMap.putAll(tags);

    final JsonObject obj = new JsonObject();
    obj.add("timestamp", m.timestamp());
    obj.add("type",      getType(m.id()));
    obj.add("name",      m.id().name());
    obj.add("tags",      toJson(tagMap));
    obj.add("value",     m.value());
    return obj;
  }

  private JsonObject toJson(Map<String, String> tags) {
    final JsonObject obj = new JsonObject();
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      obj.add(entry.getKey(), entry.getValue());
    }
    return obj;
  }

  private String getType(Id id) {
    for (Tag t : id.tags()) {
      if (t.key().equals("type")) {
        return t.value();
      }
    }
    return DataType.GAUGE.value();
  }

  private Map<String, String> getCommonTags() {
    try {
      return commonTags.call();
    } catch (Exception e) {
      logger.warn("failed to determine common tags", e);
      return null;
    }
  }

  private void postJson(URL url, List<Measurement> ms) throws Exception {
    final Map<String, String> tags = getCommonTags();
    if (!ms.isEmpty() && tags != null) {
      logger.info("sending {} messages to sidecar {} with tags {}", ms.size(), url.toString(), tags);
      numMessages.increment();
      numMeasurements.increment(ms.size());
      String json = toJson(ms, tags);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      try {
        con.setRequestMethod("POST");
        con.setDoInput(true);
        con.setDoOutput(true);
        try (OutputStream out = con.getOutputStream()) {
          out.write(json.getBytes("UTF-8"));
        }
        con.connect();

        int status = con.getResponseCode();
        if (status != 200) {
          logger.error("post to sidecar failed with status: " + status + ", payload: " + json);
        }
      } finally {
        con.disconnect();
      }
    }
  }

  @Override protected Counter newCounter(Id id) {
    return new SidecarCounter(clock(), id);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new SidecarDistributionSummary(clock(), id);
  }

  @Override protected Timer newTimer(Id id) {
    return new SidecarTimer(clock(), id);
  }

  @Override protected Gauge newGauge(Id id) {
    return new SidecarGauge(clock(), id);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    // Legacy sidecar does not provide a way to properly support max gauges. This is just
    // mapped to a gauge, so the last reported value will get used
    return new SidecarGauge(clock(), id);
  }
}
