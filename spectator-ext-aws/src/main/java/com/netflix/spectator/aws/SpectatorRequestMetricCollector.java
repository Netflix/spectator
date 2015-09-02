/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.aws;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.AWSRequestMetrics.Field;
import com.amazonaws.util.TimingInfo;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;

import java.beans.Introspector;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A {@link RequestMetricCollector} that captures request level metrics for AWS clients.
 */
public class SpectatorRequestMetricCollector extends RequestMetricCollector {

  private static final Field[] TIMERS = {
      Field.ClientExecuteTime,
      Field.CredentialsRequestTime,
      Field.HttpClientReceiveResponseTime,
      Field.HttpClientSendRequestTime,
      Field.HttpRequestTime,
      Field.RequestMarshallTime,
      Field.RequestSigningTime,
      Field.ResponseProcessingTime,
      Field.RetryPauseTime
  };

  private static final Field[] COUNTERS = {
      Field.BytesProcessed,
      Field.HttpClientRetryCount,
      Field.RequestCount
  };

  private static final TagField[] TAGS = {
      new TagField(Field.ServiceEndpoint),
      new TagField(Field.ServiceName),
      new TagField(Field.StatusCode)
  };

  private static final TagField[] ERRORS = {
      new TagField(Field.AWSErrorCode),
      new TagField(Field.Exception, e -> e.getClass().getSimpleName())
  };

  private final Registry registry;

  /**
   * Constructs a new instance.
   */
  public SpectatorRequestMetricCollector(Registry registry) {
    super();
    this.registry = Preconditions.checkNotNull(registry, "registry");
  }

  @Override
  public void collectMetrics(Request<?> request, Response<?> response) {
    final AWSRequestMetrics metrics = request.getAWSRequestMetrics();
    if (metrics.isEnabled()) {
      final Map<String, String> baseTags = getBaseTags(request);
      final TimingInfo timing = metrics.getTimingInfo();

      for (Field counter : COUNTERS) {
        Optional.ofNullable(timing.getCounter(counter.name()))
            .filter(v -> v.longValue() > 0)
            .ifPresent(v -> registry.counter(metricId(counter, baseTags)).increment(v.longValue()));
      }

      for (Field timer : TIMERS) {
        Optional.ofNullable(timing.getLastSubMeasurement(timer.name()))
            .filter(TimingInfo::isEndTimeKnown)
            .ifPresent(t -> registry.timer(metricId(timer, baseTags))
                .record(t.getEndTimeNano() - t.getStartTimeNano(), TimeUnit.NANOSECONDS));
      }

      notEmpty(metrics.getProperty(Field.ThrottleException)).ifPresent(throttleExceptions -> {
        final Id throttling = metricId("throttling", baseTags);
        throttleExceptions.forEach(ex ->
            registry.counter(throttling.withTag("throttleException", ex.getClass().getSimpleName()))
                .increment());
      });
    }
  }

  private Id metricId(Field metric, Map<String, String> tags) {
    return metricId(metric.name(), tags);
  }

  private Id metricId(String metric, Map<String, String> tags) {
    return registry.createId(idName(metric), tags);
  }

  private Map<String, String> getBaseTags(Request<?> request) {
    final AWSRequestMetrics metrics = request.getAWSRequestMetrics();
    final Map<String, String> baseTags = new HashMap<>();
    for (TagField tag : TAGS) {
      baseTags.put(tag.getName(), tag.getValue(metrics).orElse("UNKNOWN"));
    }
    baseTags.put("requestType", request.getOriginalRequest().getClass().getSimpleName());
    final boolean error = isError(metrics);
    if (error) {
      for (TagField tag : ERRORS) {
        baseTags.put(tag.getName(), tag.getValue(metrics).orElse("UNKNOWN"));
      }
    }
    baseTags.put("error", Boolean.toString(error));
    return Collections.unmodifiableMap(baseTags);
  }

  /**
   * Produces the name of a metric from the name of the SDK measurement.
   *
   * @param name
   *     Name of the SDK measurement, usually from the enum
   *     {@link com.amazonaws.util.AWSRequestMetrics.Field}.
   * @return
   *     Name to use in the metric id.
   */
  //VisibleForTesting
  static String idName(String name) {
    return "aws.request." + Introspector.decapitalize(name);
  }

  private static Optional<List<Object>> notEmpty(List<Object> properties) {
    return Optional.ofNullable(properties).filter(v -> !v.isEmpty());
  }

  private static <R> Optional<R> firstValue(List<Object> properties, Function<Object, R> transform) {
    return notEmpty(properties).map(v -> transform.apply(v.get(0)));
  }

  private static boolean isError(AWSRequestMetrics metrics) {
    for (TagField err : ERRORS) {
      if (err.getValue(metrics).isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static class TagField {
    private final Field field;
    private final String name;
    private final Function<Object, String> tagExtractor;

    public TagField(Field field) {
      this(field, Object::toString);
    }

    public TagField(Field field, Function<Object, String> tagExtractor) {
      this.field = field;
      this.tagExtractor = tagExtractor;
      this.name = Introspector.decapitalize(field.name());
    }

    public String getName() {
      return name;
    }

    public Optional<String> getValue(AWSRequestMetrics metrics) {
      return firstValue(metrics.getProperty(field), tagExtractor);
    }
  }
}
