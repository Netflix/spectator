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

package com.netflix.spectator.controllers.filter;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Tag;

import java.util.regex.Pattern;


/**
 * A simple MeasurementFilter based on meter/tag names and values.
 */
public class TagMeasurementFilter implements MeasurementFilter {
  private final Pattern meterNamePattern;
  private final Pattern tagNamePattern;
  private final Pattern tagValuePattern;

  /**
   * Constructor.
   */
  public TagMeasurementFilter(String meterNameRegex, String tagNameRegex, String tagValueRegex) {
    if (meterNameRegex != null && !meterNameRegex.isEmpty() && !meterNameRegex.equals(".*")) {
      meterNamePattern = Pattern.compile(meterNameRegex);
    } else {
      meterNamePattern = null;
    }

    if (tagNameRegex != null && !tagNameRegex.isEmpty() && !tagNameRegex.equals(".*")) {
      tagNamePattern = Pattern.compile(tagNameRegex);
    } else {
      tagNamePattern = null;
    }

    if (tagValueRegex != null && !tagValueRegex.isEmpty() && !tagValueRegex.equals(".*")) {
      tagValuePattern = Pattern.compile(tagValueRegex);
    } else {
      tagValuePattern = null;
    }
  }

  /**
   * Implements MeasurementFilter interface.
   */
  public boolean keep(Meter meter, Measurement measurement) {
    Id id = measurement.id();
    if (meterNamePattern != null
        && !meterNamePattern.matcher(id.name()).matches()) {
      return false;
    }

    if (tagNamePattern != null || tagValuePattern != null) {
      for (Tag tag : id.tags()) {
        boolean nameOk = tagNamePattern == null
                         || tagNamePattern.matcher(tag.key()).matches();
        boolean valueOk = tagValuePattern == null
                         || tagValuePattern.matcher(tag.value()).matches();
        if (nameOk && valueOk) {
          return true;
        }
      }
      return false;
    }

    return true;
  }
};

