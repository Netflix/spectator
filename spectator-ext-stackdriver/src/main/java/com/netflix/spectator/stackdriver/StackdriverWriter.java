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

package com.netflix.spectator.stackdriver;


import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest;
import com.google.api.services.monitoring.v3.model.Metric;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.api.services.monitoring.v3.model.TypedValue;

import com.google.api.client.http.HttpResponseException;
import com.google.common.collect.Lists;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/* These are builtin metric types that we might be able to use
 * except probably not because there isnt a label to say this is our use
 * as opposed to the assumption that "there is only one" of them.

    "agent.googleapis.com/jvm/memory/usage"
    "agent.googleapis.com/jvm/cpu/time"
    "agent.googleapis.com/jvm/gc/time"
    "agent.googleapis.com/jvm/thread/num_live"
    "agent.googleapis.com/jvm/thread/peak"
    "agent.googleapis.com/jvm/uptime"

    "agent.googleapis.com/tomcat/manager/sessions"
    "agent.googleapis.com/tomcat/request_processor/error_count"
    "agent.googleapis.com/tomcat/request_processor/processing_time"
    "agent.googleapis.com/tomcat/request_processor/request_count"
    "agent.googleapis.com/tomcat/request_processor/traffic_count"
    "agent.googleapis.com/tomcat/threads/current"
    "agent.googleapis.com/tomcat/threads/busy"
*/


/**
 * Adapter from Spectator Meters to Stackdriver Time Series Metrics.
 *
 * This class is not thread safe, but is assumed to be called from a
 * single thread.
 *
 * Places that are not thread safe include the management of
 * the custom descriptor cache and the use of java.text.DataFormat,
 * which is stated to not be thread-safe.
 */
public class StackdriverWriter {
  /**
   * This is the Spectator Id used for the timer measuring writeRegistry calls.
   */
  public static final String WRITE_TIMER_NAME = "stackdriver.writeRegistry";

  /**
   * Stackdriver limits TimeSeries create requests to 200 values.
   */
  private static final int MAX_TS_PER_REQUEST = 200;

  /**
   * Spectator doesnt have a public concrete Id class so we'll use the
   * default registry as a factory.
   *
   * We need to generate IDs for synthetic measurements (e.g. Timers).
   */
  private static final Registry ID_FACTORY = new DefaultRegistry(Clock.SYSTEM);

  /**
   * Internal logging.
   */
  private final Logger log = LoggerFactory.getLogger("StackdriverWriter");

  /**
   * TimeSeries data in Stackdriver API takes a date string, not a timestamp.
   *
   * We are using a literal 'Z' here rather than the time format Z because
   * Stackdriver doesnt recognize the format that Java produces. So instead
   * we'll report in UTC and have it convert our time into GMT for reporting.
   */
  private final SimpleDateFormat rfc3339;

  /**
   * This is required before writing time series data.
   */
  protected MonitoredResource monitoredResource;

  /**
   * The client-side stub talking to Stackdriver.
   */
  private final Monitoring service;

  /**
   * The name of the project we give to stackdriver for metric types and data.
   */
  private final String projectResourceName;

  /**
   * The name of our application is used to identify the source of the metrics.
   */
  private final String applicationName;

  /**
   * A unique id that distinguishes our data from other replicas with the same
   * applicationName (in the same project).
   */
  private String instanceId;

  /**
   * Manages the custom Metric Descriptors with Stackdriver.
   */
  private MetricDescriptorCache cache;

  /**
   * The Stackdriver TimeInterval Start time for CUMULATIVE (counter) types.
   */
  private String counterStartTimeRfc3339;

  /**
   * Kork needs this to add the hints.
   */
  public MetricDescriptorCache getDescriptorCache() {
    return cache;
  }

  /**
   * Filters the measurements we want to push to Stackdriver.
   */
  private Predicate<Measurement> measurementFilter;

  /**
   * Whether to add an APPLICATION_LABEL tag to time series data.
   * (otherwise the application is presumed to have been captured elsewhere).
   */
  private boolean addApplicationLabelToTimeSeriesData;

  /**
   * If Stackdriver were to return an error complaining about a TimeSeries
   * data element we are trying to write, it references that by its index
   * into the array we sent. That is pretty much useless to debug since we
   * have no idea what or how the data is organized.
   *
   * This method is intended to interpret the Stackdriver error message
   * in the context of the request we made and return the referenced element
   * so that it can be logged in more explicit detail to understand what
   * happened (most likely what label was missing from what descriptor type).
   *
   * @param msg
   *   The exception message.
   *
   * @param nextN
   *   The time series data from the request
   *
   * @return
   *   The time series element referred to by the message, or null.
   */
  public static TimeSeries
  findProblematicTimeSeriesElement(String msg, List<TimeSeries> nextN) {
    String regex = "timeSeries\\[(\\d+?)\\]\\.metric\\.labels\\[(\\d+?)\\]";
    Matcher matcher = Pattern.compile(regex).matcher(msg);
    if (matcher.find()) {
      int tsIndex = Integer.parseInt(matcher.group(1));
      return nextN.get(tsIndex);
    }
    return null;
  }

