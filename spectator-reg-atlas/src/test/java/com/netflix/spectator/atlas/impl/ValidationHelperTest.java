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
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.ipc.http.HttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class ValidationHelperTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidationHelperTest.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private void check(Registry r, long sent, long http, long invalid, long other) {
    Id baseId = r.createId("spectator.measurements");
    Id droppedId = baseId.withTag("id", "dropped");

    Assertions.assertEquals(sent, r.counter(baseId.withTag("id", "sent")).count());
    Assertions.assertEquals(http, r.counter(droppedId.withTag("error", "http-error")).count());
    Assertions.assertEquals(invalid, r.counter(droppedId.withTag("error", "validation")).count());
    Assertions.assertEquals(other, r.counter(droppedId.withTag("error", "other")).count());
  }

  private HttpResponse httpResponse(int status, ValidationResponse vres) throws IOException {
    String json = MAPPER.writeValueAsString(vres);
    return new HttpResponse(status, Collections.emptyMap(), json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void incrementDroppedHttp() {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    helper.incrementDroppedHttp(42);
    check(registry, 0, 42, 0, 0);
  }

  @Test
  public void ok() {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    helper.recordResults(42, new HttpResponse(200, Collections.emptyMap()));
    check(registry, 42, 0, 0, 0);
  }

  @Test
  public void validationErrorPartial() throws IOException {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    ValidationResponse vres = new ValidationResponse();
    vres.setType("error");
    vres.setErrorCount(3);
    vres.setMessage(Collections.singletonList("foo"));
    helper.recordResults(42, httpResponse(202, vres));
    check(registry, 39, 0, 3, 0);
  }

  @Test
  public void validationErrorAll() throws IOException {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    ValidationResponse vres = new ValidationResponse();
    vres.setType("error");
    vres.setErrorCount(42);
    vres.setMessage(Collections.singletonList("foo"));
    helper.recordResults(42, httpResponse(400, vres));
    check(registry, 0, 0, 42, 0);
  }

  @Test
  public void validationErrorNullMessages() throws IOException {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    ValidationResponse vres = new ValidationResponse();
    vres.setType("error");
    vres.setErrorCount(42);
    helper.recordResults(42, httpResponse(400, vres));
    check(registry, 0, 0, 42, 0);
  }

  @Test
  public void validationErrorEmptyMessages() throws IOException {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    ValidationResponse vres = new ValidationResponse();
    vres.setType("error");
    vres.setErrorCount(42);
    vres.setMessage(Collections.emptyList());
    helper.recordResults(42, httpResponse(400, vres));
    check(registry, 0, 0, 42, 0);
  }

  @Test
  public void validationErrorBadJson() throws IOException {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    HttpResponse res = new HttpResponse(400, Collections.emptyMap());
    helper.recordResults(42, res);
    check(registry, 0, 0, 0, 42);
  }

  @Test
  public void serverError() {
    Registry registry = new DefaultRegistry();
    ValidationHelper helper = new ValidationHelper(LOGGER, MAPPER, registry);
    helper.recordResults(42, new HttpResponse(500, Collections.emptyMap()));
    check(registry, 0, 42, 0, 0);
  }
}
