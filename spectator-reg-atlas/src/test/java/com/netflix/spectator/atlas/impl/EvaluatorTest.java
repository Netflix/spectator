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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class EvaluatorTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);

  private List<Measurement> data(String name, double... vs) {
    List<Measurement> ms = new ArrayList<>();
    for (int i = 0; i < vs.length; ++i) {
      String pos = String.format("%03d", i);
      String value = String.format("%f", vs[i]);
      ms.add(new Measurement(registry.createId(name, "i", pos, "v", value), 0L, vs[i]));
    }
    return ms;
  }

  private List<Measurement> counterData(String name, double... vs) {
    List<Measurement> ms = new ArrayList<>();
    for (int i = 0; i < vs.length; ++i) {
      String pos = String.format("%03d", i);
      String value = String.format("%f", vs[i]);
      Id id = registry.createId(name, "i", pos, "v", value, "statistic", "count");
      ms.add(new Measurement(id, 0L, vs[i]));
    }
    return ms;
  }

  private Evaluator newEvaluator(String... commonTags) {
    return newEvaluator(false, commonTags);
  }

  private Evaluator newEvaluator(boolean delayGauge, String... commonTags) {
    EvaluatorConfig config = new EvaluatorConfig() {
      @Override public long evaluatorStepSize() {
        return 5000L;
      }

      @Override public Map<String, String> commonTags() {
        return tags(commonTags);
      }

      @Override public boolean delayGaugeAggregation() {
        return delayGauge;
      }
    };
    return new Evaluator(config);
  }

  private Map<String, String> tags(String... ts) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < ts.length; i += 2) {
      map.put(ts[i], ts[i + 1]);
    }
    return map;
  }

  private Subscription newSubscription(String name, String expr) {
    return new Subscription().withId(name).withExpression(expr).withFrequency(5000);
  }

  private EvalPayload sort(EvalPayload p) {
    List<EvalPayload.Metric> ms = p.getMetrics();
    ms.sort(Comparator.comparing(EvalPayload.Metric::getId));
    return new EvalPayload(p.getTimestamp(), p.getMetrics());
  }

  @Test
  public void noSubsForGroup() {
    Evaluator evaluator = newEvaluator();
    EvalPayload payload = evaluator.eval(0L);
    EvalPayload expected = new EvalPayload(0L, Collections.emptyList());
    Assertions.assertEquals(expected, payload);
  }

  @Test
  public void sumAndMaxForGroup() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:sum"));
    subs.add(newSubscription("max", ":true,:max"));

    Evaluator evaluator = newEvaluator();
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("max", Collections.emptyMap(), 3.0));
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 6.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assertions.assertEquals(expected, sort(payload));
  }

  @Test
  public void updateSub() {

    // Eval with sum
    List<Subscription> sumSub = new ArrayList<>();
    sumSub.add(newSubscription("sum", ":true,:sum"));
    Evaluator evaluator = newEvaluator();
    evaluator.sync(sumSub);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));
    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 6.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assertions.assertEquals(expected, payload);

    // Update to use max instead
    List<Subscription> maxSub = new ArrayList<>();
    maxSub.add(newSubscription("sum", ":true,:max"));
    evaluator.sync(maxSub);
    payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));
    metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 3.0));
    expected = new EvalPayload(0L, metrics);
    Assertions.assertEquals(expected, payload);
  }

  @Test
  public void commonTagsMatch() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", "app,www,:eq,name,foo,:eq,:and,:sum"));

    Evaluator evaluator = newEvaluator("app", "www");
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", tags("app", "www", "name", "foo"), 6.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assertions.assertEquals(expected, payload);
  }

  @Test
  public void commonTagsNoMatch() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", "app,abc,:eq,name,foo,:eq,:and,:sum"));

    Evaluator evaluator = newEvaluator("app", "www");
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    EvalPayload expected = new EvalPayload(0L, Collections.emptyList());
    Assertions.assertEquals(expected, payload);
  }

  @Test
  public void commonTagsGroupBy() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", "name,foo,:eq,:sum,(,app,),:by"));

    Evaluator evaluator = newEvaluator("app", "www");
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", tags("app", "www", "name", "foo"), 6.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assertions.assertEquals(expected, payload);
  }

  @Test
  public void badExpression() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", "invalid expression,:foo"));

    Evaluator evaluator = newEvaluator("app", "www");
    evaluator.sync(subs);
    Assertions.assertEquals(0, evaluator.subscriptionCount());
  }

  @Test
  public void delayAggrCounterSum() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:sum"));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, counterData("foo", 1.0, 2.0, 3.0));

    Assertions.assertEquals(1, payload.getMetrics().size());
    Assertions.assertEquals(
        new EvalPayload.Metric("sum", tags(), 6.0),
        payload.getMetrics().get(0)
    );
  }

  @Test
  public void delayAggrGaugeSum() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:sum"));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    Assertions.assertEquals(3, payload.getMetrics().size());
    for (EvalPayload.Metric m : payload.getMetrics()) {
      Map<String, String> tags = m.getTags();
      Assertions.assertEquals(1, tags.size());
      Assertions.assertTrue(tags.containsKey("atlas.aggr"));
    }
  }


  @Test
  public void delayAggrGaugeCount() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:count"));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    Assertions.assertEquals(3, payload.getMetrics().size());
    for (EvalPayload.Metric m : payload.getMetrics()) {
      Map<String, String> tags = m.getTags();
      Assertions.assertEquals(1, tags.size());
      Assertions.assertTrue(tags.containsKey("atlas.aggr"));
      Assertions.assertEquals(1.0, m.getValue());
    }
  }

  @Test
  public void delayAggrGaugeGroupByCount() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:count,(,name,),:by"));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    Assertions.assertEquals(3, payload.getMetrics().size());
    for (EvalPayload.Metric m : payload.getMetrics()) {
      Map<String, String> tags = m.getTags();
      Assertions.assertEquals(2, tags.size());
      Assertions.assertTrue(tags.containsKey("atlas.aggr"));
      Assertions.assertEquals(1.0, m.getValue());
    }
  }

  @Test
  public void delayAggrGaugeMax() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("max", ":true,:max"));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, data("foo", 1.0, 2.0, 3.0));

    Assertions.assertEquals(1, payload.getMetrics().size());
    Assertions.assertEquals(
        new EvalPayload.Metric("max", tags(), 3.0),
        payload.getMetrics().get(0)
    );
  }

  @Test
  public void delayAggrCombinationSum() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(newSubscription("sum", ":true,:sum,(,name,),:by"));

    List<Measurement> ms = data("gauge", 1.0, 2.0, 3.0);
    ms.addAll(counterData("counter", 1.0, 2.0, 3.0));

    Evaluator evaluator = newEvaluator(true);
    evaluator.sync(subs);
    EvalPayload payload = evaluator.eval(0L, ms);

    Assertions.assertEquals(4, payload.getMetrics().size());
    int counterValues = 0;
    int gaugeValues = 0;
    for (EvalPayload.Metric m : payload.getMetrics()) {
      Map<String, String> tags = m.getTags();
      switch (tags.get("name")) {
        case "gauge":
          Assertions.assertTrue(tags.containsKey("atlas.aggr"));
          ++gaugeValues;
          break;
        case "counter":
          Assertions.assertFalse(tags.containsKey("atlas.aggr"));
          ++counterValues;
          break;
        default:
          Assertions.fail();
      }
    }
    Assertions.assertEquals(1, counterValues);
    Assertions.assertEquals(3, gaugeValues);
  }
}
