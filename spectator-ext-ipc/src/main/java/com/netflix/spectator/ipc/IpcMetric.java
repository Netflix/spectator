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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * IPC metric names and associated metadata.
 */
public enum IpcMetric {
  /**
   * Timer recording the number and latency of outbound requests.
   */
  clientCall("ipc.client.call", EnumSet.of(
      IpcTagKey.owner,
      IpcTagKey.result,
      IpcTagKey.status,
      IpcTagKey.attempt,
      IpcTagKey.attemptFinal
  )),

  /**
   * Timer recording the number and latency of inbound requests.
   */
  serverCall("ipc.server.call", EnumSet.of(
      IpcTagKey.owner,
      IpcTagKey.result,
      IpcTagKey.status
  )),

  /**
   * Number of outbound requests that are currently in flight.
   */
  clientInflight("ipc.client.inflight", EnumSet.of(
      IpcTagKey.owner
  )),

  /**
   * Number of inbound requests that are currently in flight.
   */
  serverInflight("ipc.server.inflight", EnumSet.of(
      IpcTagKey.owner
  ));

  private final String metricName;
  private final EnumSet<IpcTagKey> requiredDimensions;

  /** Create a new instance. */
  IpcMetric(String metricName, EnumSet<IpcTagKey> requiredDimensions) {
    this.metricName = metricName;
    this.requiredDimensions = requiredDimensions;
  }

  /** Returns the metric name to use in the meter id. */
  public String metricName() {
    return metricName;
  }

  /** Returns the set of dimensions that are required for this metrics. */
  public EnumSet<IpcTagKey> requiredDimensions() {
    return requiredDimensions;
  }

  private static final Class<?>[] METER_TYPES = {
      Counter.class,
      Timer.class,
      DistributionSummary.class,
      Gauge.class
  };

  private static final SortedSet<String> ATTEMPT_FINAL_VALUES = new TreeSet<>();

  static {
    ATTEMPT_FINAL_VALUES.add(Boolean.TRUE.toString());
    ATTEMPT_FINAL_VALUES.add(Boolean.FALSE.toString());
  }

  private static void assertTrue(boolean condition, String description, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(description, args));
    }
  }

  private static String getName(Class<?> cls) {
    for (Class<?> c : METER_TYPES) {
      if (c.isAssignableFrom(cls)) {
        return c.getSimpleName();
      }
    }
    return cls.getSimpleName();
  }

  private static boolean isPercentile(Id id) {
    final String stat = Utils.getTagValue(id, "statistic");
    return "percentile".equals(stat);
  }

  private static void validateIpcMeter(Registry registry, IpcMetric metric, Class<?> type) {
    final String name = metric.metricName();
    registry.stream()
        .filter(m -> name.equals(m.id().name()) && !isPercentile(m.id()))
        .forEach(m -> {
          assertTrue(type.isAssignableFrom(m.getClass()),
              "[%s] has the wrong type, expected %s but found %s",
              m.id(), type.getSimpleName(), getName(m.getClass()));
          metric.validate(m.id());
        });
  }

  /**
   * Validate all of the common IPC metrics contained within the specified registry.
   *
   * @param registry
   *     Registry to query for IPC metrics.
   */
  public static void validate(Registry registry) {
    validateIpcMeter(registry, IpcMetric.clientCall, Timer.class);
    validateIpcMeter(registry, IpcMetric.serverCall, Timer.class);
    validateIpcMeter(registry, IpcMetric.clientInflight, DistributionSummary.class);
    validateIpcMeter(registry, IpcMetric.serverInflight, DistributionSummary.class);
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private <T extends Enum<T>> void validateValues(Id id, String key, Class<T> cls) {
    String value = Utils.getTagValue(id, key);
    if (value != null) {
      try {
        Enum.valueOf(cls, value);
      } catch (Exception e) {
        String values = Arrays.stream(cls.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(String.format(
            "[%s] invalid value for dimension %s, acceptable values are (%s)",
            id, key, values));
      }
    }
  }

  private void validateValues(Id id, String key, SortedSet<String> allowedValues) {
    String value = Utils.getTagValue(id, key);
    if (value != null && !allowedValues.contains(value)) {
      String values = allowedValues.stream()
          .collect(Collectors.joining(", "));
      throw new IllegalArgumentException(String.format(
          "[%s] invalid value for dimension %s, acceptable values are (%s)",
          id, key, values));
    }
  }

  /**
   * Validate that the specified id has the correct tagging for this IPC metric.
   *
   * @param id
   *     Meter identifier to validate.
   */
  public void validate(Id id) {
    assertTrue(metricName.equals(id.name()), "%s != %s", metricName, id.name());

    // Check that required dimensions are present
    EnumSet<IpcTagKey> dimensions = requiredDimensions.clone();
    dimensions.forEach(k -> {
      String value = Utils.getTagValue(id, k.key());
      assertTrue(value != null, "[%s] is missing required dimension %s", id, k.key());
    });

    // Check the values are correct for enum keys
    validateValues(id, IpcTagKey.attemptFinal.key(), ATTEMPT_FINAL_VALUES);
    validateValues(id, IpcTagKey.attempt.key(), IpcAttempt.class);
    validateValues(id, IpcTagKey.result.key(), IpcResult.class);
    validateValues(id, IpcTagKey.status.key(), IpcStatus.class);

    // Check that result and status are consistent
    String status = Utils.getTagValue(id, IpcTagKey.status.key());
    if (status != null) {
      IpcResult expected = IpcStatus.valueOf(status).result();
      IpcResult actual = IpcResult.valueOf(Utils.getTagValue(id, IpcTagKey.result.key()));
      assertTrue(actual == expected, "[%s] result is inconsistent with status", id);
    }
  }
}
