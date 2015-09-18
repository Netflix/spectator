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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default/standard implementation of the DynamicId interface.
 *
 * Created by pstout on 8/3/15.
 */
final class DefaultDynamicId implements DynamicId {
  /**
   * Utility class for sorting and deduplicating lists of tag factories.
   */
  private static final class FactorySorterAndDeduplicator {
    private static final Comparator<String> REVERSE_STRING_COMPARATOR =
      (String left, String right) -> right.compareTo(left);

    /** Map used to sort and deduplicate the presented tag factories. */
    private final Map<String, TagFactory> map = new TreeMap<>(REVERSE_STRING_COMPARATOR);

    /** Construct a new instance with the specified factories in it. */
    FactorySorterAndDeduplicator(Iterable<TagFactory> tagFactories) {
      addFactories(tagFactories);
    }

    /**
     * Adds the specified tag to the collected tags. It will overwrite any existing value
     * associated the key in the specified tag.
     *
     * @param factory
     *      The tag factory to add to the collection
     */
    void addFactory(TagFactory factory) {
      map.put(factory.name(), factory);
    }

    /**
     * Adds the tags (key, value)-pairs to the collected tags. Any values associated with the tags
     * in the map will overwrite any existing values with the same key that are already in the
     * collection.
     *
     * @param factories
     *     The set of tag factories to add.
     */
    void addFactories(Iterable<TagFactory> factories) {
      for (TagFactory factory : factories) {
          map.put(factory.name(), factory);
      }
    }

    /** Returns the sorted set of tag factories as an unmodifiable collection. */
    Collection<TagFactory> asCollection() {
      return Collections.unmodifiableCollection(map.values());
    }
  }

  /** Implementation that always produces the same tag. */
  private static class ConstantTagFactory implements TagFactory {
    private final Tag tag;

    ConstantTagFactory(Tag tag) {
      this.tag = tag;
    }

    @Override
    public String name() {
      return tag.key();
    }

    @Override
    public Tag createTag(String value) {
      return tag;
    }
  }

  /** Implementation that always returns null, which result in the tag being omitted. */
  private static final TagFactory NOOP_TAG_FACTORY = new TagFactory() {
    @Override
    public String name() {
      return "noopTagFactory";
    }

    @Override
    public Tag createTag(String value) {
        return null;
    }
  };

  private final String name;
  private final Collection<TagFactory> tagFactories;

  /**
   * Creates a new id with the specified name and collection of factories.
   *
   * @param name
   *      the name of the new id
   * @param tagFactories
   *      the possibly empty collection of factories to be attached to the new id
   * @return
   *      the newly created id
   */
  static DefaultDynamicId createWithFactories(String name, Iterable<TagFactory> tagFactories) {
    if (tagFactories == null) {
      return new DefaultDynamicId(name);
    } else {
      FactorySorterAndDeduplicator sorter = new FactorySorterAndDeduplicator(tagFactories);

      return new DefaultDynamicId(name, sorter.asCollection());
    }
  }

  /**
   * Constructs a new id with the specified name and no associated tag factories.
   */
  DefaultDynamicId(String name) {
    this(name, Collections.emptyList());
  }

  /**
   * Constructs a new id with the specified name and tag factories.
   *
   * @param name
   *      the name of the new id
   * @param tagFactories
   *      the possibly empty, immutable collection of tag factories to use
   */
  private DefaultDynamicId(String name, Collection<TagFactory> tagFactories) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.tagFactories = tagFactories;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Iterable<Tag> tags() {
    return tagFactories.stream()
      .map(factory -> factory.createTag(null))
      .filter(tag -> tag != null)
      .collect(Collectors.toList());
  }

  @Override
  public Collection<TagFactory> tagFactories() {
    return tagFactories;
  }

  @Override
  public Id withTag(String k, String v) {
    return withTagFactory(new ConstantTagFactory(new TagList(k, v)));
  }

  @Override
  public Id withTag(Tag t) {
    return withTagFactory(new ConstantTagFactory(t));
  }

  @Override
  public Id withTags(Iterable<Tag> tags) {
    return createNewId(sorter -> tags.forEach(tag -> sorter.addFactory(new ConstantTagFactory(tag))));
  }

  @Override
  public Id withTags(Map<String, String> tags) {
    return createNewId(sorter ->
      tags.forEach((key, value) -> sorter.addFactory(new ConstantTagFactory(new TagList(key, value)))));
  }

  @Override
  public DynamicId withTagName(String tagName) {
    return withTagFactory(NOOP_TAG_FACTORY);
  }

  @Override
  public DynamicId withTagNames(Iterable<String> tagNames) {
    return createNewId(sorter -> tagNames.forEach(tagName -> sorter.addFactory(NOOP_TAG_FACTORY)));
  }

  @Override
  public DynamicId withTagFactory(TagFactory factory) {
    if (tagFactories.isEmpty()) {
      return new DefaultDynamicId(name, Collections.singleton(factory));
    } else {
      return createNewId(sorter -> sorter.addFactory(factory));
    }
  }

  @Override
  public DynamicId withTagFactories(Iterable<TagFactory> factories) {
    return createNewId(sorter -> sorter.addFactories(factories));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultDynamicId that = (DefaultDynamicId) o;

    return name.equals(that.name) && tagFactories.equals(that.tagFactories);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + tagFactories.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name);
    if (!tagFactories.isEmpty()) {
      buf.append(':').append(tags());
    }
    return buf.toString();
  }

  /**
   * Creates a new dynamic id based on the factories associated with this id
   * and whatever additions are made by the specified consumer.
   *
   * @param consumer
   *      lambda that can update the sorter with additional tag factories
   * @return
   *      the newly created id
   */
  private DynamicId createNewId(Consumer<FactorySorterAndDeduplicator> consumer) {
    FactorySorterAndDeduplicator sorter = new FactorySorterAndDeduplicator(tagFactories);

    consumer.accept(sorter);
    return new DefaultDynamicId(name, sorter.asCollection());
  }
}
