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

/**
 * Helper functions for creating the mappers used to encode Atlas payloads.
 */
public final class JsonUtils {

  private JsonUtils() {
  }

  /** Create an object mapper with a custom serializer for measurements. */
  public static ObjectMapper createMapper(JsonFactory factory) {
    SimpleModule module = new SimpleModule()
        .addSerializer(Measurement.class, new MeasurementSerializer());
    return new ObjectMapper(factory).registerModule(module);
  }
}