  /**
   * Constructs a writer instance.
   *
   * @param configParams
   *   The configuration parameters.
   */
  @SuppressWarnings("PMD.BooleanInversion")
  public StackdriverWriter(ConfigParams configParams) {
    cache = configParams.getDescriptorCache();
    service = configParams.getStackdriverStub();
    projectResourceName = "projects/" + configParams.getProjectName();
    applicationName = configParams.getApplicationName();
    instanceId = configParams.getInstanceId();
    measurementFilter = configParams.getMeasurementFilter();
    addApplicationLabelToTimeSeriesData
        = !configParams.isMetricUniquePerApplication();

    rfc3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000000Z'");
    rfc3339.setTimeZone(TimeZone.getTimeZone("GMT"));

    Date startDate = new Date(configParams.getCounterStartTime());
    counterStartTimeRfc3339 = rfc3339.format(startDate);

    log.info("Constructing StackdriverWriter {}={}",
             MetricDescriptorCache.INSTANCE_LABEL, instanceId);
  }

  /**
   * Helper function for logging time series errors in more detail.
   *
   * @see #findProblematicTimeSeriesElement
   */
  private void handleTimeSeriesResponseException(
       HttpResponseException rex, String msg, List<TimeSeries> nextN) {
    TimeSeries ts = findProblematicTimeSeriesElement(rex.getMessage(), nextN);
    if (ts != null) {
      log.error("{}:  time series element: {}",
                rex.getMessage(), ts.toString());
    } else {
      log.error("Caught HttpResponseException {}", msg, rex);
    }
  }

  /**
   * Convert a Spectator metric Meter into a Stackdriver TimeSeries entry.
   *
   * @param descriptor
   *   The Stackdriver MetricDescriptor for the measurement.
   *
   * @param measurement
   *   The Spectator Measurement to encode.
   *
   * @return
   *   The Stackdriver TimeSeries equivalent for the measurement.
   */
  public TimeSeries measurementToTimeSeries(
        MetricDescriptor descriptor, Measurement measurement) {
    Map<String, String> labels
        = cache.tagsToTimeSeriesLabels(descriptor, measurement.id().tags());

    long millis = measurement.timestamp();
    double value = measurement.value();

    TimeInterval timeInterval = new TimeInterval();
    Date date = new Date(millis);
    timeInterval.setEndTime(rfc3339.format(date));

    if (descriptor.getMetricKind().equals("CUMULATIVE")) {
      timeInterval.setStartTime(counterStartTimeRfc3339);
    }

    TypedValue typedValue = new TypedValue();
    typedValue.setDoubleValue(value);

    Point point = new Point();
    point.setValue(typedValue);
    point.setInterval(timeInterval);

    Metric metric = new Metric();
    metric.setType(descriptor.getType());
    metric.setLabels(labels);

    TimeSeries ts = new TimeSeries();
    ts.setResource(monitoredResource);
    ts.setMetric(metric);
    ts.setMetricKind(descriptor.getMetricKind());
    ts.setValueType("DOUBLE");
    ts.setPoints(Lists.<Point>newArrayList(point));

    return ts;
  }

  /**
   * Generate an Id for the derived timer measurements.
   *
   * @param id
   *   The original Measurement id
   *
   * @return
   *   A copy of the original but without the 'statistic' tag, and the
   *   name will be decorated with "__count" or "__totalTime" depending on the
   *   value of the original statistic tag.
   */
  Id deriveBaseTimerId(Id id) {
    String suffix = null;
    ArrayList<Tag> tags = new ArrayList<Tag>();
    for (Tag tag : id.tags()) {
      if (tag.key().equals("statistic")) {
        if (tag.value().equals("totalTime")) {
          suffix = "totalTime";
        } else if (tag.value().equals("count")) {
          suffix = "count";
        } else {
          throw new IllegalStateException(
                       "Unexpected statistic=" + tag.value());
        }
        continue;
      }
      tags.add(tag);
    }
    if (suffix == null) {
        // Didnt have statistic, so return original.
        return id;
    }

    return ID_FACTORY.createId(id.name() + "__" + suffix).withTags(tags);
  }

  /**
   * Remember the derived Ids that we use for timer transformations.
   */
  private Map<Id, Id> timerBaseIds = new HashMap<Id, Id>();

