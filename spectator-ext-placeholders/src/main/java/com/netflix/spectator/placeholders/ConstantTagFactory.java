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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.Preconditions;

/**
 * TagFactory implementation that always produces the same tag.  Useful for
 * providing a default value for a tag.
 */
public final class ConstantTagFactory implements TagFactory {
  private final Tag tag;

  /**
   * Construct a new instance that will always return a Tag with the specified value.
   *
   * @param key
   *        the non-null key for the tag
   * @param value
   *        the non-null value for the tag
   */
  public ConstantTagFactory(String key, String value) {
    this(new BasicTag(key, value));
  }

  /**
   * Construct a new instance that will always return the specified tag.
   *
   * @param tag
   *        the non-null Tag instance to return from createTag
   */
  public ConstantTagFactory(Tag tag) {
    this.tag = Preconditions.checkNotNull(tag, "tag");
  }

  @Override
  public String name() {
    return tag.key();
  }

  @Override
  public Tag createTag() {
    return tag;
  }
}
