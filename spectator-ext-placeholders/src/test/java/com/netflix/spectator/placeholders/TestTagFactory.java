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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;
import org.junit.jupiter.api.Disabled;

/**
 * Helper class for testing dynamic metrics.
 */
@Disabled
class TestTagFactory implements TagFactory {
  private final String name;
  private final String[] valueHolder;

  TestTagFactory(String[] valueHolder) {
    this("tag", valueHolder);
  }

  TestTagFactory(String name, String[] valueHolder) {
    this.name = name;
    this.valueHolder = valueHolder;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Tag createTag() {
    return new BasicTag(name, valueHolder[0]);
  }
}
