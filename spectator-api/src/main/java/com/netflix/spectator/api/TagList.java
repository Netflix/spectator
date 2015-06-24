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

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A tag list implemented as a singly linked list. Doesn't automatically dedup keys but supports
 * a cheap prepend at the call site to allow for inexpensive dynamic ids.
 */
public final class TagList implements Iterable<Tag>, Tag {

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
   * Create a new instance with a new tag prepended to the list {@code next}.
   */
  TagList(String key, String value, TagList next) {
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

  /**
   * Create a new list with the tags prepended to this list.
   *
   * @param tags
   *     A set of tags to prepend.
   * @return
   *     New tag list with the tags prepended.
   */
  TagList prepend(Iterable<Tag> tags) {
    TagList head = this;
    for (Tag t : tags) {
      head = new TagList(t.key(), t.value(), head);
    }
    return head;
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
    if (tags instanceof TagList) {
      return (TagList) tags;
    } else {
      TagList head = EMPTY;
      for (Tag t : tags) {
        head = new TagList(t.key(), t.value(), head);
      }
      return head;
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
    for (Map.Entry<String, String> t : tags.entrySet()) {
      head = new TagList(t.getKey(), t.getValue(), head);
    }
    return head;
  }

  /** Empty tag list. */
  static final TagList EMPTY = null;
}
