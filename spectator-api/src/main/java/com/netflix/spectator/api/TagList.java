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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * A tag list implemented as a singly linked list.  The contents of the list are maintained in sorted order by
 * key with no duplicates.
 */
final class TagList implements Iterable<Tag>, Tag {
  /**
   * Utility class for sorting and deduplicating lists of tags.
   */
  private static class TagSorterAndDeduplicator {
    private static final Comparator<String> REVERSE_STRING_COMPARATOR = new Comparator<String>() {
      @Override
      public int compare(String left, String right) {
        return right.compareTo(left);
      }
    };

    /** Map used to sort and deduplicate the presented tags. */
    private final Map<String, Tag> map;

    /**
     * Construct a new instance with no tags in it.
     */
    TagSorterAndDeduplicator() {
      map  = new TreeMap<>(REVERSE_STRING_COMPARATOR);
    }

    /**
     * Adds the specified tag to the collected tags.  It will overwrite any existing value associated the key
     * in the specified tag.
     *
     * @param tag the tag to add to the collection
     */
    void addTag(Tag tag) {
      map.put(tag.key(), tag);
    }

    /**
     * Adds the tags in the iterable to the collected tags.  Any values associated with the tags in the iterable
     * will overwrite any existing values with the same key that are already in the collection.
     *
     * @param tags the set of tags to add
     */
    void addTags(Iterable<Tag> tags) {
      for (Tag t : tags) {
        map.put(t.key(), t);
      }
    }

    /**
     * Adds the tags (key, value)-pairs to the collected tags.  Any values associated with the tags in the map
     * will overwrite any existing values with the same key that are already in the collection.
     *
     * @param tags the set of tags to add
     */
    void addTags(Map<String, String> tags) {
      for (Map.Entry<String, String> t : tags.entrySet()) {
        map.put(t.getKey(), new TagList(t.getKey(), t.getValue()));
      }
    }

    /**
     * @return the sorted set of deduplicated tags
     */
    Iterable<Tag> sortedTags() {
      return map.values();
    }
  }

  private final String key;
  private final String value;
  private final TagList next;
  private final int hc;

  /**
   * Create a new instance with a single pair in the list.
   */
  TagList(String key, String value) {
    this(key, value, EMPTY);
  }

  /**
   * Create a new instance with a new tag prepended to the list {@code next}.  Any entries in next should have keys
   * that are lexicographically after the specified key.
   */
  private TagList(String key, String value, TagList next) {
    this.key = Preconditions.checkNotNull(key, "key");
    this.value = Preconditions.checkNotNull(value, "value");
    this.next = next;
    this.hc = 31 * (key.hashCode() + value.hashCode() + (next == null ? 23 : next.hashCode()));
  }

  @Override public String key() {
    return key;
  }

  @Override public String value() {
    return value;
  }

  @Override public Iterator<Tag> iterator() {
    final TagList root = this;
    return new Iterator<Tag>() {
      private TagList current = root;

      public boolean hasNext() {
        return current != EMPTY;
      }

      public Tag next() {
        if (current == EMPTY) {
          throw new NoSuchElementException();
        }
        Tag tmp = current;
        current = current.next;
        return tmp;
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof TagList)) return false;
    TagList other = (TagList) obj;
    return key.equals(other.key) && value.equals(other.value)
      && (next == other.next || (next != null && next.equals(other.next)));
  }

  /**
   * This object is immutable and the hash code is precomputed in the constructor. The id object
   * is typically created to lookup a Meter based on dynamic dimensions so we assume that it is
   * highly likely that the hash code method will be called and that it could be in a fairly
   * high volume execution path.
   *
   * {@inheritDoc}
   */
  @Override public int hashCode() {
    return hc;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    TagList cur = next;

    buf.append(key).append('=').append(value);
    while (cur != null) {
      buf.append(":").append(cur.key()).append("=").append(cur.value());
      cur = cur.next;
    }
    return buf.toString();
  }

  /**
   * Produces a list with with the specified tag merged with the existing values in this list.  If the key of the
   * specified tag matches an existing list entry, then the value of the specified tag will replace the existing
   * value.
   *
   * @param tag th possibly null tag to merge into the list
   * @return A tag list with merged values.
   */
  TagList mergeTag(Tag tag) {
    if (tag == null) {
      return this;
    } else if (next == null) {
      int comparison = key.compareTo(tag.key());

      if (comparison == 0) { // Same key, so the specified value replaces the current value.
        return new TagList(tag.key(), tag.value(), EMPTY);
      } else if (comparison < 0) { // The key in this list is before the key in the specified list.
        return new TagList(key, value, new TagList(tag.key(), tag.value(), EMPTY));
      } else { // The key in this list is after the key in the specified list.
        return new TagList(tag.key(), tag.value(), this);
      }
    } else {
      // Is it possible to optimize this case so as to reuse the tail of the existing TagList?
      TagSorterAndDeduplicator entries = new TagSorterAndDeduplicator();

      entries.addTags(this);
      entries.addTag(tag);

      return createFromSortedTags(entries.sortedTags());
    }
  }

