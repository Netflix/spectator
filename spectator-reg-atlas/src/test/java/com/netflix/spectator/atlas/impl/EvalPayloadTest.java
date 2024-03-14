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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class EvalPayloadTest {

  @Test
  public void metricEquals() {
    EqualsVerifier.forClass(EvalPayload.Metric.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void messageEquals() {
    EqualsVerifier.forClass(EvalPayload.Message.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void diagnosticMessageEquals() {
    EqualsVerifier.forClass(EvalPayload.DiagnosticMessage.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void evalPayloadEquals() {
    EqualsVerifier.forClass(EvalPayload.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  private List<EvalPayload.Metric> metrics(int n) {
    List<EvalPayload.Metric> ms = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      ms.add(new EvalPayload.Metric("_", Collections.emptyMap(), i));
    }
    return ms;
  }

  private List<EvalPayload.Message> messages(int n) {
    List<EvalPayload.Message> ms = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      ms.add(new EvalPayload.Message(
          "_",
          new EvalPayload.DiagnosticMessage(EvalPayload.MessageType.error, "" + i)
      ));
    }
    return ms;
  }

  @Test
  public void toBatchesBelowThreshold() {
    EvalPayload payload = new EvalPayload(0L, metrics(4));
    List<EvalPayload> batches = payload.consumeBatches(4);
    Assertions.assertEquals(1, batches.size());
    Assertions.assertSame(payload, batches.get(0));
  }

  @Test
  public void toBatchesAboveThreshold() {
    EvalPayload payload = new EvalPayload(0L, metrics(21));
    List<EvalPayload> batches = payload.consumeBatches(4);
    Assertions.assertEquals(6, batches.size());
    int i = 0;
    for (EvalPayload batch : batches) {
      for (EvalPayload.Metric metric : batch.getMetrics()) {
        int v = (int) metric.getValue();
        Assertions.assertEquals(i, v);
        ++i;
      }
    }
  }

  @Test
  public void toBatchesWithMessages() {
    EvalPayload payload = new EvalPayload(0L, metrics(21), messages(2));
    List<EvalPayload> batches = payload.consumeBatches(4);
    Assertions.assertEquals(6, batches.size());
    int i = 0;
    for (EvalPayload batch : batches) {
      Assertions.assertEquals(i == 0 ? 2 : 0, batch.getMessages().size());
      for (EvalPayload.Metric metric : batch.getMetrics()) {
        int v = (int) metric.getValue();
        Assertions.assertEquals(i, v);
        ++i;
      }
    }
  }
}
