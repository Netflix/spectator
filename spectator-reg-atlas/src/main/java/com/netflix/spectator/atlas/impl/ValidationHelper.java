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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.ipc.http.HttpResponse;
import org.slf4j.Logger;

final class ValidationHelper {

  private final Logger logger;
  private final ObjectMapper jsonMapper;

  private final Counter measurementsSent;
  private final Counter measurementsDroppedInvalid;
  private final Counter measurementsDroppedHttp;
  private final Counter measurementsDroppedOther;

  ValidationHelper(Logger logger, ObjectMapper jsonMapper, Registry registry) {
    this.logger = logger;
    this.jsonMapper = jsonMapper;

    Id baseId = registry.createId("spectator.measurements");
    Id droppedId = baseId.withTag("id", "dropped");
    this.measurementsSent = registry.counter(baseId.withTag("id", "sent"));
    this.measurementsDroppedHttp = registry.counter(droppedId.withTag("error", "http-error"));
    this.measurementsDroppedInvalid = registry.counter(droppedId.withTag("error", "validation"));
    this.measurementsDroppedOther = registry.counter(droppedId.withTag("error", "other"));
  }

  void incrementDroppedHttp(int amount) {
    measurementsDroppedHttp.increment(amount);
  }

  /**
   * Report metrics and do basic logging of validation results to help the user with
   * debugging.
   */
  void recordResults(int numMeasurements, HttpResponse res) {
    if (res.status() == 200) {
      measurementsSent.increment(numMeasurements);
    } else if (res.status() < 500) {
      // For validation:
      // 202 - partial failure
      // 400 - all failed, could also be some other sort of failure
      try {
        ValidationResponse vres = jsonMapper.readValue(res.entity(), ValidationResponse.class);
        measurementsDroppedInvalid.increment(vres.getErrorCount());
        measurementsSent.increment(numMeasurements - vres.getErrorCount());
        logger.warn("{} measurement(s) dropped due to validation errors: {}",
            vres.getErrorCount(), vres.errorSummary());
      } catch (Exception e) {
        // Likely some other 400 error. Log at trace level in case the cause is really needed.
        logger.trace("failed to parse response", e);
        logger.warn("{} measurement(s) dropped. Http status: {}", numMeasurements, res.status());
        measurementsDroppedOther.increment(numMeasurements);
      }
    } else {
      // Some sort of server side failure
      measurementsDroppedHttp.increment(numMeasurements);
    }
  }
}
