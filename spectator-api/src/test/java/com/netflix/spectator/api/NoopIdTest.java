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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class NoopIdTest {
  @Test
  public void testTags() {
    Assertions.assertFalse(NoopId.INSTANCE.tags().iterator().hasNext());
  }

  @Test
  public void testWithKeyValue() {
    Assertions.assertSame(NoopId.INSTANCE.withTag("k", "v"), NoopId.INSTANCE);
  }

  @Test
  public void testWithTag() {
    Assertions.assertSame(NoopId.INSTANCE.withTag(new BasicTag("k", "v")), NoopId.INSTANCE);
  }

  @Test
  public void testWithTags() {
    Assertions.assertSame(NoopId.INSTANCE.withTags(ArrayTagSet.create("k", "v")), NoopId.INSTANCE);
  }

  @Test
  public void testWithTagMap() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k", "v");
    Assertions.assertSame(NoopId.INSTANCE.withTags(tags), NoopId.INSTANCE);
  }

  @Test
  public void testToString() {
    Assertions.assertEquals(NoopId.INSTANCE.toString(), "noop");
  }
}
