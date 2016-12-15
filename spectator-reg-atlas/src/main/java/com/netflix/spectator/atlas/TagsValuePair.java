/*
 * Copyright 2014-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pair consisting of a set of tags and a double value.
 */
class TagsValuePair {

  /** Create a pair from a measurement. */
  static TagsValuePair from(Measurement m) {
    return new TagsValuePair(convert(m.id()), m.value());
  }

  /**
   * Create a pair from a measurement.
   *
   * @param commonTags
   *     Common tags that will get added in after computing the tag map from the
   *     measurement. These will override any values with the same key already in
   *     the map.
   * @param m
   *     The input measurement used for the based tags and the value.
   * @return
   *     Pair based on the common tags and measurement.
   */
  static TagsValuePair from(Map<String, String> commonTags, Measurement m) {
    Map<String, String> tags = convert(m.id());
    tags.putAll(commonTags);
    return new TagsValuePair(tags, m.value());
  }

  private static Map<String, String> convert(Id id) {
    Map<String, String> tags = new HashMap<>();

    for (Tag t : id.tags()) {
      String k = ValidCharacters.toValidCharset(t.key());
      String v = ValidCharacters.toValidCharset(t.value());
      tags.put(k, v);
    }

    String name = ValidCharacters.toValidCharset(id.name());
    tags.put("name", name);

    return tags;
  }

  private final Map<String, String> tags;
  private final double value;

  /** Create a new instance. */
  TagsValuePair(Map<String, String> tags, double value) {
    this.tags = Collections.unmodifiableMap(tags);
    this.value = value;
  }

  /** Return the tags from the pair. */
  Map<String, String> tags() {
    return tags;
  }

  /** Return the value from the pair. */
  double value() {
    return value;
  }

  @Override public String toString() {
    return "TagsValuePair(" + tags + "," + value + ")";
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof TagsValuePair)) return false;
    TagsValuePair other = (TagsValuePair) obj;
    return tags.equals(other.tags) && Double.compare(value, other.value) == 0;
  }

  @Override public int hashCode() {
    int result = tags.hashCode();
    result = 31 * result + Double.hashCode(value);
    return result;
  }
}
