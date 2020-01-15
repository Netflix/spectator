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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.Preconditions;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default/standard implementation of the PlaceholderId interface.
 */
final class DefaultPlaceholderId implements PlaceholderId {
  /**
   * Utility class for sorting and deduplicating lists of tag factories.
   */
  private static final class FactorySorterAndDeduplicator {
    /** Map used to sort and deduplicate the presented tag factories. */
    private final Map<String, TagFactory> map = new TreeMap<>();

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

  private final String name;
  private final Collection<TagFactory> tagFactories;
  private final Registry registry;

  /**
   * Creates a new id with the specified name and collection of factories.
   *
   * @param name
   *      the name of the new id
   * @param tagFactories
   *      the possibly empty collection of factories to be attached to the new id
   * @param registry
   *      the registry to use when resolving to an {@link Id}
   * @return
   *      the newly created id
   */
  static DefaultPlaceholderId createWithFactories(String name, Iterable<TagFactory> tagFactories, Registry registry) {
    if (tagFactories == null) {
      return new DefaultPlaceholderId(name, registry);
    } else {
      FactorySorterAndDeduplicator sorter = new FactorySorterAndDeduplicator(tagFactories);

      return new DefaultPlaceholderId(name, sorter.asCollection(), registry);
    }
  }

  /**
   * Constructs a new id with the specified name and no associated tag factories.
   */
  DefaultPlaceholderId(String name, Registry registry) {
    this(name, Collections.emptyList(), registry);
  }

  /**
   * Constructs a new id with the specified name and tag factories.
   *  @param name
   *      the name of the new id
   * @param tagFactories
   *      the possibly empty collection of factories to be attached to the new id
   * @param registry
   *      the registry to use when resolving to an {@link Id}
   */
  private DefaultPlaceholderId(String name, Collection<TagFactory> tagFactories, Registry registry) {
    this.name = Preconditions.checkNotNull(name, "name");
    this.tagFactories = tagFactories;
    this.registry = registry;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public DefaultPlaceholderId withTag(String k, String v) {
    return withTagFactory(new ConstantTagFactory(new BasicTag(k, v)));
  }

  @Override
  public DefaultPlaceholderId withTag(Tag t) {
    return withTagFactory(new ConstantTagFactory(t));
  }

  @Override
  public DefaultPlaceholderId withTags(Iterable<Tag> tags) {
    return createNewId(sorter -> tags.forEach(tag -> sorter.addFactory(new ConstantTagFactory(tag))));
  }

  @Override
  public DefaultPlaceholderId withTags(Map<String, String> tags) {
    return createNewId(sorter ->
            tags.forEach((key, value) -> sorter.addFactory(new ConstantTagFactory(new BasicTag(key, value)))));
  }

  @Override
  public DefaultPlaceholderId withTagFactory(TagFactory factory) {
    if (tagFactories.isEmpty()) {
      return new DefaultPlaceholderId(name, Collections.singleton(factory), registry);
    } else {
      return createNewId(sorter -> sorter.addFactory(factory));
    }
  }

  @Override
  public DefaultPlaceholderId withTagFactories(Iterable<TagFactory> factories) {
    return createNewId(sorter -> sorter.addFactories(factories));
  }

  @Override
  public Id resolveToId() {
    Iterable<Tag> tags = tagFactories.stream()
            .map(TagFactory::createTag)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    return registry.createId(name, tags);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultPlaceholderId that = (DefaultPlaceholderId) o;

    // We cannot use tagFactories.equals(that.tagFactories) below, because Java
    // unmodifiable collections do not override equals appropriately.
    return name.equals(that.name)
        && registry == that.registry
        && tagFactories.size() == that.tagFactories.size()
        && tagFactories.containsAll(that.tagFactories);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + 31 * registry.hashCode() + tagFactories.hashCode();
  }

  @Override
  public String toString() {
    return resolveToId().toString();
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
  private DefaultPlaceholderId createNewId(Consumer<FactorySorterAndDeduplicator> consumer) {
    FactorySorterAndDeduplicator sorter = new FactorySorterAndDeduplicator(tagFactories);

    consumer.accept(sorter);
    return new DefaultPlaceholderId(name, sorter.asCollection(), registry);
  }
}
