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

import java.util.function.Supplier;

/**
 * A factory for producing tag values.
 */
public interface TagFactory {

  /**
   * Helper method for creating a tag factory that uses a lambda to supply
   * the value.
   *
   * @param name
   *     Key to use for the returned tag value.
   * @param value
   *     Supplier used to retrieve the value for the tag. If the return
   *     value is null, then a null tag is returned an the dimension will
   *     be suppressed.
   * @return
   *     Factory for producing tags using the value supplier.
   */
  static TagFactory from(String name, Supplier<String> value) {
    return new TagFactory() {
      @Override public String name() {
        return name;
      }

      @Override public Tag createTag() {
        final String v = value.get();
        return (v == null) ? null : new BasicTag(name, v);
      }
    };
  }

  /**
   * Returns the name of the factory, which is used as the key for any Tag
   * produced by the createTag method.
   */
  String name();

  /**
   * Produces a tag based on the runtime context available to the factory.
   *
   * @return
   *      the appropriate tag given the available context data, possibly null
   */
  Tag createTag();
}
