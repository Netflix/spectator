/*
 * Copyright 2014-2025 Netflix, Inc.
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

public class TagListBuilderTest {

  @Test
  public void createEmpty() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder.buildAndReset();
    Assertions.assertEquals(0, ts.size());
  }

  @Test
  public void addSingleTag() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder.add("name", "value").buildAndReset();
    Assertions.assertEquals(1, ts.size());
    Assertions.assertEquals("name", ts.getKey(0));
    Assertions.assertEquals("value", ts.getValue(0));
  }

  @Test
  public void addMultipleTags() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder
        .add("a", "1")
        .add("b", "2")
        .add("c", "3")
        .buildAndReset();
    Assertions.assertEquals(3, ts.size());
    Assertions.assertEquals("a", ts.getKey(0));
    Assertions.assertEquals("1", ts.getValue(0));
    Assertions.assertEquals("b", ts.getKey(1));
    Assertions.assertEquals("2", ts.getValue(1));
    Assertions.assertEquals("c", ts.getKey(2));
    Assertions.assertEquals("3", ts.getValue(2));
  }

  @Test
  public void addTagObject() {
    TagListBuilder builder = TagListBuilder.create();
    Tag tag = Tag.of("key", "value");
    TagList ts = builder.add(tag).buildAndReset();
    Assertions.assertEquals(1, ts.size());
    Assertions.assertEquals("key", ts.getKey(0));
    Assertions.assertEquals("value", ts.getValue(0));
  }

  @Test
  public void mixedAddMethods() {
    TagListBuilder builder = TagListBuilder.create();
    Tag tag = Tag.of("tag1", "value1");
    TagList ts = builder
        .add(tag)
        .add("tag2", "value2")
        .buildAndReset();
    Assertions.assertEquals(2, ts.size());
    Assertions.assertEquals("tag1", ts.getKey(0));
    Assertions.assertEquals("value1", ts.getValue(0));
    Assertions.assertEquals("tag2", ts.getKey(1));
    Assertions.assertEquals("value2", ts.getValue(1));
  }

  @Test
  public void builderReuse() {
    TagListBuilder builder = TagListBuilder.create();
    
    TagList ts1 = builder.add("a", "1").buildAndReset();
    Assertions.assertEquals(1, ts1.size());
    Assertions.assertEquals("a", ts1.getKey(0));
    
    TagList ts2 = builder.add("b", "2").buildAndReset();
    Assertions.assertEquals(1, ts2.size());
    Assertions.assertEquals("b", ts2.getKey(0));
  }

  @Test
  public void sortedOrder() {
    TagListBuilder builder = TagListBuilder.create();
    builder
        .add("a", "1")
        .add("b", "2")
        .add("c", "3");
    Assertions.assertTrue(builder.isSorted());
    builder.buildAndReset();
  }

  @Test
  public void unsortedOrder() {
    TagListBuilder builder = TagListBuilder.create();
    builder
        .add("c", "3")
        .add("a", "1")
        .add("b", "2");
    Assertions.assertFalse(builder.isSorted());
    builder.buildAndReset();
  }

  @Test
  public void duplicates() {
    TagListBuilder builder = TagListBuilder.create();
    builder
        .add("a", "1")
        .add("b", "2")
        .add("b", "3")
        .add("c", "4");
    Assertions.assertFalse(builder.isSorted());
    builder.buildAndReset();
  }

  @Test
  public void expandCapacity() {
    TagListBuilder builder = TagListBuilder.create();
    for (int i = 0; i < 15; i++) {
      builder.add(String.format("key%02d", i), "value" + i);
    }
    TagList ts = builder.buildAndReset();
    Assertions.assertEquals(15, ts.size());
    for (int i = 0; i < 15; i++) {
      Assertions.assertEquals(String.format("key%02d", i), ts.getKey(i));
      Assertions.assertEquals("value" + i, ts.getValue(i));
    }
  }

  @Test
  public void resetClearsState() {
    TagListBuilder builder = TagListBuilder.create();
    builder.add("a", "1");
    builder.reset();
    TagList ts = builder.buildAndReset();
    Assertions.assertEquals(0, ts.size());
  }

  @Test
  public void nullKey() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder.add(null, "value").buildAndReset();
    Assertions.assertEquals(0, ts.size());
  }

  @Test
  public void nullValue() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder.add("key", null).buildAndReset();
    Assertions.assertEquals(0, ts.size());
  }

  @Test
  public void emptyKeyValue() {
    TagListBuilder builder = TagListBuilder.create();
    TagList ts = builder.add("", "").buildAndReset();
    Assertions.assertEquals(1, ts.size());
    Assertions.assertEquals("", ts.getKey(0));
    Assertions.assertEquals("", ts.getValue(0));
  }

  @Test
  public void sortedStatePreservedAfterReuse() {
    TagListBuilder builder = TagListBuilder.create();
    
    // First use: add sorted tags
    builder
        .add("a", "1")
        .add("b", "2")
        .add("c", "3");
    Assertions.assertTrue(builder.isSorted());
    builder.buildAndReset();
    
    // Second use: add sorted tags again - should still be considered sorted
    builder
        .add("x", "1")
        .add("y", "2")
        .add("z", "3");
    Assertions.assertTrue(builder.isSorted());
    builder.buildAndReset();
    
    // Third use: empty builder should be sorted
    Assertions.assertTrue(builder.isSorted());
  }
}
