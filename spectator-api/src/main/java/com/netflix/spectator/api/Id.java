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

import java.util.Map;

/**
 * Identifier for a meter or measurement.
 */
public interface Id {
  /** Description of the measurement that is being collected. */
  String name();

  /** Other dimensions that can be used to classify the measurement. */
  Iterable<Tag> tags();

  /** New id with an additional tag value. */
  Id withTag(String k, String v);

  /** New id with an additional tag value. */
  Id withTag(Tag t);

  /** New id with additional tag values. */
  Id withTags(Iterable<Tag> tags);

  /** New id with additional tag values. */
  Id withTags(Map<String, String> tags);
}
