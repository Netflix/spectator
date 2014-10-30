/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.api;

import com.netflix.spectator.impl.Preconditions;

import java.util.*;

/** Id implementation for the default registry. */
final class DefaultId implements Id {

  private static final Set<String> EMPTY = new HashSet<>();

  private final String name;
  private final TagList tags;

  /** Create a new instance. */
  DefaultId(String name) {
    this(name, TagList.EMPTY);
  }

  /** Create a new instance. */
  DefaultId(String name, TagList tags) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.tags = tags;
  }

  @Override public String name() {
    return name;
  }

  @Override public Iterable<Tag> tags() {
    return (tags == TagList.EMPTY) ? Collections.<Tag>emptyList() : tags;
  }

  @Override public DefaultId withTag(Tag tag) {
    return new DefaultId(name, new TagList(tag.key(), tag.value(), tags));
  }

  @Override public DefaultId withTag(String key, String value) {
    return new DefaultId(name, new TagList(key, value, tags));
  }

  @Override public DefaultId withTags(Iterable<Tag> ts) {
    TagList tmp = (tags == null) ? TagList.create(ts) : tags.prepend(ts);
    return new DefaultId(name, tmp);
  }

  /**
   * Returns a new id with the tag list sorted by key and with no duplicate keys. If a duplicate
   * is found the last entry in the list with a given key will be used.
   */
  public DefaultId normalize() {
    return rollup(EMPTY, false);
  }

  /**
   * Create a new id by removing tags from the list. This operation will 1) sort the list by the
   * tag keys, 2) dedup entries if multiple have the same key, and 3) remove keys specified by
   * the parameters.
   *
   * @param keys
   *     Set of keys to either keep or remove.
   * @param keep
   *     If true, then the new id can only have tag keys in the provided set. Otherwise the new id
   *     can only have ids not in that set.
   * @return
   *     New identifier after applying the rollup.
   */
  public DefaultId rollup(Set<String> keys, boolean keep) {
    Map<String, String> ts = new TreeMap<>();
    for (Tag t : tags) {
      if (keys.contains(t.key()) == keep) {
        ts.put(t.key(), t.value());
      }
    }
    return new DefaultId(name, TagList.create(ts));
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof DefaultId)) return false;
    DefaultId other = (DefaultId) obj;
    return name.equals(other.name)
      && ((tags == null && other.tags == null) || (tags != null && tags.equals(other.tags)));
  }

  @Override public int hashCode() {
    return name.hashCode() + (tags == null ? 0 : tags.hashCode());
  }

  @Override public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name);
    for (Tag t : tags()) {
      buf.append(":").append(t.key()).append("=").append(t.value());
    }
    return buf.toString();
  }
}
