/*
 * Copyright 2014-2018 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;

@RunWith(JUnit4.class)
public class IpcMetricTest {

  private Registry registry = null;

  @Before
  public void init() {
    registry = new DefaultRegistry();
  }

  @Test
  public void validateIdOk() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdBadName() {
    Id id = registry.createId("ipc.client-call")
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdMissingResult() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdErrorGroupOnSuccess() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcErrorGroup.general)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test
  public void validateIdErrorGroupOnFailure() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.failure)
        .withTag(IpcErrorGroup.general)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdErrorGroupNotOnFailure() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.failure)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdBadResultValue() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcTagKey.result.tag("foo"))
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    IpcMetric.clientCall.validate(id);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateIdBadAttemptFinalValue() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), "foo");
    IpcMetric.clientCall.validate(id);
  }

  @Test
  public void validateRegistryOk() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    registry.timer(id).record(Duration.ofSeconds(42));
    IpcMetric.validate(registry);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRegistryTimerWrongType() {
    Id id = registry.createId(IpcMetric.clientCall.metricName())
        .withTag(IpcTagKey.owner.tag("test"))
        .withTag(IpcResult.success)
        .withTag(IpcAttempt.initial)
        .withTag(IpcTagKey.attemptFinal.key(), true);
    registry.counter(id).increment();
    IpcMetric.validate(registry);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateRegistryDistSummaryWrongType() {
    Id id = registry.createId(IpcMetric.clientInflight.metricName())
        .withTag(IpcTagKey.owner.tag("test"));
    registry.counter(id).increment();
    IpcMetric.validate(registry);
  }
}
