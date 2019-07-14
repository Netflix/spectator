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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AsciiSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;


public class MeasurementSerializerTest {

  private final AsciiSet set = AsciiSet.fromPattern("-._A-Za-z0-9");
  private final Map<String, AsciiSet> overrides =
      Collections.singletonMap("cluster", AsciiSet.fromPattern("-._A-Za-z0-9^~"));

  private final DefaultRegistry registry = new DefaultRegistry();
  private final SimpleModule module = new SimpleModule()
      .addSerializer(Measurement.class, new MeasurementSerializer(set, overrides));
  private final ObjectMapper mapper = new ObjectMapper().registerModule(module);

  @Test
  public void encode() throws Exception {
    Id id = registry.createId("foo", "bar", "baz");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"bar\":\"baz\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void explicitDsType() throws Exception {
    Id id = registry.createId("foo", "atlas.dstype", "rate");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"atlas.dstype\":\"rate\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void invalidName() throws Exception {
    Id id = registry.createId("f@%", "bar", "baz");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"f__\",\"bar\":\"baz\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void userTagName() throws Exception {
    Id id = registry.createId("foo", "name", "bar");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void invalidKey() throws Exception {
    Id id = registry.createId("foo", "b$$", "baz");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"b__\":\"baz\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void invalidValue() throws Exception {
    Id id = registry.createId("foo", "bar", "b&*");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"bar\":\"b__\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void valueCharsetOverrides() throws Exception {
    Id id = registry.createId("foo", "bar", "abc^~def", "cluster", "abc^~def");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"bar\":\"abc__def\",\"cluster\":\"abc^~def\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void publishPayloadEmpty() throws Exception {
    PublishPayload p = new PublishPayload(Collections.emptyMap(), Collections.emptyList());
    String json = mapper.writeValueAsString(p);
    String expected = "{\"tags\":{},\"metrics\":[]}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void publishPayloadNoCommonTags() throws Exception {
    Id id = registry.createId("foo");
    Measurement m = new Measurement(id, 42L, 3.0);
    PublishPayload p = new PublishPayload(Collections.emptyMap(), Collections.singletonList(m));
    String json = mapper.writeValueAsString(p);
    String tags = "{\"name\":\"foo\",\"atlas.dstype\":\"gauge\"}";
    String mjson = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    String expected = "{\"tags\":{},\"metrics\":[" + mjson + "]}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void publishPayloadWithCommonTags() throws Exception {
    Id id = registry.createId("foo");
    Measurement m = new Measurement(id, 42L, 3.0);
    PublishPayload p = new PublishPayload(Collections.singletonMap("a", "b"), Collections.singletonList(m));
    String json = mapper.writeValueAsString(p);
    String tags = "{\"name\":\"foo\",\"atlas.dstype\":\"gauge\"}";
    String mjson = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    String expected = "{\"tags\":{\"a\":\"b\"},\"metrics\":[" + mjson + "]}";
    Assertions.assertEquals(expected, json);
  }
}
