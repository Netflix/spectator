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

import java.util.Collection;

/**
 * An extension of the {@link Id} interface that allows the list of tag names to attached to the Id
 * to be declared in advance of the use of the metric.
 */
public interface DynamicId extends Id {
  /** Immutable map of tag names to tag factories. */
  Collection<TagFactory> tagFactories();

  /** New id with an additional tag name. */
  DynamicId withTagName(String tagName);

  /** New id with additional tag names. */
  DynamicId withTagNames(Iterable<String> tagNames);

  /** New id with an additional tag factory.
   * @param factory the factory to use to generate the values for the tag
   */
  DynamicId withTagFactory(TagFactory factory);

  /** New id with additional tag factories.
   * @param factories a map of tag name to factories for producing values for that tag
   */
  DynamicId withTagFactories(Iterable<TagFactory> factories);
}
