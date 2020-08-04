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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Identifier for a meter or measurement.
 */
public interface Id extends TagList {

  /** Description of the measurement that is being collected. */
  String name();

  /** Other dimensions that can be used to classify the measurement. */
  Iterable<Tag> tags();

  /** Return a new id with an additional tag value. */
  Id withTag(String k, String v);

  /** Return a new id with an additional tag value. */
  Id withTag(Tag t);

  /**
   * Return a new id with an additional tag value using {@link Boolean#toString(boolean)} to
   * convert the boolean value to a string representation. This is merely a convenience function
   * for:
   *
   * <pre>
   *   id.withTag("key", Boolean.toString(value))
   * </pre>
   */
  default Id withTag(String k, boolean v) {
    return withTag(k, Boolean.toString(v));
  }

  /**
   * Return a new id with an additional tag value using {@link Enum#name()} to
   * convert the Enum to a string representation. This is merely a convenience function
   * for:
   *
   * <pre>
   *   id.withTag("key", myEnum.name())
   * </pre>
   */
  default <E extends Enum<E>> Id withTag(String k, Enum<E> v) {
    return withTag(k, v.name());
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  default Id withTags(String k1, String v1) {
    return withTag(k1, v1);
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  default Id withTags(String k1, String v1, String k2, String v2) {
    // The original reason for this method was to avoid allocating a string array before
    // creating a Tag array. The internals have changed so it can work on the string array
    // directly. The overload is kept for backwards compatiblity.
    final String[] ts = {
        k1, v1,
        k2, v2
    };
    return withTags(ts);
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  default Id withTags(String k1, String v1, String k2, String v2, String k3, String v3) {
    // The original reason for this method was to avoid allocating a string array before
    // creating a Tag array. The internals have changed so it can work on the string array
    // directly. The overload is kept for backwards compatiblity.
    final String[] ts = {
        k1, v1,
        k2, v2,
        k3, v3
    };
    return withTags(ts);
  }

  /** Return a new id with additional tag values. */
  default Id withTags(String... tags) {
    Id tmp = this;
    for (int i = 0; i < tags.length; i += 2) {
      tmp = tmp.withTag(tags[i], tags[i + 1]);
    }
    return tmp;
  }

  /** Return a new id with additional tag values. */
  default Id withTags(Tag... tags) {
    Id tmp = this;
    for (Tag t : tags) {
      tmp = tmp.withTag(t);
    }
    return tmp;
  }

  /** Return a new id with additional tag values. */
  default Id withTags(Iterable<Tag> tags) {
    Id tmp = this;
    for (Tag t : tags) {
      tmp = tmp.withTag(t);
    }
    return tmp;
  }

  /**
   * Return a new id with additional tag values.
   *
   * If using a {@link java.util.concurrent.ConcurrentMap}, note that the map <strong>should
   * not</strong> be concurrently modified during this call. It is up to the user to ensure
   * that it contains the correct set of tags that should be added to the id before and for the
   * entire duration of the call until the new id is returned.
   */
  default Id withTags(Map<String, String> tags) {
    Id tmp = this;
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      tmp = tmp.withTag(entry.getKey(), entry.getValue());
    }
    return tmp;
  }

  /** Return the key at the specified index. The name will be treated as position 0. */
  @Override default String getKey(int i) {
    return i == 0 ? "name" : Utils.getValue(tags(), i - 1).key();
  }

  /** Return the value at the specified index. The name will be treated as position 0. */
  @Override default String getValue(int i) {
    return i == 0 ? name() : Utils.getValue(tags(), i - 1).value();
  }

  /** Return the size, number of tags, for the id including the name. */
  @Override default int size() {
    return Utils.size(tags()) + 1;
  }

  /** Return a new tag list with only tags that match the predicate. */
  @Override default Id filter(BiPredicate<String, String> predicate) {
    List<Tag> filtered = new ArrayList<>();
    for (Tag tag : tags()) {
      if (predicate.test(tag.key(), tag.value())) {
        filtered.add(tag);
      }
    }
    return new DefaultId(name(), ArrayTagSet.create(filtered));
  }

  /** Return a new tag list with only tags with keys that match the predicate. */
  @Override default Id filterByKey(Predicate<String> predicate) {
    return filter((k, v) -> predicate.test(k));
  }

  /**
   * Create an immutable Id with the provided name. In many cases it is preferable to use
   * {@link Registry#createId(String)} instead so that the overhead for instrumentation can
   * be mostly removed when choosing to use a NoopRegistry. Using this method directly the Id
   * will always be created.
   */
  static Id create(String name) {
    return new DefaultId(name);
  }
}
