/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.controllers;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;

import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilter;
import com.netflix.spectator.controllers.filter.TagMeasurementFilter;
import com.netflix.spectator.controllers.model.ApplicationRegistry;
import com.netflix.spectator.controllers.model.MetricValues;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.Map;
import java.util.HashMap;


/**
 * Provides an HTTP endpoint for polling spectator metrics.
 */
@RequestMapping("/spectator/metrics")
@RestController
@ConditionalOnExpression("${spectator.webEndpoint.enabled:false}")
public class MetricsController {
  @Autowired
  private Registry registry;

  /**
   * A measurement filter that accepts all measurements.
   */
  public static final Predicate<Measurement> ALL_MEASUREMENTS_FILTER = m -> true;

  @Value("${spectator.applicationName:}")
  private String applicationName;

  @Value("${spectator.applicationVersion:}")
  private String applicationVersion;

  @Value("${spectator.webEndpoint.prototypeFilter.path:}")
  private String prototypeFilterPath;

  private final Map<Id, String> knownMeterKinds = new HashMap<>();
  private Predicate<Measurement> defaultMeasurementFilter = null;

  /**
   * The default measurement filter is configured through properties.
   */
  public Predicate<Measurement> getDefaultMeasurementFilter() throws IOException {
    if (defaultMeasurementFilter != null) {
      return defaultMeasurementFilter;
    }

    if (!prototypeFilterPath.isEmpty()) {
      defaultMeasurementFilter = PrototypeMeasurementFilter.loadFromPath(
          prototypeFilterPath);
    } else {
      defaultMeasurementFilter = ALL_MEASUREMENTS_FILTER;
    }

    return defaultMeasurementFilter;
  }


  /**
   * Endpoint for querying current metric values.
   *
   * The result is a JSON document describing the metrics and their values.
   * The result can be filtered using query parameters or configuring the
   * controller instance itself.
   */
  @RequestMapping(method = RequestMethod.GET)
  public ApplicationRegistry getMetrics(@RequestParam Map<String, String> filters)
    throws IOException {
    boolean all = filters.get("all") != null;
    String filterMeterNameRegex = filters.getOrDefault("meterNameRegex", "");
    String filterTagNameRegex = filters.getOrDefault("tagNameRegex", "");
    String filterTagValueRegex = filters.getOrDefault("tagValueRegex", "");
    TagMeasurementFilter queryFilter = new TagMeasurementFilter(
        filterMeterNameRegex, filterTagNameRegex, filterTagValueRegex);
    Predicate<Measurement> filter;
    if (all) {
      filter = queryFilter;
    } else {
      filter = queryFilter.and(getDefaultMeasurementFilter());
    }

    ApplicationRegistry response = new ApplicationRegistry();
    response.setApplicationName(applicationName);
    response.setApplicationVersion(applicationVersion);
    response.setMetrics(encodeRegistry(registry, filter));
    return response;
  }

  /**
   * Internal API for encoding a registry that can be encoded as JSON.
   * This is a helper function for the REST endpoint and to test against.
   */
  Map<String, MetricValues> encodeRegistry(
        Registry sourceRegistry, Predicate<Measurement> filter) {
    Map<String, MetricValues> metricMap = new HashMap<>();

    /*
     * Flatten the meter measurements into a map of measurements keyed by
     * the name and mapped to the different tag variants.
     */
    for (Meter meter : sourceRegistry) {
      String kind = knownMeterKinds.computeIfAbsent(
                         meter.id(), k -> meterToKind(sourceRegistry, meter));

      for (Measurement measurement : meter.measure()) {
        if (!filter.test(measurement)) {
          continue;
        }
        if (Double.isNaN(measurement.value())) {
          continue;
        }

        String meterName = measurement.id().name();
        MetricValues have = metricMap.get(meterName);
        if (have == null) {
          metricMap.put(meterName, new MetricValues(kind, measurement));
        } else {
          have.addMeasurement(measurement);
        }
      }
    }
    return metricMap;
  }

  /**
   * Determine the type of a meter for reporting purposes.
   *
   * @param registry
   *    Used to provide supplemental information (e.g. to search for the meter).
   *
   * @param meter
   *    The meters whose kind we want to know.
   *
   * @return
   *    A string such as "Counter". If the type cannot be identified as one of
   *    the standard Spectator api interface variants, then the simple class name
   *    is returned.
   */
  public static String meterToKind(Registry registry, Meter meter) {
    String kind;
    if (meter instanceof Timer) {
      kind = "Timer";
    } else if (meter instanceof Counter) {
      kind = "Counter";
    } else if (meter instanceof Gauge) {
      kind = "Gauge";
    } else if (meter instanceof DistributionSummary) {
      kind = "DistributionSummary";
    } else {
      kind = meter.getClass().getSimpleName();
    }
    return kind;
  }
}
