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

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A collection of DataPoint instances for a common set of Tags.
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class TaggedDataPoints {
  private static class JacksonableTag implements Tag {
    private String key;
    private String value;

    JacksonableTag(Tag tag) {
      key = tag.key();
      value = tag.value();
    }

    public String key() {
      return key;
    }
    public String value() {
      return value;
    }
    public String getKey() {
      return key;
    }
    public String getValue() {
      return value;
    }

    @Override public int hashCode() {
      return Objects.hash(key, value);
    }

    @Override public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof Tag)) return false;

      Tag tag = (Tag) obj;
      return key.equals(tag.key()) && value.equals(tag.value());
    }

    @Override public String toString() {
      return key + "=" + value;
    }

    static List<Tag> convertTags(Iterable<Tag> iterable) {
      ArrayList<Tag> result = new ArrayList<Tag>();
      for (Tag tag : iterable) {
        result.add(new JacksonableTag(tag));
      }
      return result;
    }
  };

  /**
   * The tag bindings for the values.
   */
  public Iterable<Tag> getTags() {
    return tags;
  }

  /**
   * The current values.
   */
  public Iterable<DataPoint> getValues() {
    return dataPoints;
  }

  /**
   * Constructor from a single measurement data point.
   */
  public TaggedDataPoints(Measurement measurement) {
    tags = JacksonableTag.convertTags(measurement.id().tags());
    dataPoints = Arrays.asList(DataPoint.make(measurement));
  }

  /**
   * Constructor from a list of data points (for testing).
   */
  public TaggedDataPoints(Iterable<Tag> tags, List<DataPoint> dataPoints) {
    this.tags = JacksonableTag.convertTags(tags);
    this.dataPoints = dataPoints;
  }

  @Override
  public String toString() {
    return String.format("{TAGS={%s} DATA={%s}", tags, dataPoints);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TaggedDataPoints)) return false;
    TaggedDataPoints other = (TaggedDataPoints) obj;
    return tags.equals(other.tags) && dataPoints.equals(other.dataPoints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tags, dataPoints);
  }

  private List<Tag> tags;
  private List<DataPoint> dataPoints;
}
