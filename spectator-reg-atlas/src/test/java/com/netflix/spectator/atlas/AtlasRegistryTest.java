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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.atlas.impl.PublishPayload;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.http.HttpClient;
import com.netflix.spectator.ipc.http.HttpRequestBuilder;
import com.netflix.spectator.ipc.http.HttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;


public class AtlasRegistryTest {

  private ManualClock clock;
  private AtlasRegistry registry;

  private AtlasConfig newConfig() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("atlas.enabled", "false");
    props.put("atlas.step", "PT10S");
    props.put("atlas.lwc.step", "PT10S");
    props.put("atlas.batchSize", "3");

    return new AtlasConfig() {
      @Override public String get(String k) {
        return props.get(k);
      }

      @Override public Registry debugRegistry() {
        return new NoopRegistry();
      }
    };
  }

  private List<Measurement> getMeasurements() {
    clock.setWallTime(clock.wallTime() + 10000);
    return registry.measurements().collect(Collectors.toList());
  }

  private List<List<Measurement>> getBatches() {
    long step = 10000;
    if (clock.wallTime() == 0L) {
      clock.setWallTime(step);
    }
    long t = clock.wallTime() / step * step;
    registry.pollMeters(t);
    return registry
        .getBatches(t)
        .stream()
        .map(RollupPolicy.Result::measurements)
        .collect(Collectors.toList());
  }

  @BeforeEach
  public void before() {
    clock = new ManualClock();
    registry = new AtlasRegistry(clock, newConfig());
  }

  @Test
  public void measurementsEmpty() {
    Assertions.assertEquals(0, getMeasurements().size());
  }

  @Test
  public void measurementsWithCounter() {
    registry.counter("test").increment();
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void measurementsWithTimer() {
    registry.timer("test").record(42, TimeUnit.NANOSECONDS);
    Assertions.assertEquals(4, getMeasurements().size());
  }

  @Test
  public void measurementsWithDistributionSummary() {
    registry.distributionSummary("test").record(42);
    Assertions.assertEquals(4, getMeasurements().size());
  }

  @Test
  public void measurementsWithGauge() {
    registry.gauge("test").set(4.0);
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void measurementsIgnoresNaN() {
    registry.gauge("test").set(Double.NaN);
    Assertions.assertEquals(0, getMeasurements().size());
  }

  @Test
  public void measurementsWithMaxGauge() {
    registry.maxGauge(registry.createId("test")).set(4.0);
    Gauge g = registry.maxGauge("test");
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void batchesEmpty() {
    Assertions.assertEquals(0, getBatches().size());
  }

  @Test
  public void batchesExact() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assertions.assertEquals(3, getBatches().size());
    for (List<Measurement> batch : getBatches()) {
      Assertions.assertEquals(3, batch.size());
    }
  }

  @Test
  public void batchesLastPartial() {
    for (int i = 0; i < 7; ++i) {
      registry.counter("" + i).increment();
    }
    List<List<Measurement>> batches = getBatches();
    Assertions.assertEquals(3, batches.size());
    for (int i = 0; i < batches.size(); ++i) {
      Assertions.assertEquals((i < 2) ? 3 : 1, batches.get(i).size());
    }
  }

  @Test
  public void initialDelayTooCloseToStart() {
    long d = newConfig().initialPollingDelay(clock, 10000);
    Assertions.assertEquals(1000, d);
  }

  @Test
  public void initialDelayTooCloseToEnd() {
    clock.setWallTime(19123);
    long d = newConfig().initialPollingDelay(clock, 10000);
    Assertions.assertEquals(9000, d);
  }

  @Test
  public void initialDelayOk() {
    clock.setWallTime(12123);
    long d = newConfig().initialPollingDelay(clock, 10000);
    Assertions.assertEquals(2123, d);
  }

  @Test
  public void initialDelayTooCloseToStartSmallStep() {
    long d = newConfig().initialPollingDelay(clock, 5000);
    Assertions.assertEquals(500, d);
  }

  @Test
  public void initialDelayTooCloseToEndSmallStep() {
    clock.setWallTime(19623);
    long d = newConfig().initialPollingDelay(clock, 5000);
    Assertions.assertEquals(877, d);
  }

  @Test
  public void batchesExpiration() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assertions.assertEquals(3, getBatches().size());
    for (List<Measurement> batch : getBatches()) {
      Assertions.assertEquals(3, batch.size());
    }

    clock.setWallTime(Duration.ofMinutes(15).toMillis() + 1);
    registry.removeExpiredMeters();
    Assertions.assertEquals(0, getBatches().size());
  }

  @Test
  public void keepsNonExpired() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    registry.sendToAtlas();
    Assertions.assertEquals(3, getBatches().size());
  }

  @Test
  public void removesExpired() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    clock.setWallTime(Duration.ofMinutes(15).toMillis() + 1);
    registry.sendToAtlas();
    Assertions.assertEquals(0, getBatches().size());
  }

  @Test
  public void shutdownWithoutStarting() {
    AtlasRegistry r = new AtlasRegistry(
        Clock.SYSTEM,
        k -> k.equals("atlas.enabled") ? "true" : null);
    r.close();
  }

  @Test
  public void flushOnShutdown() {
    List<PublishPayload> payloads = new ArrayList<>();
    HttpClient client = uri -> new TestRequestBuilder(uri, payloads);
    ManualClock c = new ManualClock();
    AtlasRegistry r = new AtlasRegistry(c, new TestConfig(), client);
    r.start();

    c.setWallTime(58_000);
    r.maxGauge("test").set(1.0);
    c.setWallTime(62_000);
    r.maxGauge("test").set(2.0);
    r.close();

    Assertions.assertEquals(2, payloads.size());

    Assertions.assertEquals(1.0, getValue(payloads.get(0)));
    Assertions.assertEquals(2.0, getValue(payloads.get(1)));
  }

  @Test
  public void flushOnShutdownCounter() {
    List<PublishPayload> payloads = new ArrayList<>();
    HttpClient client = uri -> new TestRequestBuilder(uri, payloads);
    ManualClock c = new ManualClock();
    AtlasRegistry r = new AtlasRegistry(c, new TestConfig(), client);
    r.start();

    c.setWallTime(58_000);
    r.counter("test").increment();
    c.setWallTime(62_000);
    r.counter("test").add(60.0);
    r.close();

    Assertions.assertEquals(2, payloads.size());

    Assertions.assertEquals(1.0 / 60.0, getValue(payloads.get(0)));
    Assertions.assertEquals(1.0, getValue(payloads.get(1)));
  }

  private double getValue(PublishPayload payload) {
    return payload.getMetrics()
        .stream()
        .filter(m -> m.id().name().equals("test"))
        .mapToDouble(Measurement::value)
        .sum();
  }

  private static class TestConfig implements AtlasConfig {

    @Override public String get(String k) {
      return null;
    }

    @Override public boolean enabled() {
      return true;
    }

    @Override public long initialPollingDelay(Clock clock, long stepSize) {
      // use a long delay to avoid actually sending unless triggered by tests
      return 6_000_000;
    }
  }

  private static class TestRequestBuilder extends HttpRequestBuilder {

    private static final JsonFactory FACTORY = new SmileFactory();

    private final List<PublishPayload> payloads;

    TestRequestBuilder(URI uri, List<PublishPayload> payloads) {
      super(new IpcLogger(new NoopRegistry()), uri);
      this.payloads = payloads;
    }

    private Map<String, String> decodeTags(JsonParser parser) throws IOException {
      Map<String, String> tags = new HashMap<>();
      parser.nextToken();
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String k = parser.getCurrentName();
        String v = parser.nextTextValue();
        tags.put(k, v);
      }
      return tags;
    }

    private Measurement decodeMeasurement(JsonParser parser) throws IOException {
      Map<String, String> tags = Collections.emptyMap();
      long timestamp = -1L;
      double value = Double.NaN;
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String field = parser.getCurrentName();
        switch (field) {
          case "tags":
            tags = decodeTags(parser);
            break;
          case "timestamp":
            timestamp = parser.nextLongValue(-1L);
            break;
          case "value":
            parser.nextToken();
            value = parser.getDoubleValue();
            break;
          default:
            throw new IllegalArgumentException("unexpected field: " + field);
        }
      }

      String name = tags.remove("name");
      Id id = Id.create(name).withTags(tags);
      return new Measurement(id, timestamp, value);
    }

    private PublishPayload decodePayload(JsonParser parser) throws IOException {
      Map<String, String> tags = Collections.emptyMap();
      List<Measurement> metrics = new ArrayList<>();
      parser.nextToken();
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String field = parser.getCurrentName();
        switch (field) {
          case "tags":
            tags = decodeTags(parser);
            break;
          case "metrics":
            parser.nextToken();
            while (parser.nextToken() == JsonToken.START_OBJECT) {
              metrics.add(decodeMeasurement(parser));
            }
            break;
          default:
            throw new IllegalArgumentException("unexpected field: " + field);
        }
      }
      return new PublishPayload(tags, metrics);
    }

    @Override public HttpRequestBuilder withContent(String type, byte[] content) {
      try {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(content));
             JsonParser parser = FACTORY.createParser(in)) {
          payloads.add(decodePayload(parser));
        }
      } catch (IOException e) {
        e.printStackTrace();
        throw new UncheckedIOException(e);
      }
      return this;
    }

    @Override protected HttpResponse sendImpl() throws IOException {
      return new HttpResponse(200, Collections.emptyMap());
    }
  }
}
