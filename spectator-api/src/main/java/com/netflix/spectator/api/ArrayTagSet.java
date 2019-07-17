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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * An immutable set of tags sorted by the tag key.
 */
final class ArrayTagSet implements TagList {

  private static final Comparator<Tag> TAG_COMPARATOR = (t1, t2) -> t1.key().compareTo(t2.key());

  /** Empty tag set. */
  static final ArrayTagSet EMPTY = new ArrayTagSet(new String[0]);

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
    return (tags instanceof ArrayTagSet) ? (ArrayTagSet) tags : EMPTY.addAll(tags);
  }

  /** Create a new tag set. */
  static ArrayTagSet create(Map<String, String> tags) {
    return EMPTY.addAll(tags);
  }

  private final String[] tags;
  private final int length;

  private int cachedHashCode;

  private ArrayTagSet(String[] tags) {
    this(tags, tags.length);
  }

  private ArrayTagSet(String[] tags, int length) {
    if (tags.length % 2 != 0) {
      throw new IllegalArgumentException("length of tags array must be even");
    }
    if (length > tags.length) {
      throw new IllegalArgumentException("length cannot be larger than tags array");
    }
    this.tags = tags;
    this.length = length;
    this.cachedHashCode = 0;
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
    if (length == 0) {
      return new ArrayTagSet(new String[] {tag.key(), tag.value()});
    } else {
      String[] newTags = new String[length + 2];
      String k = tag.key();
      int i = 0;
      for (; i < length && tags[i].compareTo(k) < 0; i += 2) {
        newTags[i] = tags[i];
        newTags[i + 1] = tags[i + 1];
      }
      if (i < length && tags[i].equals(k)) {
        // Override
        newTags[i++] = tag.key();
        newTags[i++] = tag.value();
        System.arraycopy(tags, i, newTags, i, length - i);
        i = length;
      } else {
        // Insert
        newTags[i] = tag.key();
        newTags[i + 1] = tag.value();
        System.arraycopy(tags, i, newTags, i + 2, length - i);
        i = newTags.length;
      }
      return new ArrayTagSet(newTags, i);
    }
  }

  /** Add a collection of tags to the set. */
  ArrayTagSet addAll(Iterable<Tag> ts) {
    if (ts instanceof Collection) {
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
    } else if (ts instanceof ConcurrentMap) {
      // Special case ConcurrentMaps to avoid propagating errors if there is a bug in the caller
      // and the map is mutated while being added:
      // https://github.com/Netflix/spectator/issues/733
      List<Tag> data = new ArrayList<>(ts.size());
      for (Map.Entry<String, String> entry : ts.entrySet()) {
        data.add(new BasicTag(entry.getKey(), entry.getValue()));
      }
      return addAll(data);
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
    } else if (length == 0) {
      Arrays.sort(ts, 0, tsLength, TAG_COMPARATOR);
      int len = dedup(ts, 0, ts, 0, tsLength);
      return new ArrayTagSet(toStringArray(ts, len));
    } else {
      String[] newTags = new String[(length + tsLength) * 2];
      Arrays.sort(ts, 0, tsLength, TAG_COMPARATOR);
      int newLength = merge(newTags, tags, length, ts, tsLength);
      return new ArrayTagSet(newTags, newLength);
    }
  }

  private String[] toStringArray(Tag[] ts, int length) {
    String[] strs = new String[length * 2];
    for (int i = 0; i < length; ++i) {
      strs[2 * i] = ts[i].key();
      strs[2 * i + 1] = ts[i].value();
    }
    return strs;
  }

  /**
   * Merge and dedup any entries in {@code ts} that have the same key. The last entry
   * with a given key will get selected.
   */
  private int merge(String[] dst, String[] srcA, int lengthA, Tag[] srcB, int lengthB) {
    int i = 0;
    int ai = 0;
    int bi = 0;

    while (ai < lengthA && bi < lengthB) {
      final String ak = srcA[ai];
      final String av = srcA[ai + 1];
      Tag b = srcB[bi];
      int cmp = ak.compareTo(b.key());
      if (cmp < 0) {
        dst[i++] = ak;
        dst[i++] = av;
        ai += 2;
      } else if (cmp > 0) {
        dst[i++] = b.key();
        dst[i++] = b.value();
        ++bi;
      } else {
        // Newer tags should override, use source B if there are duplicate keys.
        // If source B has duplicates, then use the last value for the given key.
        int j = bi + 1;
        for (; j < lengthB && ak.equals(srcB[j].key()); ++j) {
          b = srcB[j];
        }
        dst[i++] = b.key();
        dst[i++] = b.value();
        bi = j;
        ai += 2; // Ignore
      }
    }

    if (ai < lengthA) {
      System.arraycopy(srcA, ai, dst, i, lengthA - ai);
      i += lengthA - ai;
    } else if (bi < lengthB) {
      i = dedup(srcB, bi, dst, i, lengthB - bi);
    }

    return i;
  }

  /**
   * Dedup any entries in {@code ts} that have the same key. The last entry with a given
   * key will get selected. Input data must already be sorted by the tag key. Returns the
   * length of the overall deduped array.
   */
  private int dedup(Tag[] src, int ss, Tag[] dst, int ds, int len) {
    if (len == 0) {
      return ds;
    } else {
      dst[ds] = src[ss];
      String k = src[ss].key();
      int j = ds;
      final int e = ss + len;
      for (int i = ss + 1; i < e; ++i) {
        if (k.equals(src[i].key())) {
          dst[j] = src[i];
        } else {
          k = src[i].key();
          dst[++j] = src[i];
        }
      }
      return j + 1;
    }
  }

  /**
   * Dedup any entries in {@code ts} that have the same key. The last entry with a given
   * key will get selected. Input data must already be sorted by the tag key. Returns the
   * length of the overall deduped array.
   */
  private int dedup(Tag[] src, int ss, String[] dst, int ds, int len) {
    if (len == 0) {
      return ds;
    } else {
      String k = src[ss].key();
      dst[ds] = k;
      dst[ds + 1] = src[ss].value();
      int j = ds;
      final int e = ss + len;
      for (int i = ss + 1; i < e; ++i) {
        if (k.equals(src[i].key())) {
          dst[j] = src[i].key();
          dst[j + 1] = src[i].value();
        } else {
          j += 2; // Not deduping, skip over previous entry
          k = src[i].key();
          dst[j] = k;
          dst[j + 1] = src[i].value();
        }
      }
      return j + 2;
    }
  }

  /** Return the key at the specified position. */
  @Override public String getKey(int i) {
    return tags[i * 2];
  }

  /** Return the value at the specified position. */
  @Override public String getValue(int i) {
    return tags[i * 2 + 1];
  }

  /** Return the current size of this tag set. */
  @Override public int size() {
    return length / 2;
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
    for (int i = 0; i < length; i += 2) {
      builder.append(':').append(tags[i]).append('=').append(tags[i + 1]);
    }
    return builder.toString();
  }
}
