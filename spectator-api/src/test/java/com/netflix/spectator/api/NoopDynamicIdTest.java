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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class NoopDynamicIdTest {
  @Test
  public void testTags() {
    Assert.assertFalse(NoopDynamicId.INSTANCE.tags().iterator().hasNext());
  }

  @Test
  public void testWithKeyValueTag() {
    Assert.assertSame(NoopDynamicId.INSTANCE.withTag("k", "v"), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testWithTag() {
    Assert.assertSame(NoopDynamicId.INSTANCE.withTag(new TagList("k", "v")), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testWithTags() {
    Assert.assertSame(NoopDynamicId.INSTANCE.withTags(new TagList("k", "v")), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testWithTagsMap() {
    Map<String, String> tags = new HashMap<>();

    tags.put("key", "value");
    Assert.assertSame(NoopDynamicId.INSTANCE.withTags(tags), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testWithTagFactory() {
    Assert.assertSame(NoopDynamicId.INSTANCE.withTagFactory(new ConstantTagFactory("k", "v")), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testWithTagFactories() {
    Assert.assertSame(NoopDynamicId.INSTANCE.withTagFactories(Arrays.asList(new ConstantTagFactory("k", "v"))), NoopDynamicId.INSTANCE);
  }

  @Test
  public void testToString() {
    Assert.assertEquals(NoopDynamicId.INSTANCE.toString(), "noop");
  }
}
