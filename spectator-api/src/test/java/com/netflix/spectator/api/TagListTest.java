/*
 * Copyright 2014-2026 Netflix, Inc.
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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TagListTest {
  @Test
  public void testEmpty() {
    TagList empty = TagList.empty();
    assertEquals(0, empty.size());
    assertEquals(empty, TagList.empty());
    assertFalse(empty.iterator().hasNext());
  }

  @Test
  public void testCreateMap() {
    Map<String, String> tagMap = new HashMap<>();
    tagMap.put("a", "v1");
    tagMap.put("b", "v2");
    tagMap.put("c", "v3");
    TagList tags = TagList.create(tagMap);
    assertEquals(3, tags.size());
    assertEquals(Tag.of("a", "v1"), tags.getTag(0));
    assertEquals(Tag.of("b", "v2"), tags.getTag(1));
    assertEquals(Tag.of("c", "v3"), tags.getTag(2));
  }

  @Test
  public void testCreateIterable() {
    TagList tags = TagList.create(
        Arrays.asList(Tag.of("a", "v1"), Tag.of("b", "v2"), Tag.of("c", "v3")));
    assertEquals(3, tags.size());
    assertEquals(Tag.of("a", "v1"), tags.getTag(0));
    assertEquals(Tag.of("b", "v2"), tags.getTag(1));
    assertEquals(Tag.of("c", "v3"), tags.getTag(2));
  }

  @Test
  public void testCreateIterableContravariance() {
    List<CustomTag> customTags =
        Arrays.asList(CustomTag.of("a", "v1"), CustomTag.of("b", "v2"), CustomTag.of("c", "v3"));
    TagList tags = TagList.create(customTags);
    assertEquals(3, tags.size());
    assertEquals(Tag.of("a", "v1"), tags.getTag(0));
    assertEquals(Tag.of("b", "v2"), tags.getTag(1));
    assertEquals(Tag.of("c", "v3"), tags.getTag(2));
  }

  @Test
  public void testCollector() {
    // Use a mix of Tag implementations to ensure the collector handles a heterogeneous stream.
    TagList tags = Stream.<Tag>of(Tag.of("a", "v1"), CustomTag.of("b", "v2"), Tag.of("c", "v3"))
        .collect(TagList.toTagList());
    assertEquals(3, tags.size());
    assertEquals(Tag.of("a", "v1"), tags.getTag(0));
    assertEquals(Tag.of("b", "v2"), tags.getTag(1));
    assertEquals(Tag.of("c", "v3"), tags.getTag(2));
  }

  static final class CustomTag implements Tag {

    private final String key;
    private final String value;

    private CustomTag(String key, String value) {
      this.key = key;
      this.value = value;
    }

    static CustomTag of(String key, String value) {
      return new CustomTag(key, value);
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public String value() {
      return value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Tag)) {
        return false;
      }
      Tag other = (Tag) obj;
      return Objects.equals(key, other.key()) && Objects.equals(value, other.value());
    }
  }
}
