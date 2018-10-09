/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base class used for builders that need to allow for additional tagging to be
 * added onto a base id. This is typically used in conjunction with {@link IdBuilder}.
 */
@SuppressWarnings("unchecked")
public class TagsBuilder<T extends TagsBuilder<T>> {

  /** Create a new instance. */
  protected TagsBuilder() {
    // This class is only intended to be created by a sub-class. Since there are no
    // abstract methods at this time it is documented via the protected constructor
    // rather than making the class abstract.
  }

  /** Set of extra tags that the sub-class should add in to the id. */
  protected final List<Tag> extraTags = new ArrayList<>();

  /** Add an additional tag value. */
  public T withTag(String k, String v) {
    extraTags.add(new BasicTag(k, v));
    return (T) this;
  }

  /** Add an additional tag value. */
  public T withTag(String k, Boolean v) {
    return withTag(k, Boolean.toString(v));
  }

  /** Add an additional tag value based on the name of the enum. */
  public <E extends Enum<E>> T withTag(String k, Enum<E> v) {
    return withTag(k, v.name());
  }

  /** Add an additional tag value. */
  public T withTag(Tag t) {
    extraTags.add(t);
    return (T) this;
  }

  /** Add additional tag values. */
  public T withTags(String... tags) {
    for (int i = 0; i < tags.length; i += 2) {
      extraTags.add(new BasicTag(tags[i], tags[i + 1]));
    }
    return (T) this;
  }

  /** Add additional tag values. */
  public T withTags(Tag... tags) {
    Collections.addAll(extraTags, tags);
    return (T) this;
  }

  /** Add additional tag values. */
  public T withTags(Iterable<Tag> tags) {
    for (Tag t : tags) {
      extraTags.add(t);
    }
    return (T) this;
  }

  /** Add additional tag values. */
  public T withTags(Map<String, String> tags) {
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      extraTags.add(new BasicTag(entry.getKey(), entry.getValue()));
    }
    return (T) this;
  }
}
