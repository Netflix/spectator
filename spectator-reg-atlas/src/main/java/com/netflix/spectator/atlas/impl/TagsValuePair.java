/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import java.util.Collections;
import java.util.Map;

/**
 * Pair consisting of a set of tags and a double value.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class TagsValuePair {

  private final Map<String, String> tags;
  private final double value;

  /** Create a new instance. */
  public TagsValuePair(Map<String, String> tags, double value) {
    this.tags = Collections.unmodifiableMap(tags);
    this.value = value;
  }

  /** Return the tags from the pair. */
  public Map<String, String> tags() {
    return tags;
  }

  /** Return the value from the pair. */
  public double value() {
    return value;
  }

  @Override public String toString() {
    return "TagsValuePair(" + tags + "," + value + ")";
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TagsValuePair)) return false;
    TagsValuePair other = (TagsValuePair) obj;
    return tags.equals(other.tags) && Double.compare(value, other.value) == 0;
  }

  @Override public int hashCode() {
    int result = tags.hashCode();
    result = 31 * result + Double.hashCode(value);
    return result;
  }
}
