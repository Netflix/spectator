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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;


public class MeasurementSerializerTest {

  private final DefaultRegistry registry = new DefaultRegistry();
  private final SimpleModule module = new SimpleModule()
      .addSerializer(Measurement.class, new MeasurementSerializer());
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
  public void nameWrittenVerbatim() throws Exception {
    // The serializer no longer fixes invalid characters; that is handled when the meter is
    // created. Whatever id reaches the serializer is written out as is.
    Id id = registry.createId("f@%", "bar", "baz");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"f@%\",\"bar\":\"baz\",\"atlas.dstype\":\"gauge\"}";
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
  public void keyWrittenVerbatim() throws Exception {
    Id id = registry.createId("foo", "b$$", "baz");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"b$$\":\"baz\",\"atlas.dstype\":\"gauge\"}";
    String expected = "{\"tags\":" + tags + ",\"timestamp\":42,\"value\":3.0}";
    Assertions.assertEquals(expected, json);
  }

  @Test
  public void valueWrittenVerbatim() throws Exception {
    Id id = registry.createId("foo", "bar", "b&*");
    Measurement m = new Measurement(id, 42L, 3.0);
    String json = mapper.writeValueAsString(m);
    String tags = "{\"name\":\"foo\",\"bar\":\"b&*\",\"atlas.dstype\":\"gauge\"}";
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
