/*
 * Copyright 2014-2018 Netflix, Inc.
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
import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper for encoding measurements into a JSON array that can be read by the aggregator
 * service. For more information see:
 *
 * https://github.com/Netflix-Skunkworks/iep-apps/tree/master/atlas-aggregator
 */
final class JsonUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

  private static final JsonFactory FACTORY = new JsonFactory();

  private static final int UNKNOWN = -1;
  private static final int ADD = 0;
  private static final int MAX = 10;

  private JsonUtils() {
  }

  /** Encode the measurements to a JSON payload that can be sent to the aggregator. */
  static byte[] encode(
      Map<String, String> commonTags,
      List<Measurement> measurements) throws IOException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JsonGenerator gen = FACTORY.createGenerator(baos);
    gen.writeStartArray();
    Map<String, Integer> strings = buildStringTable(gen, commonTags, measurements);
    for (Measurement m : measurements) {
      appendMeasurement(gen, strings, commonTags, m.id(), m.value());
    }
    gen.writeEndArray();
    gen.close();
    return baos.toByteArray();
  }

  private static Map<String, Integer> buildStringTable(
      JsonGenerator gen,
      Map<String, String> commonTags,
      List<Measurement> measurements) throws IOException {
    Map<String, Integer> strings = new HashMap<>();

    strings.put("name", 0);
    commonTags.forEach((k, v) -> {
      strings.put(k, 0);
      strings.put(v, 0);
    });

    for (Measurement m : measurements) {
      Id id = m.id();
      strings.put(id.name(), 0);
      for (Tag t : id.tags()) {
        strings.put(t.key(), 0);
        strings.put(t.value(), 0);
      }
    }

    String[] sorted = strings.keySet().toArray(new String[0]);
    Arrays.sort(sorted);

    gen.writeNumber(sorted.length);
    for (int i = 0; i < sorted.length; ++i) {
      gen.writeString(sorted[i]);
      strings.put(sorted[i], i);
    }

    return strings;
  }

  private static void appendMeasurement(
      JsonGenerator gen,
      Map<String, Integer> strings,
      Map<String, String> commonTags,
      Id id,
      double value) throws IOException {

    int op = operation(id);
    if (shouldSend(op, value)) {
      // Number of tag entries, commonTags + name + tags
      int n = commonTags.size() + 1 + Utils.size(id.tags());
      gen.writeNumber(n);

      // Write out the key/value pairs for the tags
      for (Map.Entry<String, String> entry : commonTags.entrySet()) {
        gen.writeNumber(strings.get(entry.getKey()));
        gen.writeNumber(strings.get(entry.getValue()));
      }
      for (Tag t : id.tags()) {
        gen.writeNumber(strings.get(t.key()));
        gen.writeNumber(strings.get(t.value()));
      }
      gen.writeNumber(strings.get("name"));
      gen.writeNumber(strings.get(id.name()));

      // Write out the operation and delta value
      gen.writeNumber(op);
      gen.writeNumber(value);
    }
  }

  private static int operation(Id id) {
    for (Tag t : id.tags()) {
      if ("statistic".equals(t.key())) {
        return operation(t.value());
      }
    }
    LOGGER.warn("invalid statistic for {}, value will be dropped", id);
    return UNKNOWN;
  }

  private static int operation(String stat) {
    int op;
    switch (stat) {
      case "count":          op = ADD; break;
      case "totalAmount":    op = ADD; break;
      case "totalTime":      op = ADD; break;
      case "totalOfSquares": op = ADD; break;
      case "percentile":     op = ADD; break;
      case "max":            op = MAX; break;
      case "gauge":          op = MAX; break;
      case "activeTasks":    op = MAX; break;
      case "duration":       op = MAX; break;
      default:               op = UNKNOWN; break;
    }
    return op;
  }

  private static boolean shouldSend(int op, double value) {
    return op != UNKNOWN && !Double.isNaN(value) && (value > 0.0 || op == MAX);
  }
}
