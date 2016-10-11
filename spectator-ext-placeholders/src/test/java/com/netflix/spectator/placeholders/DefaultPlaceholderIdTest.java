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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the DefaultPlaceholderId class.
 *
 * Created on 9/17/15.
 */
@RunWith(JUnit4.class)
public class DefaultPlaceholderIdTest {

  private static final Registry REGISTRY = new DefaultRegistry();
  private static final Id ID_1 = REGISTRY.createId("foo", "k1", "v1");
  private static final Id ID_2 = REGISTRY.createId("foo", "k1", "v1", "k2", "v2");

  @Test(expected = NullPointerException.class)
  public void testNullName() {
    new DefaultPlaceholderId(null, REGISTRY);
  }

  @Test
  public void testName() {
    PlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY);
    Assert.assertEquals(id.name(), "foo");
  }

  @Test
  public void equalsContractTest() {
    EqualsVerifier
            .forClass(DefaultPlaceholderId.class)
            .withPrefabValues(Iterable.class, ID_1.tags(), ID_2.tags())
            .suppress(Warning.NULL_FIELDS)
            .verify();
  }

  @Test
  public void testToString() {
    DefaultPlaceholderId id = (new DefaultPlaceholderId("foo", REGISTRY)).withTag("k1", "v1").withTag("k2", "v2");
    Assert.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void testToStringNameOnly() {
    DefaultPlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY);
    Assert.assertEquals(id.toString(), "foo");
  }

  @Test
  public void testWithTag() {
    Tag expected = new BasicTag("key", "value");
    DefaultPlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY).withTag(expected);
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertTrue("tags empty", tags.hasNext());
    Assert.assertEquals(expected, tags.next());
  }

  @Test
  public void testWithTagsIterable() {
    List<Tag> tags = new ArrayList<>();
    tags.add(new BasicTag("k1", "v1"));
    tags.add(new BasicTag("k2", "v2"));
    DefaultPlaceholderId id = (new DefaultPlaceholderId("foo", REGISTRY)).withTags(tags);
    Assert.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void testWithTagsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    DefaultPlaceholderId id = (new DefaultPlaceholderId("foo", REGISTRY)).withTags(map);
    Assert.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void testWithNoopTagFactory() {
    DefaultPlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY).withTagFactory(new TagFactory() {
      @Override
      public String name() {
        return "noopTagFactory";
      }

      @Override
      /* Implementation that always returns null, which should result in the tag being omitted. */
      public Tag createTag() {
        return null;
      }
    });
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertFalse("tags not empty", tags.hasNext());
  }

  @Test
  public void testWithTagFactory() {
    Tag expected = new BasicTag("key", "value");
    DefaultPlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY).withTagFactory(new ConstantTagFactory(expected));
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertTrue("tags empty", tags.hasNext());
    Assert.assertEquals(expected, tags.next());
  }

  @Test
  public void testWithTagFactories() {
    Tag tags1 = new BasicTag("k1", "v1");
    Tag tags2 = new BasicTag("k2", "v2");
    List<TagFactory> factories = Arrays.asList(new ConstantTagFactory(tags1), new ConstantTagFactory(tags2));
    DefaultPlaceholderId id = new DefaultPlaceholderId("foo", REGISTRY).withTagFactories(factories);
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertTrue("tags empty", tags.hasNext());
    Assert.assertEquals(tags1, tags.next());
    Assert.assertEquals(tags2, tags.next());
  }

  @Test
  public void testResolveToId() {
    Tag tag = new BasicTag("key", "value");
    Id expected = REGISTRY.createId("foo").withTag(tag);
    PlaceholderId placeholderId = new DefaultPlaceholderId("foo", REGISTRY).withTag(tag);
    Assert.assertEquals(expected, placeholderId.resolveToId());
  }

  @Test
  public void testCreateWithFactories() {
    Tag tags1 = new BasicTag("k1", "v1");
    Tag tags2 = new BasicTag("k2", "v2");
    List<TagFactory> factories = Arrays.asList(new ConstantTagFactory(tags1), new ConstantTagFactory(tags2));
    DefaultPlaceholderId id = DefaultPlaceholderId.createWithFactories("foo", factories, REGISTRY);
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertEquals("foo", id.name());
    Assert.assertTrue("tags empty", tags.hasNext());
    Assert.assertEquals(tags1, tags.next());
    Assert.assertEquals(tags2, tags.next());
  }

  @Test
  public void testCreateWithFactoriesNullIterable() {
    DefaultPlaceholderId id = DefaultPlaceholderId.createWithFactories("foo", null, REGISTRY);
    Iterator<Tag> tags = id.resolveToId().tags().iterator();

    Assert.assertEquals("foo", id.name());
    Assert.assertFalse("tags not empty", tags.hasNext());
  }
}
