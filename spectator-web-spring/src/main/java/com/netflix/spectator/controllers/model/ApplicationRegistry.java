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

package com.netflix.spectator.controllers.model;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Objects;



/**
 * An ApplicationRegistry is the encoding of the metrics from a particular application.
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class ApplicationRegistry {
  private static long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
  private String applicationName;
  private String applicationVersion;
  private Map<String, MetricValues> metrics;

  /**
   * The application name exporting the metrics.
   */
  public String getApplicationName() {
    return applicationName;
  }

  /**
   * The application name exporting the metrics.
   */
  public void setApplicationName(String name) {
     applicationName = name;
  }

  /**
   * The version of the application name exporting the metrics.
   */
  public String getApplicationVersion() {
    return applicationVersion;
  }

  /**
   * The version of the application name exporting the metrics.
   */
  public void setApplicationVersion(String version) {
      applicationVersion = version;
  }

  /**
   * The JVM start time (millis).
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   * The current metrics.
   */
  public Map<String, MetricValues> getMetrics() {
    return metrics;
  }

  /**
   * Sets the metric map.
   *
   * This is just an assignment, not a copy.
   */
    public void setMetrics(Map<String, MetricValues> map) {
    metrics = map;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ApplicationRegistry)) return false;
    ApplicationRegistry other = (ApplicationRegistry) obj;
    return applicationName.equals(other.applicationName) && metrics.equals(other.metrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(applicationName, metrics);
  }
}