  /**
   * Transform timer measurements from a composite with count/totalTime tags
   * to a pair of specialized measurements without the "statistic" tag.
   *
   * This is so we can have different units for the measurement
   * MetricDescriptor to note that the totalTime is in nanoseconds.
   *
   * @param measurements
   *   The list of measurements to transform come from a Timer meter.
   *
   * @return
   *   A list of measurements, probably the same as the original size, where
   *   each of the elements corresponds to an original element but does not
   *   have the "statistic" label. Where the original was "count", the new
   *   id name will have a "__count" suffix and "__totalTime" for the
   *   "totalTime" statistic. The base name will be the same as the original.
   */
  private Iterable<Measurement> transformTimerMeasurements(
      Iterable<Measurement> measurements) {
    ArrayList<Measurement> result = new ArrayList<Measurement>();
    for (Measurement measurement : measurements) {
      if (!measurementFilter.test(measurement)) {
        continue;
      }
      Id id = timerBaseIds.computeIfAbsent(
                  measurement.id(), k -> deriveBaseTimerId(k));
      result.add(
         new Measurement(id, measurement.timestamp(), measurement.value()));
    }
    return result;
  }

  /**
   * Add a TimeSeries for each appropriate meter measurement.
   */
  void addMeterToTimeSeries(
        Registry registry, Meter meter, List<TimeSeries> tsList) {
    Iterable<Measurement> measurements = meter.measure();
    boolean applyFilter = true;

    if (cache.meterIsTimer(registry, meter)) {
       measurements = transformTimerMeasurements(measurements);
       applyFilter = false;
    }
    for (Measurement measurement : measurements) {
      if (applyFilter && !measurementFilter.test(measurement)) {
        continue;
      }

      MetricDescriptor descriptor
          = cache.descriptorOrNull(registry, meter, measurement);
      if (descriptor == null) {
        continue;
      }
      tsList.add(measurementToTimeSeries(descriptor, measurement));
    }
  }

  /**
   * Produce a TimeSeries for each appropriate measurement in the registry.
   */
  public List<TimeSeries> registryToTimeSeries(Registry registry) {
    log.info("Collecting metrics...");
    ArrayList<TimeSeries> tsList = new ArrayList<TimeSeries>();
    Iterator<Meter> iterator = registry.iterator();

    while (iterator.hasNext()) {
      addMeterToTimeSeries(registry, iterator.next(), tsList);
    }
    return tsList;
  }

  /**
   * Update Stackdriver with the current Spectator metric registry values.
   */
  public void writeRegistry(Registry registry) {
    // The timer will appear in our response, but be off by one invocation
    // because it isnt updated until we finish.
    Id writeId = registry.createId(WRITE_TIMER_NAME);
    registry.timer(writeId).record(new Runnable() {
        public void run() {
            writeRegistryHelper(registry);
        }
    });
  }

  /**
   * Get the monitoredResource for the TimeSeries data.
   *
   * This will return null if the resource cannot be determined.
   */
  MonitoredResource determineMonitoredResource() {
    if (monitoredResource == null) {
      String project
          = projectResourceName.substring(projectResourceName.indexOf('/') + 1);
      try {
          monitoredResource = new MonitoredResourceBuilder()
              .setStackdriverProject(project)
              .build();
          if (addApplicationLabelToTimeSeriesData) {
            cache.addExtraTimeSeriesLabel(
                MetricDescriptorCache.APPLICATION_LABEL, applicationName);
          }
          if (monitoredResource.getType().equals("global")) {
            cache.addExtraTimeSeriesLabel(
              MetricDescriptorCache.INSTANCE_LABEL, instanceId);
          }
          log.info("Using monitoredResource={} with extraTimeSeriesLabels={}",
                   monitoredResource, cache.getExtraTimeSeriesLabels());
      } catch (IOException ioex) {
        log.error("Unable to determine monitoredResource at this time.", ioex);
      }
    }
    return monitoredResource;
  }

  /**
   * Implementatio of writeRegistry wrapped for timing.
   */
  private void writeRegistryHelper(Registry registry) {
    MonitoredResource resource = determineMonitoredResource();
    if (resource == null) {
      log.warn("Cannot determine the managed resource - not flushing metrics.");
      return;
    }
    List<TimeSeries> tsList = registryToTimeSeries(registry);
    if (tsList.isEmpty()) {
       log.info("No metric data points.");
       return;
    }

    CreateTimeSeriesRequest tsRequest = new CreateTimeSeriesRequest();
    int offset = 0;
    int failed = 0;
    List<TimeSeries> nextN;

    log.info("Writing metrics...");
    while (offset < tsList.size()) {
      if (offset + MAX_TS_PER_REQUEST < tsList.size()) {
        nextN = tsList.subList(offset, offset + MAX_TS_PER_REQUEST);
        offset += MAX_TS_PER_REQUEST;
      } else {
        nextN = tsList.subList(offset, tsList.size());
        offset = tsList.size();
      }
      tsRequest.setTimeSeries(nextN);
      try {
        log.debug("Writing {} points.", nextN.size());
        service.projects().timeSeries().create(projectResourceName, tsRequest)
               .execute();
      } catch (HttpResponseException rex) {
        handleTimeSeriesResponseException(rex, "creating time series", nextN);
        failed += nextN.size();
      } catch (IOException ioex) {
        log.error("Caught HttpResponseException creating time series " + ioex);
        failed += nextN.size();
      }
    }
    log.info("Wrote {} values", tsList.size() - failed);
  }
}
