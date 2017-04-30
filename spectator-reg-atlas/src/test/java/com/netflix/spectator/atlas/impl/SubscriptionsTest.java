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
package com.netflix.spectator.atlas.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@RunWith(JUnit4.class)
public class SubscriptionsTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(Subscriptions.class)
        .suppress(Warning.NULL_FIELDS, Warning.NONFINAL_FIELDS)
        .verify();
  }

  private Map<Subscription, Long> map(long ttl, Subscription... subs) {
    Map<Subscription, Long> m = new HashMap<>();
    for (Subscription sub : subs) {
      m.put(sub, ttl);
    }
    return m;
  }

  private Subscription newSub(String id, String expr, long freq) {
    return new Subscription().withId(id).withExpression(expr).withFrequency(freq);
  }

  private Subscriptions newSubs(Subscription... subs) {
    return new Subscriptions().withExpressions(Arrays.asList(subs));
  }

  @Test
  public void updateInit() {
    Map<Subscription, Long> subs = new HashMap<>();
    Subscription a = newSub("a", ":true,:sum", 10L);
    Subscription b = newSub("b", ":true,:sum", 60L);
    newSubs(a, b).update(subs, 0L, 15L);

    Assert.assertEquals(map(15L, a, b), subs);
  }

  @Test
  public void updateComplete() {
    Subscription a = newSub("a", ":true,:sum", 10L);
    Subscription b = newSub("b", ":true,:sum", 60L);
    Map<Subscription, Long> subs = map(15L, a, b);
    newSubs(a, b).update(subs, 10L, 30L);

    Assert.assertEquals(map(30L, a, b), subs);
  }

  @Test
  public void updatePartial() {
    Subscription a = newSub("a", ":true,:sum", 10L);
    Subscription b = newSub("b", ":true,:sum", 60L);
    Map<Subscription, Long> subs = map(15L, a, b);
    newSubs(b).update(subs, 10L, 30L);

    Map<Subscription, Long> expected = map(15L, a, b);
    expected.put(b, 30L);
    Assert.assertEquals(expected, subs);
  }

  @Test
  public void updatePartialExpire() {
    Subscription a = newSub("a", ":true,:sum", 10L);
    Subscription b = newSub("b", ":true,:sum", 60L);
    Map<Subscription, Long> subs = map(15L, a, b);
    newSubs(b).update(subs, 16L, 30L);

    Map<Subscription, Long> expected = map(30L, b);
    Assert.assertEquals(expected, subs);
  }
}
