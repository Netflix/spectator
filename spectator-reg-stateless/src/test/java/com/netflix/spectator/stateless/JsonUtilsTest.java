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
package com.netflix.spectator.stateless;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JsonUtilsTest {

  private static final JsonFactory FACTORY = new JsonFactory();

  private final Registry registry = new DefaultRegistry();

  private Measurement unknown(double delta, String name, String... tags) {
    Id id = registry.createId(name).withTags(tags);
    return new Measurement(id, 0L, delta);
  }

  private Measurement count(double delta, String name, String... tags) {
    Id id = registry.createId(name).withTag(Statistic.count).withTags(tags);
    return new Measurement(id, 0L, delta);
  }

  private Measurement max(double delta, String name, String... tags) {
    Id id = registry.createId(name).withTag(Statistic.max).withTags(tags);
    return new Measurement(id, 0L, delta);
  }

  @Test
  public void encodeNoCommonTags() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(count(42.0, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(1, values.size());
    ms.forEach(m -> Assertions.assertEquals(42.0, values.get(m.id()).value, 1e-12));
  }

  @Test
  public void encodeCommonTags() throws Exception {
    Map<String, String> commonTags = new HashMap<>();
    commonTags.put("a", "1");
    commonTags.put("b", "2");
    List<Measurement> ms = new ArrayList<>();
    ms.add(count(42.0, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(commonTags, ms));
    Assertions.assertEquals(1, values.size());
    ms.forEach(m -> {
      Id id = m.id().withTags(commonTags);
      Assertions.assertEquals(42.0, values.get(id).value, 1e-12);
    });
  }

  @Test
  public void encodeIgnoresNaN() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(count(Double.NaN, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(0, values.size());
  }

  @Test
  public void encodeIgnoresAdd0() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(count(0, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(0, values.size());
  }

  @Test
  public void encodeSendsMax0() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(max(0, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(1, values.size());
    ms.forEach(m -> {
      Id id = m.id();
      Assertions.assertEquals(0.0, values.get(id).value, 1e-12);
    });
  }

  @Test
  public void encodeInvalidStatisticAsGauge() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(count(42, "test", "statistic", "foo"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(1, values.size());
    ms.forEach(m -> {
      Id id = m.id();
      Assertions.assertEquals(values.get(id).op, 10);
    });
  }

  @Test
  public void encodeAssumesNoStatisticIsGauge() throws Exception {
    List<Measurement> ms = new ArrayList<>();
    ms.add(unknown(42, "test"));
    Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
    Assertions.assertEquals(1, values.size());
    ms.forEach(m -> {
      Id id = m.id();
      Assertions.assertEquals(values.get(id).op, 10);
    });
  }

  @Test
  public void encodeSupportsKnownStats() throws Exception {
    for (Statistic stat : Statistic.values()) {
      List<Measurement> ms = new ArrayList<>();
      ms.add(count(42, "test", "statistic", stat.value()));
      Map<Id, Delta> values = decode(JsonUtils.encode(Collections.emptyMap(), ms));
      Assertions.assertEquals(1, values.size());
      ms.forEach(m -> {
        Id id = m.id();
        Assertions.assertEquals(42.0, values.get(id).value, 1e-12);
      });
    }
  }



  private Map<Id, Delta> decode(byte[] json) throws IOException {
    Map<Id, Delta> values = new HashMap<>();
    JsonParser parser = FACTORY.createParser(json);

    // Array start
    Assertions.assertEquals(JsonToken.START_ARRAY, parser.nextToken());

    // String table
    String[] strings = new String[parser.nextIntValue(-1)];
    for (int i = 0; i < strings.length; ++i) {
      strings[i] = parser.nextTextValue();
    }

    // Read measurements
    parser.nextToken();
    while (parser.currentToken() != JsonToken.END_ARRAY) {
      int n = parser.getIntValue();
      Map<String, String> tags = new HashMap<>(n);
      for (int i = 0; i < n; ++i) {
        String k = strings[parser.nextIntValue(-1)];
        String v = strings[parser.nextIntValue(-1)];
        tags.put(k, v);
      }
      String name = tags.get("name");
      tags.remove("name");
      Id id = registry.createId(name).withTags(tags);
      int op = parser.nextIntValue(-1);
      parser.nextToken();
      double value = parser.getDoubleValue();
      values.put(id, new Delta(op, value));

      parser.nextToken();
    }

    return values;
  }

  private static class Delta {
    final int op;
    final double value;

    Delta(int op, double value) {
      this.op = op;
      this.value = value;
    }

    @Override public String toString() {
      return (op == 0 ? "add(" : "max(") + value + ")";
    }
  }
}
