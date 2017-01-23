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
package com.netflix.spectator.atlas;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;

import java.io.IOException;

/**
 * Jackson serializer for measurements. Values will be converted to a
 * valid set as they are written out by replacing invalid characters with
 * an '_'.
 */
public class MeasurementSerializer extends JsonSerializer<Measurement> {
  @Override
  public void serialize(
      Measurement value,
      JsonGenerator gen,
      SerializerProvider serializers) throws IOException, JsonProcessingException {
    gen.writeStartObject();
    gen.writeObjectFieldStart("tags");
    gen.writeStringField("name", ValidCharacters.toValidCharset(value.id().name()));
    boolean explicitDsType = false;
    for (Tag t : value.id().tags()) {
      if ("atlas.dstype".equals(t.key())) {
        explicitDsType = true;
      }
      final String k = ValidCharacters.toValidCharset(t.key());
      final String v = ValidCharacters.toValidCharset(t.value());
      gen.writeStringField(k, v);
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
