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

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class NoopIdTest {
  @Test
  public void testTags() {
    Assert.assertFalse(NoopId.INSTANCE.tags().iterator().hasNext());
  }

  @Test
  public void testWithKeyValue() {
    Assert.assertSame(NoopId.INSTANCE.withTag("k", "v"), NoopId.INSTANCE);
  }

  @Test
  public void testWithTag() {
    Assert.assertSame(NoopId.INSTANCE.withTag(new BasicTag("k", "v")), NoopId.INSTANCE);
  }

  @Test
  public void testWithTags() {
    Assert.assertSame(NoopId.INSTANCE.withTags(ArrayTagSet.create("k", "v")), NoopId.INSTANCE);
  }

  @Test
  public void testWithTagMap() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k", "v");
    Assert.assertSame(NoopId.INSTANCE.withTags(tags), NoopId.INSTANCE);
  }

  @Test
  public void testToString() {
    Assert.assertEquals(NoopId.INSTANCE.toString(), "noop");
  }
}
