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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Represents the specification for a PrototypeMeasurementFilter.
 */
public class PrototypeMeasurementFilterSpecification {
  /**
   * Specifies how to filter an individual tag (name and value).
   */
  public static class TagFilterSpecification {
    private String key;   // regex
    private String value; // regex

    public String getKey() {
        return key;
    }
    public String getValue() {
        return value;
    }

    /**
     * Default constructor.
     */
    public TagFilterSpecification() {
        key = null;
        value = null;
    }

    /**
     * Construct a filter with particular regular expressions.
     */
    public TagFilterSpecification(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof TagFilterSpecification)) {
        return false;
      }
      TagFilterSpecification other = (TagFilterSpecification) obj;
      return key.equals(other.key) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }

    @Override
    public String toString() {
      return String.format("%s=%s", key, value);
    }
  }

  /**
   * Specifies how to filter values.
   *
   * Values are identified by a collection of tags, so the filter
   * is based on having a particular collection of name/value bindings.
   *
   * Actual values are not currently considered, but could be added later.
   */
  public static class ValueFilterSpecification {
    /**
     * A filter that allows everything.
     */
    static final ValueFilterSpecification ALL = new ValueFilterSpecification();
    static {
        ALL.tags.add(new TagFilterSpecification(".*", ".*"));
    }

    /**
     * Default constructor.
     */
    ValueFilterSpecification() {
      // empty.
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof ValueFilterSpecification)) {
        return false;
      }
      ValueFilterSpecification other = (ValueFilterSpecification) obj;
      return tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
      return tags.hashCode();
    }

    @Override
    public String toString() {
      return tags.toString();
    }


    /**
     * The tag specifications.
     */
    public List<TagFilterSpecification> getTags() {
        return tags;
    }

    /**
     * The minimal list of tag bindings that are covered by this specification.
     */
    private final List<TagFilterSpecification> tags
        = new ArrayList<TagFilterSpecification>();
  };

  /**
   * A specification for filtering on a Spectator Meter.
   *
   * A meter is a name pattern and collection of tag bindings.
   */
  public static class MeterFilterSpecification {
    /**
     * Default constructor.
     */
    public MeterFilterSpecification() {
      // empty.
    }

    /**
     * Constructor injecting a value specification.
     */
    public MeterFilterSpecification(List<ValueFilterSpecification> values) {
        this.values.addAll(values);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof MeterFilterSpecification)) {
        return false;
      }
      MeterFilterSpecification other = (MeterFilterSpecification) obj;
      return values.equals(other.values);
    }

    @Override
    public int hashCode() {
      return values.hashCode();
    }

    @Override
    public String toString() {
      return values.toString();
    }

    /**
     * The metric vlaue specifications.
     */
    public List<ValueFilterSpecification> getValues() {
      return values;
    }

    /**
     * The meter can be filtered on one or more collection of tag bindings.
     * In essence, this permits certain aspects of a meter to be considered
     * but not others.
     */
    private final List<ValueFilterSpecification> values
        = new ArrayList<ValueFilterSpecification>();
  };


  /**
   * Loads a specification from a file.
   */
  public static PrototypeMeasurementFilterSpecification loadFromPath(String path)
      throws IOException {
    byte[] jsonData = Files.readAllBytes(Paths.get(path));
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(
        jsonData, PrototypeMeasurementFilterSpecification.class);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof PrototypeMeasurementFilterSpecification)) {
      return false;
    }
    PrototypeMeasurementFilterSpecification other
        = (PrototypeMeasurementFilterSpecification) obj;
    return include.equals(other.include) && exclude.equals(other.exclude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(include, exclude);
  }

  @Override
  public String toString() {
    return String.format("INCLUDE=%s%nEXCLUDE=%s",
                         include.toString(), exclude.toString());
  }

  /**
   * The list of specifications for when meters should be included.
   */
  public Map<String, MeterFilterSpecification> getInclude() {
      return include;
  }

  /**
   * The list of specifications for when meters should be excluded.
   */
  public Map<String, MeterFilterSpecification> getExclude() {
      return exclude;
  }

  /**
   * Maps meter name patterns to the meter specification for that pattern.
   * The specified filter only passes meter/measurements that can be
   * traced back to a specification in this list.
   */
  private final Map<String, MeterFilterSpecification> include
      = new HashMap<String, MeterFilterSpecification>();

  /**
   * Maps meter name patterns to the meter specification for that pattern.
   * The specified filter does not pass meter/measurements that can be
   * traced back to a specification in this list.
   */
  private final Map<String, MeterFilterSpecification> exclude
      = new HashMap<String, MeterFilterSpecification>();
};
