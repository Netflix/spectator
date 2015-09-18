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
import java.util.Collections;
import java.util.Map;

/**
 * DynamicId implementation for the no-op registry.
 *
 * Created by pstout on 8/27/15.
 */
final class NoopDynamicId  implements DynamicId {
  /** Singleton instance. */
  static final DynamicId INSTANCE = new NoopDynamicId();

  private NoopDynamicId() {
  }

  @Override public String name() {
    return "noop";
  }

  @Override public Iterable<Tag> tags() {
    return Collections.emptyList();
  }

  @Override public Id withTag(String k, String v) {
    return this;
  }

  @Override public Id withTag(Tag tag) {
    return this;
  }

  @Override public Id withTags(Iterable<Tag> tags) {
    return this;
  }

  @Override public Id withTags(Map<String, String> tags) {
    return this;
  }

  @Override public String toString() {
    return name();
  }

  @Override
  public Collection<TagFactory> tagFactories() {
    return Collections.emptyList();
  }

  @Override
  public DynamicId withTagName(String tagName) {
    return this;
  }

  @Override
  public DynamicId withTagNames(Iterable<String> tagNames) {
    return this;
  }

  @Override
  public DynamicId withTagFactory(TagFactory factory) {
    return this;
  }

  @Override
  public DynamicId withTagFactories(Iterable<TagFactory> factories) {
    return this;
  }
}
