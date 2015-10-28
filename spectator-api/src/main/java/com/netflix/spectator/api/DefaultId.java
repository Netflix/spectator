/**
 * Copyright 2015 Netflix, Inc.
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

/** Id implementation for the default registry. */
final class DefaultId implements Id {

  private final String name;
  private final TagList tags;

  /** Create a new instance. */
  public DefaultId(String name) {
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
    return new DefaultId(name, tags == TagList.EMPTY ? new TagList(tag.key(), tag.value()) : tags.mergeTag(tag));
  }

  @Override public DefaultId withTag(String key, String value) {
    TagList tag = new TagList(key, value);
    return new DefaultId(name, tags == TagList.EMPTY ? tag : tags.mergeTag(tag));
  }

  @Override public DefaultId withTags(Iterable<Tag> ts) {
    TagList tmp = (tags == TagList.EMPTY) ? TagList.create(ts) : tags.mergeList(ts);
    return new DefaultId(name, tmp);
  }

  @Override public DefaultId withTags(Map<String, String> ts) {
    TagList tmp = (tags == TagList.EMPTY) ? TagList.create(ts) : tags.mergeMap(ts);
    return new DefaultId(name, tmp);
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
    if (tags == TagList.EMPTY) {
      return this;
    } else {
      Map<String, String> ts = new TreeMap<>();
      for (Tag t : tags) {
        if (keys.contains(t.key()) == keep && !ts.containsKey(t.key())) {
          ts.put(t.key(), t.value());
        }
      }
      return new DefaultId(name, TagList.create(ts));
    }
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof DefaultId)) return false;
    DefaultId other = (DefaultId) obj;
    return name.equals(other.name)
      && ((tags == null && other.tags == null) || (tags != null && tags.equals(other.tags)));
  }

  @Override public int hashCode() {
    return name.hashCode() + (tags == TagList.EMPTY ? 0 : tags.hashCode());
  }

  @Override public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name);
    if (tags != TagList.EMPTY) {
      buf.append(':').append(tags);
    }
    return buf.toString();
  }
}
