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
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.atlas.impl.MeasurementSerializer;
import com.netflix.spectator.atlas.impl.Subscription;
import com.netflix.spectator.atlas.impl.Subscriptions;
import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.ipc.http.HttpClient;
import com.netflix.spectator.ipc.http.HttpRequestBuilder;
import com.netflix.spectator.ipc.http.HttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


public class SubscriptionManagerTest {

  private SimpleModule module = new SimpleModule()
      .addSerializer(
          Measurement.class,
          new MeasurementSerializer(AsciiSet.fromPattern("a-z"), Collections.emptyMap()));

  private ObjectMapper mapper = new ObjectMapper(new JsonFactory()).registerModule(module);

  private SubscriptionManager newInstance(ManualClock clock, HttpResponse... responses) {
    final AtomicInteger pos = new AtomicInteger();
    HttpClient client = uri -> new HttpRequestBuilder(HttpClient.DEFAULT_LOGGER, uri) {
      @Override public HttpResponse send() {
        return responses[pos.getAndIncrement()];
      }
    };
    return new SubscriptionManager(mapper, client, clock, v -> null);
  }

  private Set<Subscription> set(Subscription... subs) {
    return new HashSet<>(Arrays.asList(subs));
  }

  private byte[] json(Subscription... subs) throws Exception {
    Subscriptions payload = new Subscriptions().withExpressions(Arrays.asList(subs));
    return mapper.writeValueAsBytes(payload);
  }

  private Subscription sub(int i) {
    return new Subscription()
        .withId("" + i)
        .withExpression("name," + i + ",:eq,:sum")
        .withFrequency(60000);
  }

  private HttpResponse ok(byte[] data) {
    return new HttpResponse(200, Collections.emptyMap(), data);
  }

  @Test
  public void emptyExpressionList() {
    ManualClock clock = new ManualClock();
    byte[] data = "{\"expressions\":[]}".getBytes(StandardCharsets.UTF_8);
    SubscriptionManager mgr = newInstance(clock, ok(data));
    mgr.refresh();
    Assertions.assertTrue(mgr.subscriptions().isEmpty());
  }

  @Test
  public void singleExpression() throws Exception {
    ManualClock clock = new ManualClock();
    byte[] data = json(sub(1));
    SubscriptionManager mgr = newInstance(clock, ok(data));
    mgr.refresh();
    Assertions.assertEquals(set(sub(1)), new HashSet<>(mgr.subscriptions()));
  }

  @Test
  public void expiration() throws Exception {
    ManualClock clock = new ManualClock();

    byte[] data1 = json(sub(1), sub(2)); // Initial set of 2 expressions
    byte[] data2 = json(sub(2));            // Final set with only expression 2

    SubscriptionManager mgr = newInstance(clock, ok(data1), ok(data2), ok(data2));

    mgr.refresh();
    Assertions.assertEquals(set(sub(1), sub(2)), new HashSet<>(mgr.subscriptions()));

    // Should still contain 1 because it hasn't expired
    mgr.refresh();
    Assertions.assertEquals(set(sub(1), sub(2)), new HashSet<>(mgr.subscriptions()));

    // Should have removed 1 because it has expired
    clock.setWallTime(Duration.ofMinutes(20).toMillis());
    mgr.refresh();
    Assertions.assertEquals(set(sub(2)), new HashSet<>(mgr.subscriptions()));
  }

  @Test
  public void startsFailing() throws Exception {
    ManualClock clock = new ManualClock();
    byte[] data = json(sub(1));

    HttpResponse ok = new HttpResponse(200, Collections.emptyMap(), data);
    HttpResponse error = new HttpResponse(500, Collections.emptyMap(), new byte[0]);

    SubscriptionManager mgr = newInstance(clock, ok, error);
    mgr.refresh();
    Assertions.assertEquals(set(sub(1)), new HashSet<>(mgr.subscriptions()));

    // Double check it is not expired
    clock.setWallTime(Duration.ofMinutes(20).toMillis());
    mgr.refresh();
    Assertions.assertEquals(set(sub(1)), new HashSet<>(mgr.subscriptions()));
  }

  @Test
  public void alwaysFailing() throws Exception {
    ManualClock clock = new ManualClock();
    byte[] data = json(sub(1));

    HttpResponse error = new HttpResponse(500, Collections.emptyMap(), new byte[0]);

    SubscriptionManager mgr = newInstance(clock, error, error);
    mgr.refresh();
    Assertions.assertTrue(mgr.subscriptions().isEmpty());

    // Double check it is not expired
    clock.setWallTime(Duration.ofMinutes(20).toMillis());
    mgr.refresh();
    Assertions.assertTrue(mgr.subscriptions().isEmpty());
  }

  @Test
  public void notModified() throws Exception {
    ManualClock clock = new ManualClock();
    byte[] data = json(sub(1));

    Map<String, List<String>> headers =
        Collections.singletonMap("ETag", Collections.singletonList("12345"));
    HttpResponse ok = new HttpResponse(200, headers, data);
    HttpResponse notModified = new HttpResponse(304, Collections.emptyMap(), new byte[0]);

    SubscriptionManager mgr = newInstance(clock, ok, notModified);
    mgr.refresh();
    Assertions.assertEquals(set(sub(1)), new HashSet<>(mgr.subscriptions()));

    // Double check it is not expired
    clock.setWallTime(Duration.ofMinutes(20).toMillis());
    mgr.refresh();
    Assertions.assertEquals(set(sub(1)), new HashSet<>(mgr.subscriptions()));
  }

  @Test
  public void invalidPayload() {
    ManualClock clock = new ManualClock();
    byte[] data = "[]".getBytes(StandardCharsets.UTF_8);
    SubscriptionManager mgr = newInstance(clock, ok(data));
    mgr.refresh();
    Assertions.assertTrue(mgr.subscriptions().isEmpty());
  }
}
