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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RunWith(JUnit4.class)
public class ArrayTagSetTest {

  @Test
  public void equalsContractTest() {
    // NOTE: EqualsVerifier doesn't work with cached hash code
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k2", "v2").addAll(ts1);
    Assert.assertEquals(ts1, ts1);
    Assert.assertEquals(ts2, ts2);
    Assert.assertNotEquals(ts1, null);
    Assert.assertNotEquals(ts1, new Object());
    Assert.assertNotEquals(ts1, ArrayTagSet.create("k1", "v2"));
    Assert.assertNotEquals(ts1, ArrayTagSet.create("k2", "v1"));
    Assert.assertNotEquals(ts1, ArrayTagSet.create("k1", "v1").addAll(ts2));
    Assert.assertEquals(ts2, ArrayTagSet.create("k2", "v2").addAll(ts1));
    Assert.assertEquals(ts2, ArrayTagSet.create("k2", "v2").addAll(ArrayTagSet.create("k1", "v1")));
  }

  @Test
  public void testToString() {
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k2", "v2").addAll(ts1);
    ArrayTagSet ts3 = ArrayTagSet.create("k3", "v3").addAll(ts2);

    Assert.assertEquals(":k1=v1", ts1.toString());
    Assert.assertEquals(":k1=v1:k2=v2", ts2.toString());
    Assert.assertEquals(":k1=v1:k2=v2:k3=v3", ts3.toString());
  }

  @Test
  public void testSingle() {
    ArrayTagSet ts = ArrayTagSet.create("k", "v");
    for (Tag t : ts) {
      Assert.assertEquals(t.key(), "k");
      Assert.assertEquals(t.value(), "v");
    }
  }

  @Test(expected = NullPointerException.class)
  public void testNullKey() {
    ArrayTagSet.create(null, "v");
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    ArrayTagSet.create("k", null);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testIteratorRemoveUnsupported() {
    ArrayTagSet.create("k", "v").iterator().remove();
  }

  @Test(expected = NoSuchElementException.class)
  public void testIteratorNext() {
    ArrayTagSet tag = ArrayTagSet.create("k", "v");
    Iterator<Tag> iter = tag.iterator();

    Assert.assertTrue(iter.hasNext());
    Assert.assertEquals(new BasicTag("k", "v"), iter.next());
    Assert.assertFalse(iter.hasNext());
    iter.next();
  }

  @Test
  public void testCreateFromMap() {
    Map<String, String> m = new HashMap<>();
    m.put("k", "v");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromMapWithMultipleValues() {
    Map<String, String> m = new HashMap<>();
    m.put("k1", "v1");
    m.put("k2", "v2");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromArrayTagSet() {
    ArrayTagSet ts = ArrayTagSet.create("k", "v");
    ArrayTagSet ts1 = ArrayTagSet.create(ts);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromEmptyIterable() {
    Assert.assertEquals(ArrayTagSet.EMPTY, ArrayTagSet.create(Collections.emptyList()));
  }

  @Test
  public void testCreateFromSingleValueIterable() {
    Collection<Tag> coll = Collections.singleton(new BasicTag("k", "v"));
    ArrayTagSet ts1 = ArrayTagSet.create(coll);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromMultiValueIterable() {
    List<Tag> coll = new ArrayList<>();
    coll.add(new BasicTag("k1", "v1"));
    coll.add(new BasicTag("k2", "v2"));
    ArrayTagSet ts1 = ArrayTagSet.create(coll);
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));
    Assert.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromEmptyMap() {
    Assert.assertEquals(ArrayTagSet.EMPTY, ArrayTagSet.create(Collections.emptyMap()));
  }

  @Test
  public void testCreateFromSingleValueMap() {
    Map<String, String> tags = new HashMap<>();

    tags.put("k", "v");
    Assert.assertEquals(ArrayTagSet.create("k", "v"), ArrayTagSet.create(tags));
  }

  @Test
  public void testCreateFromMultiValueMap() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k1", "v1");
    tags.put("k2", "v2");
    Assert.assertEquals(ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2")), ArrayTagSet.create(tags));
  }

  @Test(expected = NullPointerException.class)
  public void testMergeNullTag() {
    ArrayTagSet expected = ArrayTagSet.create("k", "v");
    expected.add(null);
  }

  @Test
  public void testMergeTag() {
    ArrayTagSet initial = ArrayTagSet.create("k2", "v2");
    ArrayTagSet update = ArrayTagSet.create("k1", "v1");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));

    Assert.assertEquals(expected, initial.addAll(update));
  }

  @Test
  public void testMergeTagWithSameKey() {
    ArrayTagSet initial = ArrayTagSet.create("k1", "v1");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v2");
    ArrayTagSet actual = initial.addAll(expected);

    Assert.assertNotSame(expected, actual);
    Assert.assertEquals(expected, actual);
  }

  @Test(expected = NullPointerException.class)
  public void testMergeNullList() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    expected.addAll((Iterable<Tag>) null);
  }

  @Test
  public void testMergeEmptyList() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    ArrayTagSet actual = expected.addAll(new ArrayList<>());
    Assert.assertSame(expected, actual);
  }

  @Test
  public void testMergeSingleValueAsList() {
    ArrayList<Tag> prefix = new ArrayList<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k3", "v3"));

    prefix.add(new BasicTag("k1", "v1"));
    ArrayTagSet actual = initial.addAll(prefix);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testMergeMultipleValuesAsList() {
    ArrayList<Tag> prefix = new ArrayList<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2")).addAll(ArrayTagSet.create("k3", "v3"));

    prefix.add(new BasicTag("k1", "v1"));
    prefix.add(new BasicTag("k2", "v2"));
    ArrayTagSet actual = initial.addAll(prefix);
    Assert.assertEquals(expected, actual);
  }

  @Test(expected = NullPointerException.class)
  public void testMergeNullMap() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    expected.addAll((Map<String, String>) null);
  }

  @Test
  public void testMergeEmptyMap() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    ArrayTagSet actual = expected.addAll(new HashMap<>());
    Assert.assertSame(expected, actual);
  }

  @Test
  public void testMergeSingleValueAsMap() {
    Map<String, String> extra = new HashMap<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k3", "v3"));

    extra.put("k1", "v1");
    ArrayTagSet actual = initial.addAll(extra);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testMergeMultipleValuesAsMap() {
    Map<String, String> extra = new HashMap<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2")).addAll(ArrayTagSet.create("k3", "v3"));

    extra.put("k1", "v1");
    extra.put("k2", "v2");
    ArrayTagSet actual = initial.addAll(extra);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void addAllTagArrayEmpty() {
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(new Tag[0]);
    Assert.assertSame(ArrayTagSet.EMPTY, ts);
  }

  @Test
  public void addAllStringArrayEmpty() {
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(new String[0]);
    Assert.assertSame(ArrayTagSet.EMPTY, ts);
  }

  @Test
  public void addAllIterable() {
    // Add an arbitrary iterable that isn't a collection or ArrayTagSet
    Collection<Tag> tags = Collections.singletonList(new BasicTag("app", "foo"));
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(tags::iterator);
    Assert.assertEquals(ArrayTagSet.EMPTY.addAll(tags), ts);
  }
}
