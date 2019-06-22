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
package com.netflix.spectator.api;

import java.util.Collections;
import java.util.Map;

/** Id implementation for the no-op registry. */
final class NoopId implements Id {
  /** Singleton instance. */
  static final Id INSTANCE = new NoopId();

  private NoopId() {
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

  @Override public String getKey(int i) {
    return "name";
  }

  @Override public String getValue(int i) {
    return name();
  }

  @Override public int size() {
    return 1;
  }
}
