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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.AsciiSet;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson serializer for measurements. Values will be converted to a
 * valid set as they are written out by replacing invalid characters with
 * an '_'.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public class MeasurementSerializer extends JsonSerializer<Measurement> {

  private final AsciiSet set;
  private final Map<String, AsciiSet> overrides;

  /**
   * Create a new instance of the serializer.
   *
   * @param set
   *     The set of characters that are allowed to be used for tag keys.
   * @param overrides
   *     Overrides for the set of characters allowed to be used for tag values.
   */
  public MeasurementSerializer(AsciiSet set, Map<String, AsciiSet> overrides) {
    super();
    this.set = set;
    this.overrides = overrides;
  }

  private String fixKey(String k) {
    return set.replaceNonMembers(k, '_');
  }

  private String fixValue(String k, String v) {
    AsciiSet s = overrides.getOrDefault(k, set);
    return s.replaceNonMembers(v, '_');
  }

  @Override
  public void serialize(
      Measurement value,
      JsonGenerator gen,
      SerializerProvider serializers) throws IOException {
    gen.writeStartObject();
    gen.writeObjectFieldStart("tags");
    gen.writeStringField("name", fixValue("name", value.id().name()));
    boolean explicitDsType = false;
    for (Tag t : value.id().tags()) {
      if (!"name".equals(t.key())) {
        if ("atlas.dstype".equals(t.key())) {
          explicitDsType = true;
        }
        final String k = fixKey(t.key());
        final String v = fixValue(k, t.value());
        gen.writeStringField(k, v);
      }
    }

    // If the dstype has not been explicitly set, then the value must be coming in
    // as a gauge. Go ahead and explicitly mark it as such because the backend will
    // default to a rate.
    if (!explicitDsType) {
      gen.writeStringField("atlas.dstype", "gauge");
    }
    gen.writeEndObject();
    gen.writeNumberField("timestamp", value.timestamp());
    gen.writeNumberField("value", value.value());
    gen.writeEndObject();
  }
}
