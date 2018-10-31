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
package com.netflix.spectator.servo;

import com.netflix.spectator.api.Tag;

/** Tag implementation for the servo registry. */
class ServoTag implements Tag {

  private final com.netflix.servo.tag.Tag tag;

  /** Create a new instance. */
  ServoTag(com.netflix.servo.tag.Tag tag) {
    this.tag = tag;
  }

  @Override public String key() {
    return tag.getKey();
  }

  @Override public String value() {
    return tag.getValue();
  }
}
