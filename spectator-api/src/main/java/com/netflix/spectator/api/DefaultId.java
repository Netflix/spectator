/*
 * Copyright 2014-2020 Netflix, Inc.
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/** Id implementation for the default registry. */
final class DefaultId implements Id {

  private final String name;
  private final ArrayTagSet tags;

  /** Create a new instance. */
  DefaultId(String name) {
    this(name, ArrayTagSet.EMPTY);
  }

  /** Create a new instance. */
  DefaultId(String name, ArrayTagSet tags) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.tags = Preconditions.checkNotNull(tags, "tags");
  }

  @Override public String name() {
    return name;
  }

  @Override public Iterable<Tag> tags() {
    return tags;
  }

  @Override public DefaultId withTag(Tag tag) {
    return new DefaultId(name, tags.add(tag));
  }

  @Override public DefaultId withTag(String key, String value) {
    return new DefaultId(name, tags.add(key, value));
  }

  @Override public DefaultId withTags(String... ts) {
    return new DefaultId(name, tags.addAll(ts));
  }

  @Override public DefaultId withTags(Tag... ts) {
    return new DefaultId(name, tags.addAll(ts));
  }

  @Override public DefaultId withTags(Iterable<Tag> ts) {
    return new DefaultId(name, tags.addAll(ts));
  }

  @Override public DefaultId withTags(Map<String, String> ts) {
    return new DefaultId(name, tags.addAll(ts));
  }

  @Override public String getKey(int i) {
    return i == 0 ? "name" : tags.getKey(i - 1);
  }

  @Override public String getValue(int i) {
    return i == 0 ? name : tags.getValue(i - 1);
  }

  @Override public int size() {
    return tags.size() + 1;
  }

  @Override public Id filter(BiPredicate<String, String> predicate) {
    return new DefaultId(name, tags.filter(predicate));
  }

  /**
   * Returns a new id with the tag list sorted by key and with no duplicate keys.  This is equivalent to
   * {@code rollup(Collections.<String>emptySet(), false)}.
   */
  DefaultId normalize() {
    return rollup(Collections.emptySet(), false);
  }

  /**
   * Create a new id by possibly removing tags from the list.  This operation will:<br/>
   *     1) remove keys as specified by the parameters<br/>
   *     2) dedup entries that have the same key, the first value associated with the key will be the one kept,<br/>
   *     3) sort the list by the tag keys.
   *
   * @param keys
   *     Set of keys to either keep or remove.
   * @param keep
   *     If true, then the new id can only have tag keys in the provided set. Otherwise the new id
   *     can only have ids not in that set.
   * @return
   *     New identifier after applying the rollup.
   */
  DefaultId rollup(Set<String> keys, boolean keep) {
    if (tags.isEmpty()) {
      return this;
    } else {
      Map<String, String> ts = new TreeMap<>();
      for (Tag t : tags) {
        if (keys.contains(t.key()) == keep && !ts.containsKey(t.key())) {
          ts.put(t.key(), t.value());
        }
      }
      return new DefaultId(name, ArrayTagSet.create(ts));
    }
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof DefaultId)) return false;
    DefaultId other = (DefaultId) obj;
    return name.equals(other.name) && tags.equals(other.tags);
  }

  @Override public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + tags.hashCode();
    return result;
  }

  @Override public String toString() {
    return name + tags;
  }
}
