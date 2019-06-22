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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class ArrayTagSetTest {

  @Test
  public void equalsContractTest() {
    // NOTE: EqualsVerifier doesn't work with cached hash code
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k2", "v2").addAll(ts1);
    Assertions.assertEquals(ts1, ts1);
    Assertions.assertEquals(ts2, ts2);
    Assertions.assertNotEquals(ts1, null);
    Assertions.assertNotEquals(ts1, new Object());
    Assertions.assertNotEquals(ts1, ArrayTagSet.create("k1", "v2"));
    Assertions.assertNotEquals(ts1, ArrayTagSet.create("k2", "v1"));
    Assertions.assertNotEquals(ts1, ArrayTagSet.create("k1", "v1").addAll(ts2));
    Assertions.assertEquals(ts2, ArrayTagSet.create("k2", "v2").addAll(ts1));
    Assertions.assertEquals(ts2, ArrayTagSet.create("k2", "v2").addAll(ArrayTagSet.create("k1", "v1")));
  }

  @Test
  public void testToString() {
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k2", "v2").addAll(ts1);
    ArrayTagSet ts3 = ArrayTagSet.create("k3", "v3").addAll(ts2);

    Assertions.assertEquals(":k1=v1", ts1.toString());
    Assertions.assertEquals(":k1=v1:k2=v2", ts2.toString());
    Assertions.assertEquals(":k1=v1:k2=v2:k3=v3", ts3.toString());
  }

  @Test
  public void testSingle() {
    ArrayTagSet ts = ArrayTagSet.create("k", "v");
    for (Tag t : ts) {
      Assertions.assertEquals(t.key(), "k");
      Assertions.assertEquals(t.value(), "v");
    }
  }

  @Test
  public void testNullKey() {
    Assertions.assertThrows(NullPointerException.class, () -> ArrayTagSet.create(null, "v"));
  }

  @Test
  public void testNullValue() {
    Assertions.assertThrows(NullPointerException.class, () -> ArrayTagSet.create("k", null));
  }

  @Test
  public void testIteratorRemoveUnsupported() {
    Assertions.assertThrows(UnsupportedOperationException.class,
        () -> ArrayTagSet.create("k", "v").iterator().remove());
  }

  @Test
  public void testIteratorNext() {
    Assertions.assertThrows(NoSuchElementException.class, () -> {
      ArrayTagSet tag = ArrayTagSet.create("k", "v");
      Iterator<Tag> iter = tag.iterator();

      Assertions.assertTrue(iter.hasNext());
      Assertions.assertEquals(new BasicTag("k", "v"), iter.next());
      Assertions.assertFalse(iter.hasNext());
      iter.next();
    });
  }

  @Test
  public void testCreateFromMap() {
    Map<String, String> m = new HashMap<>();
    m.put("k", "v");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromMapWithMultipleValues() {
    Map<String, String> m = new HashMap<>();
    m.put("k1", "v1");
    m.put("k2", "v2");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromArrayTagSet() {
    ArrayTagSet ts = ArrayTagSet.create("k", "v");
    ArrayTagSet ts1 = ArrayTagSet.create(ts);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromVarargs2() {
    Map<String, String> m = new HashMap<>();
    m.put("k1", "v1");
    m.put("k2", "v2");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v1", "k2", "v2");
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromVarargs5() {
    Map<String, String> m = new HashMap<>();
    m.put("a", "1");
    m.put("b", "2");
    m.put("c", "3");
    m.put("d", "4");
    m.put("e", "5");
    ArrayTagSet ts1 = ArrayTagSet.create(m);
    ArrayTagSet ts2 = ArrayTagSet.create("a", "1", "b", "2", "c", "3", "d", "4", "e", "5");
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromVarargsOdd() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ArrayTagSet.create("a", "1", "b"));
  }

  @Test
  public void testCreateFromEmptyIterable() {
    Assertions.assertEquals(ArrayTagSet.EMPTY, ArrayTagSet.create(Collections.emptyList()));
  }

  @Test
  public void testCreateFromSingleValueIterable() {
    Collection<Tag> coll = Collections.singleton(new BasicTag("k", "v"));
    ArrayTagSet ts1 = ArrayTagSet.create(coll);
    ArrayTagSet ts2 = ArrayTagSet.create("k", "v");
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromMultiValueIterable() {
    List<Tag> coll = new ArrayList<>();
    coll.add(new BasicTag("k1", "v1"));
    coll.add(new BasicTag("k2", "v2"));
    ArrayTagSet ts1 = ArrayTagSet.create(coll);
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));
    Assertions.assertEquals(ts1, ts2);
  }

  @Test
  public void testCreateFromEmptyMap() {
    Assertions.assertEquals(ArrayTagSet.EMPTY, ArrayTagSet.create(Collections.emptyMap()));
  }

  @Test
  public void testCreateFromSingleValueMap() {
    Map<String, String> tags = new HashMap<>();

    tags.put("k", "v");
    Assertions.assertEquals(ArrayTagSet.create("k", "v"), ArrayTagSet.create(tags));
  }

  @Test
  public void testCreateFromMultiValueMap() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k1", "v1");
    tags.put("k2", "v2");
    Assertions.assertEquals(ArrayTagSet.create("k1", "v1")
        .addAll(ArrayTagSet.create("k2", "v2")), ArrayTagSet.create(tags));
  }

  @Test
  public void testMergeNullTag() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      ArrayTagSet expected = ArrayTagSet.create("k", "v");
      expected.add(null);
    });
  }

  @Test
  public void testMergeTag() {
    ArrayTagSet initial = ArrayTagSet.create("k2", "v2");
    ArrayTagSet update = ArrayTagSet.create("k1", "v1");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k2", "v2"));

    Assertions.assertEquals(expected, initial.addAll(update));
  }

  @Test
  public void testMergeTagWithSameKey() {
    ArrayTagSet initial = ArrayTagSet.create("k1", "v1");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v2");
    ArrayTagSet actual = initial.addAll(expected);

    Assertions.assertNotSame(expected, actual);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void testMergeNullList() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
      expected.addAll((Iterable<Tag>) null);
    });
  }

  @Test
  public void testMergeEmptyList() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    ArrayTagSet actual = expected.addAll(new ArrayList<>());
    Assertions.assertSame(expected, actual);
  }

  @Test
  public void testMergeSingleValueAsList() {
    ArrayList<Tag> prefix = new ArrayList<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k3", "v3"));

    prefix.add(new BasicTag("k1", "v1"));
    ArrayTagSet actual = initial.addAll(prefix);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void testMergeMultipleValuesAsList() {
    ArrayList<Tag> prefix = new ArrayList<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1")
        .addAll(ArrayTagSet.create("k2", "v2"))
        .addAll(ArrayTagSet.create("k3", "v3"));

    prefix.add(new BasicTag("k1", "v1"));
    prefix.add(new BasicTag("k2", "v2"));
    ArrayTagSet actual = initial.addAll(prefix);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void testMergeNullMap() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
      expected.addAll((Map<String, String>) null);
    });
  }

  @Test
  public void testMergeEmptyMap() {
    ArrayTagSet expected = ArrayTagSet.create("k3", "v3");
    ArrayTagSet actual = expected.addAll(new HashMap<>());
    Assertions.assertSame(expected, actual);
  }

  @Test
  public void testMergeSingleValueAsMap() {
    Map<String, String> extra = new HashMap<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1").addAll(ArrayTagSet.create("k3", "v3"));

    extra.put("k1", "v1");
    ArrayTagSet actual = initial.addAll(extra);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void testMergeMultipleValuesAsMap() {
    Map<String, String> extra = new HashMap<>();
    ArrayTagSet initial = ArrayTagSet.create("k3", "v3");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1")
        .addAll(ArrayTagSet.create("k2", "v2"))
        .addAll(ArrayTagSet.create("k3", "v3"));

    extra.put("k1", "v1");
    extra.put("k2", "v2");
    ArrayTagSet actual = initial.addAll(extra);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void addAllTagArrayEmpty() {
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(new Tag[0]);
    Assertions.assertSame(ArrayTagSet.EMPTY, ts);
  }

  @Test
  public void addAllStringArrayEmpty() {
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(new String[0]);
    Assertions.assertSame(ArrayTagSet.EMPTY, ts);
  }

  @Test
  public void addAllIterable() {
    // Add an arbitrary iterable that isn't a collection or ArrayTagSet
    Collection<Tag> tags = Collections.singletonList(new BasicTag("app", "foo"));
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(tags::iterator);
    Assertions.assertEquals(ArrayTagSet.EMPTY.addAll(tags), ts);
  }

  @Test
  public void addAllDedupEmpty() {
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(new String[] {"a", "1", "a", "2", "a", "3"});
    Assertions.assertEquals(ArrayTagSet.EMPTY.add(new BasicTag("a", "3")), ts);
  }

  @Test
  public void addAllDedupMerge() {
    ArrayTagSet ts = ArrayTagSet.EMPTY
        .addAll(new String[] {"a", "1", "a", "2", "a", "3"})
        .addAll(new String[] {"a", "4", "a", "5", "a", "6", "b", "1"});
    ArrayTagSet expected = ArrayTagSet.EMPTY
        .add(new BasicTag("a", "6"))
        .add(new BasicTag("b", "1"));
    Assertions.assertEquals(expected, ts);
  }

  @Test
  public void tagListIterate() {
    ArrayTagSet expected = ArrayTagSet.create("a", "1", "b", "2", "c", "3", "d", "4", "e", "5");
    ArrayTagSet actual = ArrayTagSet.EMPTY;
    for (Tag t : expected) {
      actual = actual.add(t);
    }
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void tagListForEach() {
    ArrayTagSet expected = ArrayTagSet.create("a", "1", "b", "2", "c", "3", "d", "4", "e", "5");
    List<Tag> tmp = new ArrayList<>();
    expected.forEach((k, v) -> {
      tmp.add(Tag.of(k, v));
    });
    Assertions.assertEquals(expected, ArrayTagSet.create(tmp));
  }
}
