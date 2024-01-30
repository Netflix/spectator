/*
 * Copyright 2014-2024 Netflix, Inc.
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
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Validation failure response from Atlas aggregator endpoint.
 */
@SuppressWarnings("PMD.DataClass")
final class ValidationResponse {

  private final String type;
  private final int errorCount;
  private final List<String> message; // Singular to match backend response

  ValidationResponse(String type, int errorCount, List<String> message) {
    this.type = type;
    this.errorCount = errorCount;
    this.message = message;
  }

  public String getType() {
    return type;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public List<String> getMessage() {
    return message;
  }

  String errorSummary() {
    return (message == null || message.isEmpty())
        ? "unknown cause"
        : String.join("; ", message);
  }

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  static ValidationResponse fromJson(byte[] json) throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(json)) {
      String type = null;
      int errorCount = 0;
      List<String> messages = new ArrayList<>();

      checkToken(parser.nextToken(), EnumSet.of(JsonToken.START_OBJECT));
      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        switch (parser.getText()) {
          case "type":
            type = parser.nextTextValue();
            break;
          case "errorCount":
            errorCount = parser.nextIntValue(0);
            break;
          case "message":
            JsonToken token = parser.nextToken();
            checkToken(token, EnumSet.of(JsonToken.VALUE_NULL, JsonToken.START_ARRAY));
            if (token == JsonToken.START_ARRAY) {
              while (parser.nextToken() != JsonToken.END_ARRAY) {
                messages.add(parser.getText());
              }
            }
            break;
          default:
            parser.nextToken();
            parser.skipChildren();
            break;
        }
      }

      return new ValidationResponse(type, errorCount, messages);
    }
  }

  private static void checkToken(JsonToken actual, EnumSet<JsonToken> expected) throws IOException {
    if (!expected.contains(actual)) {
      throw new JsonParseException("expected " + expected + ", but found " + actual);
    }
  }
}
