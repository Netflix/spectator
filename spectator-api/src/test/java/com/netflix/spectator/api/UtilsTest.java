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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class UtilsTest {

  private List<Measurement> newList(int size) {
    Registry r = new DefaultRegistry();
    List<Measurement> data = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      data.add(new Measurement(r.createId("foo", "i", "" + i), 0L, i));
    }
    return data;
  }

  @Test
  public void getTagValueIdNoTags() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("foo");
    Assertions.assertEquals(null, Utils.getTagValue(id, "abc"));
  }

  @Test
  public void getTagValueId() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("foo", "bar", "baz", "abc", "def");
    Assertions.assertEquals("def", Utils.getTagValue(id, "abc"));
    Assertions.assertEquals("baz", Utils.getTagValue(id, "bar"));
  }

  @Test
  public void firstTag() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    Measurement m = Utils.first(ms, t);
    Assertions.assertEquals(m.id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void firstKV() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    Measurement m = Utils.first(ms, "i", "7");
    Assertions.assertEquals(m.id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void firstPredicate() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    Measurement m = Utils.first(ms, v -> v.value() == 7.0);
    Assertions.assertEquals(m.id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void firstPredicateEmpty() {
    List<Measurement> ms = newList(10);
    Measurement m = Utils.first(ms, v -> false);
    Assertions.assertEquals(null, m);
  }

  @Test
  public void filterTag() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    List<Measurement> out = Utils.toList(Utils.filter(ms, t));
    Assertions.assertEquals(1, out.size());
    Assertions.assertEquals(out.get(0).id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void filterKV() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    List<Measurement> out = Utils.toList(Utils.filter(ms, "i", "7"));
    Assertions.assertEquals(1, out.size());
    Assertions.assertEquals(out.get(0).id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void filterPredicate() {
    List<Measurement> ms = newList(10);
    Tag t = new BasicTag("i", "7");
    List<Measurement> out = Utils.toList(Utils.filter(ms, v -> v.value() == 7.0));
    Assertions.assertEquals(1, out.size());
    Assertions.assertEquals(out.get(0).id().tags(), ArrayTagSet.create(t));
  }

  @Test
  public void filterPredicateEmpty() {
    List<Measurement> ms = newList(10);
    List<Measurement> out = Utils.toList(Utils.filter(ms, v -> false));
    Assertions.assertEquals(0, out.size());
  }
}