  /**
   * Produces a list with the tags from this list merged with the tags in the specified list.  For any keys present in
   * both lists, the value from the specified list will replace the existing value.
   *
   * @param tags
   *     A set of tags to merge.
   * @return
   *     A tag list with the merged values.  Based on the inputs the result may be this, tags, or a new object.
   */
  TagList mergeList(Iterable<Tag> tags) {
    if (tags == null) {
      return this;
    }

    Iterator<Tag> iter = tags.iterator();

    if (iter.hasNext()) {
      Tag firstTag = iter.next();

      if (iter.hasNext()) {
        // Iterator has multiple entries so we need to sort them and remove any duplicates.
        TagSorterAndDeduplicator entries = new TagSorterAndDeduplicator();

        entries.addTags(this);
        entries.addTags(tags);

        return createFromSortedTags(entries.sortedTags());
      } else {
        // Single entry iterator.
        return mergeTag(firstTag);
      }
    } else {
      // Empty iterator
      return this;
    }
  }

  /**
   * Produces a list with the tags from this list merged with the tags in the specified list.  For any keys present in
   * both lists, the value from the specified list will replace the existing value.
   *
   * @param tags
   *     A set of tags to merge.
   * @return
   *     A tag list with the merged values.  Based on the inputs the result may be this or a new object.
   */
  TagList mergeMap(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return this;
    }

    if (tags.size() == 1) {
      Map.Entry<String, String> entry = tags.entrySet().iterator().next();

      return mergeTag(new TagList(entry.getKey(), entry.getValue(), EMPTY));
    } else {
        // Iterator has multiple entries so we need to sort them and remove any duplicates.
        TagSorterAndDeduplicator entries = new TagSorterAndDeduplicator();

        entries.addTags(this);
        entries.addTags(tags);

      return createFromSortedTags(entries.sortedTags());
    }
  }

  /**
   * Create a new tag list from the key/value pairs in the iterable.
   *
   * @param tags
   *     Set of key/value pairs.
   * @return
   *     New tag list with a copy of the data.
   */
  static TagList create(Iterable<Tag> tags) {
    if (tags == EMPTY || tags instanceof TagList) {
      return (TagList) tags;
    } else {
      Iterator<Tag> iter = tags.iterator();

      if (iter.hasNext()) {
        Tag firstTag = iter.next();

        if (iter.hasNext()) {
          // Iterator has multiple entries so we need to sort them and remove any duplicates.
          TagSorterAndDeduplicator entries = new TagSorterAndDeduplicator();

          entries.addTags(tags);

          return createFromSortedTags(entries.sortedTags());
        } else {
          // Single entry iterator.
          return new TagList(firstTag.key(), firstTag.value(), EMPTY);
        }
      } else {
        // Empty iterator
        return EMPTY;
      }
    }
  }

  /**
   * Create a new tag list from the key/value pairs in the map.
   *
   * @param tags
   *     Set of key/value pairs.
   * @return
   *     New tag list with a copy of the data.
   */
  static TagList create(Map<String, String> tags) {
    TagList head = EMPTY;

    if (tags.size() >= 2) {
      TagSorterAndDeduplicator entries = new TagSorterAndDeduplicator();

      for (Map.Entry<String, String> t : tags.entrySet()) {
        entries.addTag(new TagList(t.getKey(), t.getValue()));
      }
      head = createFromSortedTags(entries.sortedTags());
    } else {
      for (Map.Entry<String, String> t : tags.entrySet()) {
        head = new TagList(t.getKey(), t.getValue(), head);
      }
    }

    return head;
  }

  /**
   * Create a tag list from a sorted, deduplicated list of tags.  The TagList is created with the entries in the
   * reverse order of the entries in the provided argument.
   *
   * @param sortedTags the sorted collection of tags to use to create the list
   * @return the newly constructed tag list or {@code EMPTY} if the iterable contains no entries
   */
  private static TagList createFromSortedTags(Iterable<Tag> sortedTags) {
    TagList head = EMPTY;

    for (Tag t : sortedTags) {
      head = new TagList(t.key(), t.value(), head);
    }
    return head;
  }

  /** Empty tag list. */
  static final TagList EMPTY = null;
}
