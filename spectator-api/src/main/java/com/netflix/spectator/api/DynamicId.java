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

/**
 * An extension of the {@link Id} interface that allows the list of tag names attached
 * to the Id to be declared in advance of the use of the metric.  This can be used to
 * provide a default value for a tag or to use a TagFactory implementation that uses
 * context available in the execution environment to compute the value of the tag.
 */
public interface DynamicId extends Id {
  /**
   * New id with an additional tag factory.
   * @param factory
   *        the factory to use to generate the values for the tag
   */
  DynamicId withTagFactory(TagFactory factory);

  /**
   * New id with additional tag factories.
   * @param factories
   *        a collection of factories for producing values for the tags
   */
  DynamicId withTagFactories(Iterable<TagFactory> factories);
}
