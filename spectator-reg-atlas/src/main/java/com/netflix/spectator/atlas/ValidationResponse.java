/*
 * Copyright 2014-2020 Netflix, Inc.
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

import java.util.List;

/**
 * Validation failure response from Atlas publish endpoint.
 */
@SuppressWarnings("PMD.DataClass")
final class ValidationResponse {

  private String type;
  private int errorCount;
  private List<String> message; // singular to match server response

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(int errorCount) {
    this.errorCount = errorCount;
  }

  public List<String> getMessage() {
    return message;
  }

  public void setMessage(List<String> message) {
    this.message = message;
  }

  String errorSummary() {
    return (message == null || message.isEmpty())
        ? "unknown cause"
        : String.join("; ", message);
  }
}
