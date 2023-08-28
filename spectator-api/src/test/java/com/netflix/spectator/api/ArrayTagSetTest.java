/*
 * Copyright 2014-2020 Netflix, Inc.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
  public void testHashCode() {
    Assertions.assertEquals(1, ArrayTagSet.EMPTY.hashCode());
    Assertions.assertEquals(ArrayTagSet.create("k1", "v1", "k2", "v2"), ArrayTagSet.create("k2", "v2", "k1", "v1"));
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
  public void testAddNullKey() {
    ArrayTagSet ts = ArrayTagSet.EMPTY;
    Assertions.assertThrows(NullPointerException.class, () -> ts.add(null, "v"));
  }

  @Test
  public void testAddNullValue() {
    ArrayTagSet ts = ArrayTagSet.EMPTY;
    Assertions.assertThrows(NullPointerException.class, () -> ts.add("k", null));
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
  public void testMergeWithSelf() {
    ArrayTagSet tags = ArrayTagSet.create("k1", "v1");
    ArrayTagSet updated = tags.addAll(tags);
    Assertions.assertSame(tags, updated);
  }

  @Test
  public void testMergeWithEmpty() {
    ArrayTagSet tags = ArrayTagSet.create("k1", "v1");
    ArrayTagSet updated = tags.addAll(ArrayTagSet.EMPTY);
    Assertions.assertSame(tags, updated);
  }

  @Test
  public void testEmptyMergeWithNonEmpty() {
    ArrayTagSet tags = ArrayTagSet.create("k1", "v1");
    ArrayTagSet updated = ArrayTagSet.EMPTY.addAll(tags);
    Assertions.assertSame(tags, updated);
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
  public void addAllStringArrayMutate() {
    String[] vs = {"a", "b"};
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(vs);
    vs[0] = "c";
    Assertions.assertEquals(ArrayTagSet.create("a", "b"), ts);
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
    expected.forEach((k, v) -> tmp.add(Tag.of(k, v)));
    Assertions.assertEquals(expected, ArrayTagSet.create(tmp));
  }

  @Test
  public void tagListFilter() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filter((k, v) -> !v.equals("2"));
    Assertions.assertEquals(ArrayTagSet.create("a", "1", "c", "3"), filtered);
  }

  @Test
  public void tagListFilterMatchAll() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filter((k, v) -> true);
    Assertions.assertEquals(ArrayTagSet.create("a", "1", "b", "2", "c", "3"), filtered);
  }

  @Test
  public void tagListFilterMatchNone() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filter((k, v) -> false);
    Assertions.assertEquals(ArrayTagSet.EMPTY, filtered);
  }

  @Test
  public void tagListFilterByKey() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filterByKey(k -> !k.equals("b"));
    Assertions.assertEquals(ArrayTagSet.create("a", "1", "c", "3"), filtered);
  }

  @Test
  public void tagListFilterByKeyMatchAll() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filterByKey(k -> true);
    Assertions.assertEquals(ArrayTagSet.create("a", "1", "b", "2", "c", "3"), filtered);
  }

  @Test
  public void tagListFilterByKeyMatchNone() {
    ArrayTagSet filtered = ArrayTagSet
        .create("a", "1", "b", "2", "c", "3")
        .filterByKey(k -> false);
    Assertions.assertEquals(ArrayTagSet.EMPTY, filtered);
  }

  @Test
  public void addAllConcurrentMap() {
    Map<String, String> tags = new ConcurrentHashMap<>();
    tags.put("app", "foo");
    ArrayTagSet ts = ArrayTagSet.EMPTY.addAll(tags);
    Assertions.assertEquals(ArrayTagSet.EMPTY.add("app", "foo"), ts);
  }

  @Test
  public void addAllConcurrentMapFailure() throws InterruptedException {
    // This test just checks that we do not throw if the map is being modified concurrently.
    // It seems to fail reliably when testing prior to the patch.
    // https://github.com/Netflix/spectator/issues/733
    final AtomicBoolean done = new AtomicBoolean(false);
    final Map<String, String> tags = new ConcurrentHashMap<>();

    Thread t1 = new Thread(() -> {
      while (!done.get()) {
        tags.remove("app");
      }
    });
    t1.start();

    Thread t2 = new Thread(() -> {
      while (!done.get()) {
        tags.put("app", "foo");
      }
    });
    t2.start();

    try {
      for (int i = 0; i < 10_000; ++i) {
        ArrayTagSet.EMPTY.addAll(tags);
      }
    } finally {
      done.set(true);
      t1.join();
      t2.join();
    }
  }

  @Test
  public void compareToEmpty() {
    Assertions.assertEquals(0, ArrayTagSet.EMPTY.compareTo(ArrayTagSet.EMPTY));
  }

  @Test
  public void compareToEquals() {
    ArrayTagSet a = ArrayTagSet.create("a", "1", "b", "2", "c", "3");
    ArrayTagSet b = ArrayTagSet.create("a", "1", "b", "2", "c", "3");
    Assertions.assertEquals(0, a.compareTo(b));
  }

  @Test
  public void compareToDifferentKeys() {
    ArrayTagSet a = ArrayTagSet.create("a", "1", "b", "2", "c", "3");
    ArrayTagSet b = ArrayTagSet.create("a", "1", "d", "2", "c", "3");
    Assertions.assertEquals(-1, a.compareTo(b));
    Assertions.assertEquals(1, b.compareTo(a));
  }

  @Test
  public void compareToDifferentValues() {
    ArrayTagSet a = ArrayTagSet.create("a", "1", "b", "3", "c", "3");
    ArrayTagSet b = ArrayTagSet.create("a", "1", "b", "2", "c", "3");
    Assertions.assertEquals(1, a.compareTo(b));
    Assertions.assertEquals(-1, b.compareTo(a));
  }

  @Test
  public void compareToDifferentSizes() {
    ArrayTagSet a = ArrayTagSet.create("a", "1", "b", "2", "c", "3");
    ArrayTagSet b = ArrayTagSet.create("a", "1", "b", "2", "c", "3", "d", "4");
    Assertions.assertEquals(-1, a.compareTo(b));
    Assertions.assertEquals(1, b.compareTo(a));
  }

  @Test
  public void mergeTagList() {
    TagList tagList = new TagList() {
      @Override
      public String getKey(int i) {
        return "k" + ++i;
      }

      @Override
      public String getValue(int i) {
        return "v" + ++i;
      }

      @Override
      public int size() {
        return 3;
      }
    };
    ArrayTagSet tags = ArrayTagSet.create("k1", "v1", "k2", "v2");
    ArrayTagSet updated = tags.addAll(tagList);
    ArrayTagSet expected = ArrayTagSet.create("k1", "v1", "k2", "v2", "k3", "v3");
    Assertions.assertEquals(expected, updated);
  }

  @Test
  public void mergeEmptyTagList() {
    TagList empty = new TagList() {
      @Override
      public String getKey(int i) {
        throw new IndexOutOfBoundsException();
      }

      @Override
      public String getValue(int i) {
        throw new IndexOutOfBoundsException();
      }

      @Override
      public int size() {
        return 0;
      }
    };

    ArrayTagSet tags = ArrayTagSet.create("k1", "v1", "k2", "v2");
    ArrayTagSet updated = tags.addAll(empty);

    Assertions.assertSame(tags, updated);
  }

  @Test
  public void spliteratorSizeAndCharacteristics() {
    ArrayTagSet tags = ArrayTagSet.create("k1", "v1", "k2", "v2", "k3", "v3");
    Spliterator<Tag> spliterator = tags.spliterator();
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.SORTED));
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL));
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED));
    Assertions.assertEquals(3L, spliterator.getExactSizeIfKnown());
  }

  @Test
  public void testCreateWithSortedMap() {
    SortedMap<String, String> tagMap = new TreeMap<>();
    tagMap.put("a", "v1");
    tagMap.put("b", "v2");
    tagMap.put("c", "v3");
    tagMap.put("d", "v4");

    ArrayTagSet tags = ArrayTagSet.create(tagMap);

    List<Tag> tagList = StreamSupport.stream(tags.spliterator(), false).collect(Collectors.toList());
    Assertions.assertEquals(Arrays.asList(Tag.of("a", "v1"), Tag.of("b", "v2"), Tag.of("c", "v3"), Tag.of("d", "v4")), tagList);
  }

  @Test
  public void testCreateWithSortedMapCustomComparator() {
    SortedMap<String, String> tagMap = new TreeMap<>(Comparator.reverseOrder());
    tagMap.put("a", "v1");
    tagMap.put("b", "v2");
    tagMap.put("c", "v3");
    tagMap.put("d", "v4");

    ArrayTagSet tags = ArrayTagSet.create(tagMap);

    List<Tag> tagList = StreamSupport.stream(tags.spliterator(), false).collect(Collectors.toList());
    Assertions.assertEquals(Arrays.asList(Tag.of("a", "v1"), Tag.of("b", "v2"), Tag.of("c", "v3"), Tag.of("d", "v4")), tagList);
  }

  @Test
  public void testCreateWithSortedMapCustomComparatorNaturalOrder() {
    SortedMap<String, String> tagMap = new TreeMap<>(Comparator.naturalOrder());
    tagMap.put("a", "v1");
    tagMap.put("b", "v2");
    tagMap.put("c", "v3");
    tagMap.put("d", "v4");

    ArrayTagSet tags = ArrayTagSet.create(tagMap);

    List<Tag> tagList = StreamSupport.stream(tags.spliterator(), false).collect(Collectors.toList());
    Assertions.assertEquals(Arrays.asList(Tag.of("a", "v1"), Tag.of("b", "v2"), Tag.of("c", "v3"), Tag.of("d", "v4")), tagList);
  }

  @Test
  public void addReplace() {
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k1", "v2");
    ArrayTagSet expected = ArrayTagSet.create("k1", "v2");
    Assertions.assertEquals(expected, ts1.addAll(ts2));

    List<Tag> list = new ArrayList<>();
    list.add(new BasicTag("k1", "v3"));
    list.add(new BasicTag("k1", "v2"));
    Assertions.assertEquals(expected, ts1.addAll(list));

    list.clear();
    list.add(new BasicTag("k1", "v3"));
    list.add(new BasicTag("k1", "v2"));
    Assertions.assertEquals(expected, ArrayTagSet.create(new HashMap<>()).addAll(list));

    Assertions.assertEquals(expected, expected.addAll(new ArrayList<>()));

    Assertions.assertEquals(expected, expected.addAll(new HashMap<>()));

    Assertions.assertEquals(expected, ts1.addAll(new BadTagList("k1", "v2")));

    Assertions.assertEquals(expected, ts1.addAll(new TagIterator("k1", "v2")));

    Assertions.assertEquals(expected, ts1.addAll(new Tag[] { new BasicTag("k1", "v2")}));

    ConcurrentHashMap<String, String> cMap = new ConcurrentHashMap<>();
    cMap.put("k1", "v2");
    Assertions.assertEquals(expected, ts1.addAll(cMap));

    HashMap<String, String> hMap = new HashMap<>();
    hMap.put("k1", "v2");
    Assertions.assertEquals(expected, ts1.addAll(hMap));

    Assertions.assertEquals(expected, ts1.add("k1", "v2"));

    Assertions.assertEquals(expected, ts1.add(new BasicTag("k1", "v2")));

    Assertions.assertEquals(expected, ArrayTagSet.create("k1", "v1", "k1", "v2"));
  }

  @Test
  public void testAddBeginning() {
    ArrayTagSet tags = ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3");
    ArrayTagSet updated = tags.add("0", "v0");
    Assertions.assertEquals(ArrayTagSet.create("0", "v0", "a", "v1", "b", "v2", "c", "v3"), updated);
  }

  @Test
  public void testAddEnd() {
    ArrayTagSet tags = ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3");
    ArrayTagSet updated = tags.add("d", "v4");
    Assertions.assertEquals(ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3", "d", "v4"), updated);
  }

  @Test
  public void testAddMiddle() {
    ArrayTagSet tags = ArrayTagSet.create("a", "v1", "b", "v2", "d", "v4");
    ArrayTagSet updated = tags.add("c", "v3");
    Assertions.assertEquals(ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3", "d", "v4"), updated);
  }

  @Test
  public void testAddUpdatesExisting() {
    ArrayTagSet tags = ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3");
    ArrayTagSet updated = tags.add("c", "v3-updated");
    Assertions.assertEquals(ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3-updated"), updated);
  }

  @Test
  public void testAddUpdatesExistingWithSameTag() {
    ArrayTagSet tags = ArrayTagSet.create("a", "v1", "b", "v2", "c", "v3");
    ArrayTagSet updated = tags.add("c", "v3");
    Assertions.assertSame(tags, updated);
  }

  @Test
  public void addAllDuplicates() {
    ArrayTagSet existing = ArrayTagSet.create("a", "foo");
    List<Tag> tags = Arrays.asList(
        Tag.of("b", "bar"),
        Tag.of("b", "ERROR")
    );
    ArrayTagSet updated = existing.addAll(tags);
    assertDistinct(updated);

    // <= 1.6.9 would throw here as the two values in the array list would be merged
    // into the result without checking for duplicates.
    existing = ArrayTagSet.create("b", "foo");
    tags = Arrays.asList(
        Tag.of("a", "bar"),
        Tag.of("a", "ERROR")
    );
    updated = existing.addAll(tags);
    assertDistinct(updated);

    existing = ArrayTagSet.create("b", "foo");
    tags = Arrays.asList(
        Tag.of("a", "bar"),
        Tag.of("b", "boo"),
        Tag.of("a", "ERROR")
    );
    updated = existing.addAll(tags);
    assertDistinct(updated);
  }

  class TagIterator implements Iterable<Tag> {
    String[] tags;
    TagIterator(String... tags) {
      this.tags = tags;
    }

    @Override
    public Iterator<Tag> iterator() {
      return new Iterator<Tag>() {
        int i = 0;

        @Override
        public boolean hasNext() {
          return i < tags.length;
        }

        @Override
        public Tag next() {
          return new BasicTag(tags[i++], tags[i++]);
        }
      };
    }
  }

  class BadTagList implements TagList {

    String[] tags;
    BadTagList(String... tags) {
      this.tags = tags;
    }

    @Override
    public String getKey(int i) {
      return tags[i * 2];
    }

    @Override
    public String getValue(int i) {
      return tags[(i * 2) + 1];
    }

    @Override
    public int size() {
      return tags.length / 2;
    }
  }

  private void assertDistinct(TagList tags) {
    try {
      StreamSupport.stream(tags.spliterator(), false)
          .collect(Collectors.toMap(Tag::key, Tag::value));
    } catch (Exception e) {
      throw new AssertionError("failed to convert tags to map", e);
    }
  }
}
