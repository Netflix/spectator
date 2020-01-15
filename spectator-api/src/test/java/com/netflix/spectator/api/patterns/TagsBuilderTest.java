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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TagsBuilderTest {

  private static final Registry REGISTRY = new DefaultRegistry();

  private static Id newId(Iterable<Tag> tags) {
    return REGISTRY.createId("test").withTags(tags);
  }

  private static Id newId(String... tags) {
    return REGISTRY.createId("test").withTags(tags);
  }

  private static class Builder extends TagsBuilder<Builder> {

    Builder() {
      super();
    }

    Id build() {
      return newId(extraTags);
    }
  }

  private enum Level {
    info,
    error
  }

  @Test
  public void stringKeyValue() {
    Id id = new Builder()
        .withTag("k", "v")
        .build();
    Assertions.assertEquals(newId("k", "v"), id);
  }

  @Test
  public void booleanTrueValue() {
    Id id = new Builder()
        .withTag("k", true)
        .build();
    Assertions.assertEquals(newId("k", "true"), id);
  }

  @Test
  public void booleanFalseValue() {
    Id id = new Builder()
        .withTag("k", false)
        .build();
    Assertions.assertEquals(newId("k", "false"), id);
  }

  @Test
  public void enumValue() {
    Id id = new Builder()
        .withTag("k1", Level.info)
        .withTag("k2", Level.error)
        .build();
    Assertions.assertEquals(newId("k1", "info", "k2", "error"), id);
  }

  @Test
  public void tag() {
    Id id = new Builder()
        .withTag(new BasicTag("k", "v"))
        .build();
    Assertions.assertEquals(newId("k", "v"), id);
  }

  @Test
  public void varargStrings() {
    Id id = new Builder()
        .withTags("k1", "v1", "k2", "v2")
        .build();
    Assertions.assertEquals(newId("k1", "v1", "k2", "v2"), id);
  }

  @Test
  public void varargTags() {
    Id id = new Builder()
        .withTags(new BasicTag("k1", "v1"), new BasicTag("k2", "v2"))
        .build();
    Assertions.assertEquals(newId("k1", "v1", "k2", "v2"), id);
  }

  @Test
  public void iterableTags() {
    Id id = new Builder()
        .withTags(newId("k1", "v1", "k2", "v2").tags())
        .build();
    Assertions.assertEquals(newId("k1", "v1", "k2", "v2"), id);
  }

  @Test
  public void mapTags() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k1", "v1");
    tags.put("k2", "v2");
    Id id = new Builder()
        .withTags(tags)
        .build();
    Assertions.assertEquals(newId("k1", "v1", "k2", "v2"), id);
  }

  @Test
  public void overwrite() {
    Id id = new Builder()
        .withTag("k", "v1")
        .withTag("k", "v2")
        .build();
    Assertions.assertEquals(newId("k", "v1", "k", "v2"), id);
  }
}
