/*
 * Copyright 2014-2017 Netflix, Inc.
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

  private int cachedHashCode;

  private ArrayTagSet(Tag[] tags) {
    this(tags, tags.length);
  }

  private ArrayTagSet(Tag[] tags, int length) {
    if (length > tags.length) {
      throw new IllegalArgumentException("length cannot be larger than tags array");
    }
    this.tags = tags;
    this.length = length;
    this.cachedHashCode = 0;
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
  @SuppressWarnings("PMD.AvoidArrayLoops")
  ArrayTagSet add(Tag tag) {
    Tag newTag = BasicTag.convert(tag);
    if (length == 0) {
      return new ArrayTagSet(new Tag[] {newTag});
    } else {
      Tag[] newTags = new Tag[length + 1];
      String k = newTag.key();
      int i = 0;
      for (; i < length && tags[i].key().compareTo(k) < 0; ++i) {
        newTags[i] = tags[i];
      }
      if (i < length && tags[i].key().equals(k)) {
        // Override
        newTags[i++] = newTag;
        System.arraycopy(tags, i, newTags, i, length - i);
        i = length;
      } else {
        // Insert
        newTags[i] = newTag;
        System.arraycopy(tags, i, newTags, i + 1, length - i);
        i = newTags.length;
      }
      return new ArrayTagSet(newTags, i);
    }
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
        Tag[] newTags = new Tag[data.size()];
        int i = 0;
        for (Tag t : data) {
          newTags[i] = BasicTag.convert(t);
          ++i;
        }
        return addAll(newTags);
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
      Tag[] newTags = new Tag[ts.size()];
      int i = 0;
      for (Map.Entry<String, String> entry : ts.entrySet()) {
        newTags[i++] = new BasicTag(entry.getKey(), entry.getValue());
      }
      return addAll(newTags);
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
      Tag[] newTags = new Tag[tsLength];
      for (int i = 0; i < tsLength; ++i) {
        final int j = i * 2;
        newTags[i] = new BasicTag(ts[j], ts[j + 1]);
      }
      return addAll(newTags, tsLength);
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
      Arrays.sort(ts, 0, tsLength, TAG_COMPARATOR);
      int newLength = merge(newTags, tags, length, ts, tsLength);
      return new ArrayTagSet(newTags, newLength);
    }
  }

  /**
   * Merge and dedup any entries in {@code ts} that have the same key. The last entry
   * with a given key will get selected.
   */
  private int merge(Tag[] dst, Tag[] srcA, int lengthA, Tag[] srcB, int lengthB) {
    int i = 0;
    int ai = 0;
    int bi = 0;

    while (ai < lengthA && bi < lengthB) {
      Tag a = srcA[ai];
      Tag b = srcB[bi];
      int cmp = a.key().compareTo(b.key());
      if (cmp < 0) {
        dst[i++] = a;
        ++ai;
      } else if (cmp > 0) {
        dst[i++] = BasicTag.convert(b);
        ++bi;
      } else {
        // Newer tags should override, use source B if there are duplicate keys.
        // If source B has duplicates, then use the last value for the given key.
        int j = bi + 1;
        for (; j < lengthB && a.key().equals(srcB[j].key()); ++j) {
          b = srcB[j];
        }
        dst[i++] = BasicTag.convert(b);
        bi = j;
        ++ai; // Ignore
      }
    }

    if (ai < lengthA) {
      System.arraycopy(srcA, ai, dst, i, lengthA - ai);
      i += lengthA - ai;
    } else if (bi < lengthB) {
      System.arraycopy(srcB, bi, dst, i, lengthB - bi);
      i = dedup(dst, i, i + lengthB - bi);
    }

    return i;
  }

  /**
   * Dedup any entries in {@code ts} that have the same key. The last entry with a given
   * key will get selected. Input data must already be sorted by the tag key. Returns the
   * length of the overall deduped array.
   */
  private int dedup(Tag[] ts, int s, int e) {
    String k = ts[s].key();
    int j = s;
    for (int i = s; i < e; ++i) {
      if (k.equals(ts[i].key())) {
        ts[j] = BasicTag.convert(ts[i]);
      } else {
        k = ts[i].key();
        ts[++j] = BasicTag.convert(ts[i]);
      }
    }
    return j + 1;
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
    if (cachedHashCode == 0) {
      int result = length;
      for (int i = 0; i < length; ++i) {
        result = 31 * result + tags[i].hashCode();
      }
      cachedHashCode = result;
    }
    return cachedHashCode;
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; ++i) {
      builder.append(':').append(tags[i].key()).append('=').append(tags[i].value());
    }
    return builder.toString();
  }
}
