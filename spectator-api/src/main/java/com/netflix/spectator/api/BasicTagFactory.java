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

/**
 * Basic implementation of the TagFactory class that does nothing fancy.
 */
public final class BasicTagFactory implements TagFactory {
  private final String name;

  /** Create a new instance that will produce tags with the specified non-null name. */
  protected BasicTagFactory(String name) {
    this.name = Preconditions.checkNotNull(name, "name");
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Tag createTag(String value) {
    return value != null ? new TagList(name, value) : TagList.EMPTY;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return name.equals(((BasicTagFactory) o).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
