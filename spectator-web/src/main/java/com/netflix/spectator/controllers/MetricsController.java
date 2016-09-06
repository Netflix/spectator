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
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;

import com.netflix.spectator.controllers.model.ApplicationRegistry;
import com.netflix.spectator.controllers.filter.ChainedMeasurementFilter;
import com.netflix.spectator.controllers.model.MetricValuesMap;
import com.netflix.spectator.controllers.model.MetricValues;
import com.netflix.spectator.controllers.filter.MeasurementFilter;
import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilter;
import com.netflix.spectator.controllers.filter.TagMeasurementFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


@RequestMapping("/spectator/metrics")
@RestController
@ConditionalOnExpression("${spectator.endpoint.enabled:false}")
public class MetricsController {

  @Autowired
  Registry registry;

  @Value("${spectator.applicationName:}")
  private String applicationName;

  @Value("${spectator.applicationVersion:}")
  public String applicationVersion;

  @Value("${spectator.endpoint.tagFilter.meterNameRegex:.*}")
  private String meterNameRegex;

  @Value("${spectator.endpoint.tagFilter.tagNameRegex:}")
  private String tagNameRegex;

  @Value("${spectator.endpoint.tagFilter.tagValueRegex:}")
  private String tagValueRegex;

  @Value("${spectator.endpoint.prototypeFilter.path:}")
  private String prototypeFilterPath;

  private MeasurementFilter defaultMeasurementFilter = null;

  public MeasurementFilter getDefaultMeasurementFilter() throws IOException {
    if (defaultMeasurementFilter != null) {
      return defaultMeasurementFilter;
    }

    if (!prototypeFilterPath.isEmpty()) {
      defaultMeasurementFilter = PrototypeMeasurementFilter.loadFromPath(
          prototypeFilterPath);
    } else {
      defaultMeasurementFilter = new TagMeasurementFilter(
          meterNameRegex, tagNameRegex, tagValueRegex);
    }

    return defaultMeasurementFilter;
  }

  static String getWithDefault(Map<String, String> map,
                               String key, String defaultValue) {
    String value = map.get(key);
    return value == null ? defaultValue : value;
  }

  @RequestMapping(method=RequestMethod.GET)
  public ApplicationRegistry getMetrics(@RequestParam Map<String, String> filters)
    throws IOException {
    boolean all = filters.get("all") != null;
    String filterMeterNameRegex = getWithDefault(filters, "meterNameRegex", "");
    String filterTagNameRegex = getWithDefault(filters, "tagNameRegex", "");
    String filterTagValueRegex = getWithDefault(filters, "tagValueRegex", "");
    TagMeasurementFilter queryFilter = new TagMeasurementFilter(
        filterMeterNameRegex, filterTagNameRegex, filterTagValueRegex);
    MeasurementFilter filter;
    if (all) {
      filter = queryFilter;
    } else {
      filter = new ChainedMeasurementFilter(queryFilter,
                                            getDefaultMeasurementFilter());
    }

    ApplicationRegistry response = new ApplicationRegistry();
    response.applicationName = applicationName;
    response.metrics = encodeRegistry(registry, filter);
    return response;
  }

  MetricValuesMap encodeRegistry(Registry registry, MeasurementFilter filter) {
    MetricValuesMap metricMap = new MetricValuesMap();

    /**
     * Spectator meters seam to group measurements. The meter name is the
     * measurement name prefix. It seems that all the measurements within a
     * meter instance share the same tag values as the meter.
     * Different instances are different tag assignments, keeping the groups
     * of measurements together.
     *
     * For now, we are not going to make this assumption and will just flatten
     * out the whole measurement space. That makes fewer assumptions for now.
     * If we make grouping assumptions we can factor out the labels into a more
     * terse json. But the difference isnt necessarily that big and if the
     * assumption doesnt hold, then forming the groupings will be complicated
     * and more expensive.
     */
    for (Meter meter : registry) {
      Map<Id, List<Measurement>> collection
          = new HashMap<Id, List<Measurement>>();
      collectValues(collection, meter, filter);

      if (!collection.isEmpty()) {
        String kind = meterToKind(meter);
        for (Map.Entry<Id, List<Measurement>> entry : collection.entrySet()) {
           String entryName = entry.getKey().name();
           MetricValues have = metricMap.get(entryName);
           if (have == null) {
             metricMap.put(entryName, MetricValues.make(kind, entry.getValue()));
           } else {
             have.addMeasurements(kind, entry.getValue());
           }
        }
      }
    }
    return metricMap;
  }

  /**
   * Collect all the meter values matching the tag pattern.
   */
  public static List<Id> collectValues(
      Map<Id, List<Measurement>> collection,
      Meter meter,
      MeasurementFilter filter) {
    List<Id> new_ids = new ArrayList<Id>();

    for (Measurement measurement : meter.measure()) {
      if (!filter.keep(meter, measurement)) {
          continue;
      }
      Id id = measurement.id();

      List<Measurement> valueList = collection.get(id);
      if (valueList == null) {
        valueList = new ArrayList<Measurement>();
        collection.put(id, valueList);
        new_ids.add(id);
      }
      valueList.add(measurement);
    }
    return new_ids;
  }

  public static String meterToKind(Meter meter) {
    String kind;
    if (meter instanceof Counter) {
      kind = "Counter";
    } else if (meter instanceof Gauge) {
      kind = "Gauge";
    } else if ((meter instanceof Timer) || (meter instanceof LongTaskTimer)) {
      kind = "Timer";
    } else if (meter instanceof DistributionSummary) {
      kind = "Distribution";
    } else {
      kind = meter.getClass().getName();
      int dot = kind.lastIndexOf('.');
      kind = kind.substring(dot + 1);
    }
    return kind;  // This might be a class name of some other unforseen type.
  }
};
