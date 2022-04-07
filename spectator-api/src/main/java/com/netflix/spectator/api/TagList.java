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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Base type for a collection of tags. Allows access to the keys and values without allocations
 * if the underlying implementation does not store them as Tag objects.
 */
public interface TagList extends Iterable<Tag>, Comparable<TagList> {

  /** Create a new tag list from a map. */
  static TagList create(Map<String, String> tags) {
    return ArrayTagSet.create(tags);
  }

  /** Return the key at the specified index. */
  String getKey(int i);

  /** Return the value at the specified index. */
  String getValue(int i);

  /** Return the number of tags in the list. */
  int size();

  /** Return the tag at the specified index. */
  default Tag getTag(int i) {
    return Tag.of(getKey(i), getValue(i));
  }

  /** Return a new tag list with only tags that match the predicate. */
  default TagList filter(BiPredicate<String, String> predicate) {
    final int n = size();
    List<Tag> result = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      final String k = getKey(i);
      final String v = getValue(i);
      if (predicate.test(k, v)) {
        result.add(Tag.of(k, v));
      }
    }
    return ArrayTagSet.create(result);
  }

  /** Return a new tag list with only tags with keys that match the predicate. */
  default TagList filterByKey(Predicate<String> predicate) {
    return filter((k, v) -> predicate.test(k));
  }

  /** Apply the consumer function for each tag in the list. */
  default void forEach(BiConsumer<String, String> consumer) {
    final int n = size();
    for (int i = 0; i < n; ++i) {
      consumer.accept(getKey(i), getValue(i));
    }
  }

  @Override default Iterator<Tag> iterator() {
    final int length = size();
    return new Iterator<Tag>() {
      private int i = 0;

      @Override public boolean hasNext() {
        return i < length;
      }

      @Override public Tag next() {
        if (i >= length) {
          throw new NoSuchElementException("next called after end of iterator");
        }
        return getTag(i++);
      }
    };
  }

  @Override default int compareTo(TagList other) {
    if (this == other) {
      return 0;
    } else {
      int n = Math.min(size(), other.size());
      for (int i = 0; i < n; ++i) {
        // Check key
        int cmp = getKey(i).compareTo(other.getKey(i));
        if (cmp != 0)
          return cmp;

        // Check value
        cmp = getValue(i).compareTo(other.getValue(i));
        if (cmp != 0)
          return cmp;
      }

      // If they are equal up to this point, then remaining items in one list should
      // put it after the other
      return size() - other.size();
    }
  }
}
