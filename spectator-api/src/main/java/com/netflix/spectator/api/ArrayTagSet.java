/**
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
package com.netflix.spectator.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * An immutable set of tags sorted by the tag key.
 */
final class ArrayTagSet implements Iterable<Tag> {

  private static final Comparator<Tag> TAG_COMPARATOR = (t1, t2) -> t1.key().compareTo(t2.key());

  /** Empty tag set. */
  static final ArrayTagSet EMPTY = new ArrayTagSet(new Tag[0]);

  /** Create a new tag set. */
  static ArrayTagSet create(String... tags) {
    return EMPTY.addAll(tags);
  }

  /** Create a new tag set. */
  static ArrayTagSet create(Tag... tags) {
    return EMPTY.addAll(tags);
  }

  /** Create a new tag set. */
  static ArrayTagSet create(Iterable<Tag> tags) {
    return EMPTY.addAll(tags);
  }

  /** Create a new tag set. */
  static ArrayTagSet create(Map<String, String> tags) {
    return EMPTY.addAll(tags);
  }

  private final Tag[] tags;
  private final int length;

  private ArrayTagSet(Tag[] tags) {
    this(tags, tags.length);
  }

  private ArrayTagSet(Tag[] tags, int length) {
    if (length > tags.length) {
      throw new IllegalArgumentException("length cannot be larger than tags array");
    }
    this.tags = tags;
    this.length = length;
  }

  @Override public Iterator<Tag> iterator() {
    return new Iterator<Tag>() {
      private int i = 0;

      @Override public boolean hasNext() {
        return i < length;
      }

      @Override public Tag next() {
        if (i >= length) {
          throw new NoSuchElementException("next called after end of iterator");
        }
        return tags[i++];
      }
    };
  }

  /** Check if this set is empty. */
  boolean isEmpty() {
    return length == 0;
  }

  /** Add a new tag to the set. */
  ArrayTagSet add(String k, String v) {
    return add(new BasicTag(k, v));
  }

  /** Add a new tag to the set. */
  ArrayTagSet add(Tag tag) {
    Tag[] newTags;
    int pos = Arrays.binarySearch(tags, 0, length, tag, TAG_COMPARATOR);
    if (pos < 0) {
      // Not found in list
      newTags = new Tag[length + 1];
      int i = -pos - 1;
      if (i == 0) { // Prepend
        System.arraycopy(tags, 0, newTags, 1, length);
      } else if (i == length) { // Append
        System.arraycopy(tags, 0, newTags, 0, length);
      } else { // Insert
        System.arraycopy(tags, 0, newTags, 0, i);
        System.arraycopy(tags, i, newTags, i + 1, length - i);
      }
      newTags[i] = BasicTag.convert(tag);
    } else {
      // Override
      newTags = new Tag[length];
      System.arraycopy(tags, 0, newTags, 0, length);
      newTags[pos] = BasicTag.convert(tag);
    }
    return new ArrayTagSet(newTags);
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(Iterable<Tag> ts) {
    if (ts instanceof ArrayTagSet) {
      ArrayTagSet data = (ArrayTagSet) ts;
      return addAll(data.tags, data.length);
    } else if (ts instanceof Collection) {
      Collection<Tag> data = (Collection<Tag>) ts;
      if (data.isEmpty()) {
        return this;
      } else {
        Tag[] newTags = new Tag[length + data.size()];
        System.arraycopy(tags, 0, newTags, 0, length);
        int i = 0;
        for (Tag t : data) {
          newTags[length + i] = BasicTag.convert(t);
          ++i;
        }
        return dedup(newTags);
      }
    } else {
      List<Tag> data = new ArrayList<>();
      for (Tag t : ts) {
        data.add(t);
      }
      return addAll(data);
    }
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(Map<String, String> ts) {
    if (ts.isEmpty()) {
      return this;
    } else {
      Tag[] newTags = new Tag[length + ts.size()];
      System.arraycopy(tags, 0, newTags, 0, length);
      int i = length;
      for (Map.Entry<String, String> entry : ts.entrySet()) {
        newTags[i++] = new BasicTag(entry.getKey(), entry.getValue());
      }
      return dedup(newTags);
    }
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(String[] ts) {
    if (ts.length % 2 != 0) {
      throw new IllegalArgumentException("array length must be even, (length=" + ts.length + ")");
    }

    if (ts.length == 0) {
      return this;
    } else {
      int tsLength = ts.length / 2;
      Tag[] newTags = new Tag[length + tsLength];
      System.arraycopy(tags, 0, newTags, 0, length);
      for (int i = 0; i < tsLength; ++i) {
        final int j = i * 2;
        newTags[length + i] = new BasicTag(ts[j], ts[j + 1]);
      }
      return dedup(newTags);
    }
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(Tag[] ts) {
    return addAll(ts, ts.length);
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(Tag[] ts, int tsLength) {
    if (tsLength == 0) {
      return this;
    } else {
      Tag[] newTags = new Tag[length + tsLength];
      System.arraycopy(tags, 0, newTags, 0, length);
      for (int i = 0; i < tsLength; ++i) {
        newTags[length + i] = BasicTag.convert(ts[i]);
      }
      return dedup(newTags);
    }
  }

  /**
   * Dedup any entries in {@code ts} that have the same key. The last entry with a given
   * key will get selected.
   */
  private ArrayTagSet dedup(Tag[] ts) {
    // It is important for this sort to be stable for the override behavior to work
    // correctly.
    Arrays.sort(ts, TAG_COMPARATOR);

    String k = ts[0].key();
    int j = 0;
    for (int i = 0; i < ts.length; ++i) {
      if (k.equals(ts[i].key())) {
        ts[j] = ts[i];
      } else {
        k = ts[i].key();
        ts[++j] = ts[i];
      }
    }
    return new ArrayTagSet(ts, j + 1);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrayTagSet other = (ArrayTagSet) o;
    if (length != other.length) return false;

    for (int i = 0; i < length; ++i) {
      if (!tags[i].equals(other.tags[i])) return false;
    }
    return true;
  }

  @Override public int hashCode() {
    int result = length;
    for (int i = 0; i < length; ++i) {
      result = 31 * result + tags[i].hashCode();
    }
    return result;
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; ++i) {
      builder.append(':').append(tags[i].key()).append('=').append(tags[i].value());
    }
    return builder.toString();
  }
}
