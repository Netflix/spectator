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
 * A factory for producing tag values.
 */
public interface TagFactory {
  /**
   * Returns the name of the factory, which is used as the key for any Tag
   * produced by the createTag method.
   */
  String name();

  /**
   * Produces a tag based on the specified input value.
   *
   * @param value
   *      possibly null input to the factory for producing a tag.  The semantics
   *      of the are specific the TagFactory implementation.  If the value is
   *      null, then an implementation may compute a value based on information
   *      available in the runtime state of the thread.
   * @return
   *      the appropriate tag given the specified input value, possibly null
   */
  Tag createTag(String value);
}
