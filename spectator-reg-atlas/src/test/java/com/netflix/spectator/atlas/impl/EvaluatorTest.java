/*
 * Copyright 2014-2016 Netflix, Inc.
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
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RunWith(JUnit4.class)
public class EvaluatorTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);

  private List<TagsValuePair> data(String name, double... vs) {
    List<Measurement> ms = new ArrayList<>();
    for (int i = 0; i < vs.length; ++i) {
      String pos = String.format("%03d", i);
      String value = String.format("%f", vs[i]);
      ms.add(new Measurement(registry.createId(name, "i", pos, "v", value), 0L, vs[i]));
    }
    return ms.stream().map(this::newTagsValuePair).collect(Collectors.toList());
  }

  private TagsValuePair newTagsValuePair(Measurement m) {
    Map<String, String> tags = new HashMap<>();
    for (Tag t : m.id().tags()) {
      tags.put(t.key(), t.value());
    }
    tags.put("name", m.id().name());
    return new TagsValuePair(tags, m.value());
  }

  @Test
  public void noSubsForGroup() {
    Evaluator evaluator = new Evaluator();
    EvalPayload payload = evaluator.eval("test", 0L, Collections.emptyList());
    EvalPayload expected = new EvalPayload(0L, Collections.emptyList());
    Assert.assertEquals(expected, payload);
  }

  @Test
  public void sumAndMaxForGroup() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(new Subscription().withId("sum").withExpression(":true,:sum"));
    subs.add(new Subscription().withId("max").withExpression(":true,:max"));

    Evaluator evaluator = new Evaluator().addGroupSubscriptions("local", subs);
    EvalPayload payload = evaluator.eval("local", 0L, data("foo", 1.0, 2.0, 3.0));

    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 6.0));
    metrics.add(new EvalPayload.Metric("max", Collections.emptyMap(), 3.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assert.assertEquals(expected, payload);
  }

  @Test
  public void removeSub() {
    List<Subscription> subs = new ArrayList<>();
    subs.add(new Subscription().withId("sum").withExpression(":true,:sum"));
    subs.add(new Subscription().withId("max").withExpression(":true,:max"));
    Evaluator evaluator = new Evaluator().addGroupSubscriptions("local", subs);

    evaluator.removeGroupSubscriptions("local");
    EvalPayload payload = evaluator.eval("test", 0L, Collections.emptyList());

    EvalPayload expected = new EvalPayload(0L, Collections.emptyList());
    Assert.assertEquals(expected, payload);
  }

  @Test
  public void updateSub() {

    // Eval with sum
    List<Subscription> sumSub = new ArrayList<>();
    sumSub.add(new Subscription().withId("sum").withExpression(":true,:sum"));
    Evaluator evaluator = new Evaluator().addGroupSubscriptions("local", sumSub);
    EvalPayload payload = evaluator.eval("local", 0L, data("foo", 1.0, 2.0, 3.0));
    List<EvalPayload.Metric> metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 6.0));
    EvalPayload expected = new EvalPayload(0L, metrics);
    Assert.assertEquals(expected, payload);

    // Update to use max instead
    List<Subscription> maxSub = new ArrayList<>();
    maxSub.add(new Subscription().withId("sum").withExpression(":true,:max"));
    evaluator.addGroupSubscriptions("local", maxSub);
    payload = evaluator.eval("local", 0L, data("foo", 1.0, 2.0, 3.0));
    metrics = new ArrayList<>();
    metrics.add(new EvalPayload.Metric("sum", Collections.emptyMap(), 3.0));
    expected = new EvalPayload(0L, metrics);
    Assert.assertEquals(expected, payload);
  }
}
