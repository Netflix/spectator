/*
 * Copyright 2014-2019 Netflix, Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A {@link RequestMetricCollector} that captures request level metrics for AWS clients.
 */
public class SpectatorRequestMetricCollector extends RequestMetricCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpectatorRequestMetricCollector.class);

  private static final Set<String> ALL_DEFAULT_TAGS = new HashSet<>();

  private static final String TAG_ERROR = "error";
  private static final String TAG_REQUEST_TYPE = "requestType";
  private static final String TAG_THROTTLE_EXCEPTION = "throttleException";
  private static final String UNKNOWN = "UNKNOWN";

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

  private static final Field[] GAUGES = {
      Field.HttpClientPoolAvailableCount,
      Field.HttpClientPoolLeasedCount,
      Field.HttpClientPoolPendingCount,
  };

  private static final TagField[] TAGS = {
      new TagField(Field.ServiceEndpoint, SpectatorRequestMetricCollector::getHost),
      new TagField(Field.ServiceName),
      new TagField(Field.StatusCode)
  };

  private static final TagField[] ERRORS = {
      new TagField(Field.AWSErrorCode),
      new TagField(Field.Exception, e -> e.getClass().getSimpleName())
  };

  static {
    Stream.concat(Arrays.stream(TAGS), Arrays.stream(ERRORS))
        .map(TagField::getName).forEach(ALL_DEFAULT_TAGS::add);
    ALL_DEFAULT_TAGS.addAll(Arrays.asList(TAG_THROTTLE_EXCEPTION, TAG_REQUEST_TYPE, TAG_ERROR));
  }

  private final Registry registry;
  private final Map<String, String> customTags;

  /**
   * Constructs a new instance.
   */
  public SpectatorRequestMetricCollector(Registry registry) {
    this(registry, Collections.emptyMap());
  }

  /**
   * Constructs a new instance. Custom tags provided by the user will be applied to every metric.
   * Overriding built-in tags is not allowed.
   */
  public SpectatorRequestMetricCollector(Registry registry, Map<String, String> customTags) {
    super();
    this.registry = Preconditions.checkNotNull(registry, "registry");
    Preconditions.checkNotNull(customTags, "customTags");
    this.customTags = new HashMap<>();
    customTags.forEach((key, value) -> {
      if (ALL_DEFAULT_TAGS.contains(key)) {
        registry.propagate(new IllegalArgumentException("Invalid custom tag " + key
              + " - cannot override built-in tag"));
      } else {
        this.customTags.put(key, value);
      }
    });
  }

  @Override
  public void collectMetrics(Request<?> request, Response<?> response) {
    final AWSRequestMetrics metrics = request.getAWSRequestMetrics();
    if (metrics.isEnabled()) {
      final Map<String, String> allTags = getAllTags(request);
      final TimingInfo timing = metrics.getTimingInfo();

      for (Field counter : COUNTERS) {
        Optional.ofNullable(timing.getCounter(counter.name()))
            .filter(v -> v.longValue() > 0)
            .ifPresent(v -> registry.counter(metricId(counter, allTags)).increment(v.longValue()));
      }

      for (Field timer : TIMERS) {
        Optional.ofNullable(timing.getLastSubMeasurement(timer.name()))
            .filter(TimingInfo::isEndTimeKnown)
            .ifPresent(t -> registry.timer(metricId(timer, allTags))
                .record(t.getEndTimeNano() - t.getStartTimeNano(), TimeUnit.NANOSECONDS));
      }

      for (Field gauge : GAUGES) {
        Optional.ofNullable(timing.getCounter(gauge.name()))
            .ifPresent(v -> registry.gauge(metricId(gauge, allTags)).set(v.doubleValue()));
      }

      notEmpty(metrics.getProperty(Field.ThrottleException)).ifPresent(throttleExceptions -> {
        final Id throttling = metricId("throttling", allTags);
        throttleExceptions.forEach(ex ->
            registry.counter(throttling.withTag(TAG_THROTTLE_EXCEPTION,
                    ex.getClass().getSimpleName())).increment());
      });
    }
  }

  private Id metricId(Field metric, Map<String, String> tags) {
    return metricId(metric.name(), tags);
  }

  private Id metricId(String metric, Map<String, String> tags) {
    return registry.createId(idName(metric), tags);
  }

  private Map<String, String> getAllTags(Request<?> request) {
    final AWSRequestMetrics metrics = request.getAWSRequestMetrics();
    final Map<String, String> allTags = new HashMap<>();
    for (TagField tag : TAGS) {
      allTags.put(tag.getName(), tag.getValue(metrics).orElse(UNKNOWN));
    }
    allTags.put(TAG_REQUEST_TYPE, request.getOriginalRequest().getClass().getSimpleName());
    final boolean error = isError(metrics);
    if (error) {
      for (TagField tag : ERRORS) {
        allTags.put(tag.getName(), tag.getValue(metrics).orElse(UNKNOWN));
      }
    }
    allTags.put(TAG_ERROR, Boolean.toString(error));
    allTags.putAll(customTags);
    return Collections.unmodifiableMap(allTags);
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

  /**
   * Extracts and transforms the first item from a list.
   *
   * @param properties
   *     The list of properties to filter, may be null or empty
   * @param transform
   *     The transform to apply to the extracted list item. The
   *     transform is only applied if the list contains a non-null
   *     item at index 0.
   * @param <R>
   *     The transform return type
   * @return
   *     The transformed value, or Optional.empty() if there is no
   *     non-null item at index 0 of the list.
   */
  //VisibleForTesting
  static <R> Optional<R> firstValue(List<Object> properties, Function<Object, R> transform) {
    return notEmpty(properties).map(v -> v.get(0)).map(transform::apply);
  }

  private static boolean isError(AWSRequestMetrics metrics) {
    for (TagField err : ERRORS) {
      if (err.getValue(metrics).isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static String getHost(Object u) {
    try {
      return URI.create(u.toString()).getHost();
    } catch (Exception e) {
      LOGGER.debug("failed to parse endpoint uri: " + u, e);
      return UNKNOWN;
    }
  }

  private static class TagField {
    private final Field field;
    private final String name;
    private final Function<Object, String> tagExtractor;

    TagField(Field field) {
      this(field, Object::toString);
    }

    TagField(Field field, Function<Object, String> tagExtractor) {
      this.field = field;
      this.tagExtractor = tagExtractor;
      this.name = Introspector.decapitalize(field.name());
    }

    String getName() {
      return name;
    }

    Optional<String> getValue(AWSRequestMetrics metrics) {
      return firstValue(metrics.getProperty(field), tagExtractor);
    }
  }
}
