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


public class PrototypeMeasurementFilterSpecification {
  public static class TagFilterSpecification {
    public String key;   // regex
    public String value; // regex

    TagFilterSpecification() {
        key = null;
        value = null;
    }
    TagFilterSpecification(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof TagFilterSpecification)) {
        return false;
      }
      TagFilterSpecification other = (TagFilterSpecification)obj;
      return key.equals(other.key) && value.equals(other.value);
    }

    @Override
    public String toString() {
      return String.format("%s=%s", key, value);
    }
  }

  public static class ValueFilterSpecification {
    static final ValueFilterSpecification ALL = new ValueFilterSpecification();
    static {
        ALL.tags.add(new TagFilterSpecification(".*", ".*"));
    }

    ValueFilterSpecification() {}

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof ValueFilterSpecification)) {
        return false;
      }
      ValueFilterSpecification other = (ValueFilterSpecification)obj;
      return tags.equals(other.tags);
    }

    @Override
    public String toString() {
      return tags.toString();
    }

    public final List<TagFilterSpecification> tags
        = new ArrayList<TagFilterSpecification>();
  };

  public static class MeterFilterSpecification {
    public MeterFilterSpecification() {}
    public MeterFilterSpecification(List<ValueFilterSpecification> values) {
        this.values.addAll(values);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof MeterFilterSpecification)) {
        return false;
      }
      MeterFilterSpecification other = (MeterFilterSpecification)obj;
      return values.equals(other.values);
    }

    @Override
    public String toString() {
      return values.toString();
    }

    public final List<ValueFilterSpecification> values
        = new ArrayList<ValueFilterSpecification>();
  };


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
        = (PrototypeMeasurementFilterSpecification)obj;
    return include.equals(other.include) && exclude.equals(other.exclude);
  }

  @Override
  public String toString() {
    return String.format("INCLUDE=%s\nEXCLUDE=%s",
                         include.toString(), exclude.toString());
  }

  public final Map<String, MeterFilterSpecification> include
      = new HashMap<String, MeterFilterSpecification>();

  public final Map<String, MeterFilterSpecification> exclude
      = new HashMap<String, MeterFilterSpecification>();
};
