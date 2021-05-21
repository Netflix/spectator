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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class IpcMetricTest {

  private Registry registry = null;

  @BeforeEach
  public void init() {
    registry = new DefaultRegistry();
  }

  @Test
  public void validateIdOk() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcStatus.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateAttemptFinalTrue() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.key(), "test")
        .withTag(IpcResult.success)
        .withTag(IpcStatus.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcAttemptFinal.is_true);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateAttemptFinalFalse() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.key(), "test")
        .withTag(IpcResult.success)
        .withTag(IpcStatus.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcAttemptFinal.is_false);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateIdBadName() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId("ipc.client-call")
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcStatus.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdMissingResult() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcStatus.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdErrorStatusOnSuccess() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcStatus.bad_request)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdErrorGroupOnFailure() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.failure)
        .withTag(IpcStatus.bad_request)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateIdSuccessStatusOnFailure() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.failure)
          .withTag(IpcStatus.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  public void validateIdStatusDetailOnSuccess() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcStatus.success)
        .withTag(IpcTagKey.statusDetail.tag("foo"))
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateIdBadResultValue() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcTagKey.result.tag("foo"))
          .withTag(IpcStatus.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdBadStatusValue() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcTagKey.status.tag("foo"))
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdBadAttemptFinalValue() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), "foo");
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateIdUnspecifiedDimension() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), "foo")
          .withTag(IpcTagKey.clientApp.key(), "app");
      IpcMetric.clientCall.validate(id, true);
    });
  }

  @Test
  public void validateRegistryOk() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcStatus.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    registry.timer(id).record(Duration.ofSeconds(42));
    IpcMetric.validate(registry, true);
  }

  @Test
  public void validateRegistryTimerWrongType() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
          .withTag(IpcTagKey.owner.tag("test"))
          .withTag(IpcResult.success)
          .withTag(IpcStatus.success)
          .withTag(IpcAttempt.initial)
          .withTag(IpcTagKey.attemptFinal.key(), true);
      registry.counter(id).increment();
      IpcMetric.validate(registry);
    });
  }

  @Test
  public void validateRegistryDistSummaryWrongType() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientInflight.metricName())
          .withTag(IpcTagKey.owner.tag("test"));
      registry.counter(id).increment();
      IpcMetric.validate(registry);
    });
  }

  @Test
  public void validateFailureInjectionOk() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
            .withTag(IpcTagKey.owner.tag("test"))
            .withTag(IpcResult.success)
            .withTag(IpcStatus.success)
            .withTag(IpcAttempt.initial)
            .withTag(IpcFailureInjection.none)
            .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id, true);
  }

  @Test
  public void validateFailureInjectionInvalid() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Id id = registry.createId(IpcMetric.clientCall.metricName())
              .withTag(IpcTagKey.owner.tag("test"))
              .withTag(IpcResult.success)
              .withTag(IpcStatus.success)
              .withTag(IpcAttempt.initial)
              .withTag(Tag.of(IpcTagKey.failureInjected.key(), "false"))
              .withTag(IpcTagKey.attemptFinal.key(), true);
      IpcMetric.clientCall.validate(id);
    });
  }
}
