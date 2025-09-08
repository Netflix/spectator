/*
 * Copyright 2014-2025 Netflix, Inc.
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

import java.util.Arrays;

/**
 * Builder for creating TagList instances efficiently. This builder can be reused multiple times
 * and provides optimizations for sorted tag insertion to avoid unnecessary sorting operations
 * later. A builder instance is not thread safe and should only be used from a single thread at
 * a time. The user is responsible for managing how to reuse builder instances to avoid initial
 * allocation of the builder each time.
 *
 * <p>The builder tracks whether tags are added in sorted order and passes this information
 * to the underlying TagList implementation to optimize performance. For best performance,
 * add tags in lexicographically sorted order by key.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TagListBuilder builder = TagListBuilder.create();
 * TagList tags = builder
 *     .add("app", "myapp")
 *     .add("env", "prod")
 *     .add("region", "us-east-1")
 *     .buildAndReset();
 * }</pre>
 *
 * <p>The builder can be reused after calling {@link #buildAndReset()}:</p>
 * <pre>{@code
 * TagList tags1 = builder.add("key1", "value1").buildAndReset();
 * TagList tags2 = builder.add("key2", "value2").buildAndReset();
 * }</pre>
 */
public final class TagListBuilder {

  /**
   * Creates a new TagListBuilder instance.
   *
   * @return a new builder instance
   */
  public static TagListBuilder create() {
    return new TagListBuilder(ArrayTagSet.EMPTY);
  }

  /**
   * Creates a new TagListBuilder instance with a set of base tags that will be included
   * in all TagLists built by this builder. The base tags are automatically added when
   * the builder is created and after each {@link #buildAndReset()} call.
   *
   * @param baseTags the base tags to include in all built TagLists, must not be null
   * @return a new builder instance with the specified base tags
   */
  public static TagListBuilder create(TagList baseTags) {
    return new TagListBuilder(Preconditions.checkNotNull(baseTags, "baseTags"));
  }

  private final int basePos;
  private final String baseKey;
  private final boolean baseSorted;

  private String[] tags;
  private int pos;

  private String lastKey;
  private boolean sorted;

  /**
   * Creates a new builder with initial capacity for 10 key-value pairs.
   */
  TagListBuilder(TagList baseTags) {
    int n = baseTags.size() * 2 + 20;
    tags = new String[n];
    pos = 0;
    lastKey = null;
    sorted = true;

    // Add base tags and keep track of state for the reset
    add(baseTags);
    basePos = pos;
    baseKey = lastKey;
    baseSorted = sorted;
  }

  /**
   * Returns whether the tags added so far are in sorted order by key.
   * This is used internally to optimize TagList creation.
   *
   * @return true if tags are sorted, false otherwise
   */
  boolean isSorted() {
    return sorted;
  }

  /**
   * Doubles the capacity of the internal array if needed.
   */
  private void resizeIfNeeded() {
    if (pos >= tags.length) {
      tags = Arrays.copyOf(tags, tags.length * 2);
    }
  }

  /**
   * Checks if the given key is lexicographically greater than the last added key.
   *
   * @param key the key to check
   * @return true if key is greater than the last key, or if this is the first key
   */
  private boolean isGreaterThanLastKey(String key) {
    return lastKey == null || lastKey.compareTo(key) < 0;
  }

  /**
   * Adds all tags from the provided TagList to this builder.
   *
   * @param tags the TagList containing tags to add, must not be null
   * @return this builder for method chaining
   */
  public TagListBuilder add(TagList tags) {
    final int n = tags.size();
    for (int i = 0; i < n; ++i) {
      add(tags.getKey(i), tags.getValue(i));
    }
    return this;
  }

  /**
   * Adds a key-value pair to the builder. Null keys or values are ignored.
   *
   * <p>For optimal performance, add tags in lexicographically sorted order by key.
   * The builder tracks sort order and passes this information to the underlying
   * TagList implementation to avoid unnecessary sorting operations.</p>
   *
   * @param key the tag key, must not be null
   * @param value the tag value, must not be null
   * @return this builder for method chaining
   */
  public TagListBuilder add(String key, String value) {
    if (key != null && value != null) {
      resizeIfNeeded();
      sorted = sorted && isGreaterThanLastKey(key);
      lastKey = key;
      tags[pos++] = key;
      tags[pos++] = value;
    }
    return this;
  }

  /**
   * Adds a Tag object to the builder. Null keys or values are ignored.
   *
   * @param tag the tag to add, must not be null and must have non-null key and value
   * @return this builder for method chaining
   */
  public TagListBuilder add(Tag tag) {
    return add(tag.key(), tag.value());
  }

  /**
   * Resets the builder to its initial empty state, allowing it to be reused.
   */
  public void reset() {
    pos = basePos;
    lastKey = baseKey;
    sorted = baseSorted;
  }

  /**
   * Builds a TagList from the accumulated tags and resets the builder for reuse.
   *
   * @return a new TagList containing all the added tags
   */
  public TagList buildAndReset() {
    String[] copy = Arrays.copyOf(tags, pos);
    TagList ts = ArrayTagSet.EMPTY.addAll(copy, pos, sorted, sorted);
    reset();
    return ts;
  }
}
