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
import com.netflix.spectator.api.Tag;

import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * A simple MeasurementFilter based on meter/tag names and values.
 */
public class TagMeasurementFilter implements Predicate<Measurement> {
  private final Pattern meterNamePattern;
  private final Pattern tagNamePattern;
  private final Pattern tagValuePattern;

  private static Pattern regexToPatternOrNull(String regex) {
    if (regex != null && !regex.isEmpty() && !regex.equals(".*")) {
      return Pattern.compile(regex);
    }
    return null;
  }

  private static boolean stringMatches(String text, Pattern pattern) {
    return pattern == null || pattern.matcher(text).matches();
  }

  /**
   * Constructor.
   */
  public TagMeasurementFilter(String meterNameRegex, String tagNameRegex, String tagValueRegex) {
    meterNamePattern = regexToPatternOrNull(meterNameRegex);
    tagNamePattern = regexToPatternOrNull(tagNameRegex);
    tagValuePattern = regexToPatternOrNull(tagValueRegex);
  }

  /**
   * Implements MeasurementFilter interface.
   */
  @SuppressWarnings("PMD.JUnit4TestShouldUseTestAnnotation")
  @Override public boolean test(Measurement measurement) {
    Id id = measurement.id();
    if (!stringMatches(id.name(), meterNamePattern)) {
        return false;
    }

    if (tagNamePattern != null || tagValuePattern != null) {
      for (Tag tag : id.tags()) {
        boolean nameOk = stringMatches(tag.key(), tagNamePattern);
        boolean valueOk = stringMatches(tag.value(), tagValuePattern);
        if (nameOk && valueOk) {
          return true;
        }
      }
      return false;
    }

    return true;
  }
}
