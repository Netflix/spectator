/**
 * Copyright 2014 Netflix, Inc.
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class TagListTest {

  @Test
  public void equalsContractTest() {
    // NOTE: EqualsVerifier doesn't work with cached hash code
    TagList ts1 = new TagList("k1", "v1");
    TagList ts2 = new TagList("k2", "v2", ts1);
    Assert.assertTrue(ts1.equals(ts1));
    Assert.assertTrue(ts2.equals(ts2));
    Assert.assertTrue(!ts1.equals(null));
    Assert.assertTrue(!ts1.equals(new Object()));
    Assert.assertTrue(!ts1.equals(new TagList("k1", "v2")));
    Assert.assertTrue(!ts1.equals(new TagList("k2", "v1")));
    Assert.assertTrue(!ts1.equals(new TagList("k1", "v1", ts2)));
    Assert.assertTrue(ts2.equals(new TagList("k2", "v2", ts1)));
    Assert.assertTrue(ts2.equals(new TagList("k2", "v2", new TagList("k1", "v1"))));
  }

  @Test
  public void testSingle() {
    TagList ts = new TagList("k", "v");
    for (Tag t : ts) {
      Assert.assertEquals(t, ts);
      Assert.assertEquals(t.key(), "k");
      Assert.assertEquals(t.value(), "v");
    }
  }

  @Test(expected = NullPointerException.class)
  public void testNullKey() {
    new TagList(null, "v");
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    new TagList("k", null);
  }

  @Test
  public void testCreateFromMap() {
    Map<String, String> m = new HashMap<>();
    m.put("k", "v");
    TagList ts1 = TagList.create(m);
    TagList ts2 = new TagList("k", "v");
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromTagList() {
    TagList ts = new TagList("k", "v");
    TagList ts1 = TagList.create(ts);
    TagList ts2 = new TagList("k", "v");
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromIterable() {
    Collection<Tag> coll = Collections.<Tag>singleton(new TagList("k", "v"));
    TagList ts1 = TagList.create(coll);
    TagList ts2 = new TagList("k", "v");
    Assert.assertEquals(ts1, ts2);
  }
}
