/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AsciiSet;

import java.util.function.Function;

/**
 * Helper functions for creating the mappers used to encode Atlas payloads.
 */
public final class JsonUtils {

  private JsonUtils() {
  }

  /**
   * Return a mapping function that will replace characters that are not matched by the
   * pattern with an underscore.
   */
  public static Function<String, String> createReplacementFunction(String pattern) {
    if (pattern == null) {
      return Function.identity();
    } else {
      AsciiSet set = AsciiSet.fromPattern(pattern);
      return s -> set.replaceNonMembers(s, '_');
    }
  }

  /** Create an object mapper with a custom serializer for measurements. */
  public static ObjectMapper createMapper(JsonFactory factory, Function<String, String> fixTag) {
    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer(fixTag));
    return new ObjectMapper(factory).registerModule(module);
  }
}
