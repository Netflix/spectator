/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.tdigest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.netflix.spectator.api.DefaultId;
import com.netflix.spectator.api.Tag;
import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.TDigest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for encoding and decoding digest measurements.
 */
final class Json {

  private static final SmileFactory FACTORY = new SmileFactory();

  static {
    FACTORY
        .enable(SmileGenerator.Feature.WRITE_HEADER)
        .disable(SmileGenerator.Feature.WRITE_END_MARKER)
        .enable(SmileGenerator.Feature.CHECK_SHARED_NAMES)
        .enable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
        .disable(SmileParser.Feature.REQUIRE_HEADER);
  }

  private Json() {
  }

  /** Create a new generator for the output stream. */
  static JsonGenerator newGenerator(OutputStream out) throws IOException {
    return FACTORY.createGenerator(out);
  }

  /** Encode the measurement using the generator. */
  static void encode(TDigestMeasurement m, JsonGenerator gen) throws IOException {
    TDigest digest = m.value();
    digest.compress();
    ByteBuffer buf = ByteBuffer.allocate(digest.byteSize());
    digest.asBytes(buf);

    gen.writeStartArray();
    gen.writeStartObject();
    gen.writeStringField("name", m.id().name());
    for (Tag t : m.id().tags()) {
      gen.writeStringField(t.key(), t.value());
    }
    gen.writeEndObject();
    gen.writeNumber(m.timestamp());
    gen.writeBinary(buf.array());
    gen.writeEndArray();
  }

  /** Encode the measurements using the generator. */
  static void encode(List<TDigestMeasurement> ms, JsonGenerator gen) throws IOException {
    gen.writeStartArray();
    for (TDigestMeasurement m : ms) {
      encode(m, gen);
    }
    gen.writeEndArray();
  }

  /** Return a byte-array with the encoded measurements. */
  static byte[] encode(List<TDigestMeasurement> ms) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (JsonGenerator gen = FACTORY.createGenerator(baos)) {
      encode(ms, gen);
    }
    return baos.toByteArray();
  }

  private static void require(boolean condition, String msg) {
    if (!condition) {
      throw new IllegalArgumentException(msg);
    }
  }

  private static void expect(JsonParser parser, JsonToken expected) throws IOException {
    JsonToken t = parser.nextToken();
    if (t != expected) {
      String msg = String.format("expected %s, but found %s", expected, t);
      throw new IllegalArgumentException(msg);
    }
  }

  private static TDigestMeasurement decode(JsonParser parser) throws IOException {
    expect(parser, JsonToken.START_OBJECT);
    require("name".equals(parser.nextFieldName()), "expected name");
    DefaultId id = new DefaultId(parser.nextTextValue());
    while (parser.nextToken() == JsonToken.FIELD_NAME) {
      id = id.withTag(parser.getText(), parser.nextTextValue());
    }
    long t = parser.nextLongValue(-1L);
    expect(parser, JsonToken.VALUE_EMBEDDED_OBJECT);
    TDigest v = AVLTreeDigest.fromBytes(ByteBuffer.wrap(parser.getBinaryValue()));
    expect(parser, JsonToken.END_ARRAY);
    return new TDigestMeasurement(id, t, v);
  }

  /** Decode a list of measurements from a byte array. */
  static List<TDigestMeasurement> decode(byte[] data) throws IOException {
    return decode(data, 0, data.length);
  }

  /** Decode a list of measurements from a range of a byte array. */
  static List<TDigestMeasurement> decode(byte[] data, int offset, int length) throws IOException {
    JsonParser parser = FACTORY.createParser(data, offset, length);
    List<TDigestMeasurement> ms = new ArrayList<>();
    expect(parser, JsonToken.START_ARRAY);
    while (parser.nextToken() == JsonToken.START_ARRAY) {
      ms.add(decode(parser));
    }
    return ms;
  }
}
