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
package com.netflix.spectator.api;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

/**
 * Base type for a collection of tags. Allows access to the keys and values without allocations
 * if the underlying implementation does not store them as Tag objects.
 */
public interface TagList extends Iterable<Tag> {

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
}
