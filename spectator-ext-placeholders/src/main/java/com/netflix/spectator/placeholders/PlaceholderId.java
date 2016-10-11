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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;

import java.util.Map;

/**
 * An extension of the {@link Id} interface that allows the list of tag names attached
 * to the Id to be declared in advance of the use of the metric.  This can be used to
 * provide a default value for a tag or to use a TagFactory implementation that uses
 * context available in the execution environment to compute the value of the tag.
 */
public interface PlaceholderId {
  /** Description of the measurement that is being collected. */
  String name();

  /** New id with an additional tag value. */
  PlaceholderId withTag(String k, String v);

  /** New id with an additional tag value. */
  PlaceholderId withTag(Tag t);

  /** New id with additional tag values. */
  PlaceholderId withTags(Iterable<Tag> tags);

  /** New id with additional tag values. */
  PlaceholderId withTags(Map<String, String> tags);

  /**
   * New id with an additional tag factory.
   * @param factory
   *        the factory to use to generate the values for the tag
   */
  PlaceholderId withTagFactory(TagFactory factory);

  /**
   * New id with additional tag factories.
   * @param factories
   *        a collection of factories for producing values for the tags
   */
  PlaceholderId withTagFactories(Iterable<TagFactory> factories);

  /**
   * Invokes each of the associated tag factories to produce a Id based on the
   * runtime context available when this method is invoked.  If an associated
   * TagFactory produces a non-null Tag, then the returned Id will have that
   * Tag associated with it.
   *
   * @return an Id that has the same name as this id and the resolved tag values attached
   */
  Id resolveToId();
}
