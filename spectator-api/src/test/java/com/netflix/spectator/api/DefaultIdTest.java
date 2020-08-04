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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.AccessMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIdTest {

  @Test
  public void testNullName() {
    Assertions.assertThrows(NullPointerException.class, () -> new DefaultId(null));
  }

  @Test
  public void testName() {
    Id id = new DefaultId("foo");
    Assertions.assertEquals(id.name(), "foo");
  }

  @Test
  public void testTags() {
    ArrayTagSet ts = ArrayTagSet.create("k1", "v1");
    Id id = new DefaultId("foo", ts);
    Assertions.assertEquals(id.name(), "foo");
    Assertions.assertEquals(id.tags(), ts);
  }

  @Test
  public void testTagsEmpty() {
    Id id = new DefaultId("foo");
    Assertions.assertFalse(id.tags().iterator().hasNext());
  }

  @Test
  public void equalsContractTest() {
    ArrayTagSet ts1 = ArrayTagSet.create("k1", "v1");
    ArrayTagSet ts2 = ArrayTagSet.create("k2", "v2").addAll(ts1);
    EqualsVerifier
      .forClass(DefaultId.class)
      .withPrefabValues(ArrayTagSet.class, ts1, ts2)
      .suppress(Warning.NULL_FIELDS)
      .verify();
  }

  @Test
  public void testNormalize() {
    DefaultId id12 = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    DefaultId id21 = (new DefaultId("foo")).withTag("k1", "v1").withTags(id12.tags());
    Assertions.assertEquals(id12, id21);
    Assertions.assertEquals(id12, id21.normalize());
  }

  @Test
  public void testRollup() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    keys.add("foo");
    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    DefaultId keepId = (new DefaultId("foo")).withTag("k1", "v1");
    DefaultId dropId = (new DefaultId("foo")).withTag("k2", "v2");
    Assertions.assertEquals(keepId, id.rollup(keys, true));
    Assertions.assertEquals(dropId, id.rollup(keys, false));
  }

  @Test
  public void testRollupJustName() {
    DefaultId id = new DefaultId("foo");
    Assertions.assertSame(id, id.normalize());
  }

  @Test
  public void testRollupDeduping() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    DefaultId idWithDupes = (new DefaultId("foo")).withTag("k1", "v1").withTag("k1", "v2");
    DefaultId expectedId = (new DefaultId("foo")).withTag("k1", "v2");
    Assertions.assertEquals(expectedId, idWithDupes.rollup(keys, true));
  }

  @Test
  public void testRollupDedupingOfExcludedKey() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    DefaultId idWithDupes = (new DefaultId("foo")).withTag("k1", "v1").withTag("k1", "v2");
    DefaultId expectedId = new DefaultId("foo");
    Assertions.assertEquals(expectedId, idWithDupes.rollup(keys, false));
  }

  @Test
  public void testToString() {
    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    Assertions.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void testToStringNameOnly() {
    DefaultId id = new DefaultId("foo");
    Assertions.assertEquals(id.toString(), "foo");
  }

  @Test
  public void testWithTagsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    DefaultId id = (new DefaultId("foo")).withTags(map);
    Assertions.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void addTagAppend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("exception.thrown", "pvr");
    Assertions.assertEquals("TotalTime:app=foo:exception.thrown=pvr", id.toString());
  }

  @Test
  public void addTagPrepend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("aaa", "pvr");
    Assertions.assertEquals("TotalTime:aaa=pvr:app=foo", id.toString());
  }

  @Test
  public void addTagInsert() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("exception.thrown", "pvr")
        .withTag("bbb", "bar");
    Assertions.assertEquals("TotalTime:app=foo:bbb=bar:exception.thrown=pvr", id.toString());
  }

  @Test
  public void dedupAndAppend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTags("app", "foo")
        .withTag("exception.thrown", "pvr");
    Assertions.assertEquals("TotalTime:app=foo:exception.thrown=pvr", id.toString());
  }

  @Test
  public void withTagBooleanTrue() {
    Id id = new DefaultId("test").withTag("bool", true);
    Assertions.assertEquals("test:bool=true", id.toString());
    Assertions.assertEquals(new DefaultId("test").withTag("bool", "true"), id);
  }

  @Test
  public void withTagBooleanFalse() {
    Id id = new DefaultId("test").withTag("bool", false);
    Assertions.assertEquals("test:bool=false", id.toString());
    Assertions.assertEquals(new DefaultId("test").withTag("bool", "false"), id);
  }

  @Test
  public void withTagBooleanObjFalse() {
    Id id = new DefaultId("test").withTag("bool", Boolean.FALSE);
    Assertions.assertEquals("test:bool=false", id.toString());
    Assertions.assertEquals(new DefaultId("test").withTag("bool", "false"), id);
  }

  @Test
  public void withTagBooleanObjNull() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      Boolean value = null;
      new DefaultId("test").withTag("bool", value);
    });
  }
  
  @Test
  public void withTagEnum() {
    Id id = new DefaultId("test").withTag("enum", AccessMode.WRITE);
    Assertions.assertEquals("test:enum=WRITE", id.toString());
    Assertions.assertEquals(new DefaultId("test").withTag("enum", "WRITE"), id);
  }

  @Test
  public void withTagEnumNull() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      Enum<?> value = null;
      new DefaultId("test").withTag("enum", value);
    });
  }

  @Test
  public void issue483() {
    // Before calling hashCode on this id caused NPE
    Id id1 = new DefaultId("test")
        .withTag("api_name", "foo")
        .withTags(Statistic.percentile, new BasicTag("percentile", "1"));

    // Forces deduping to improve coverage
    Id id2 = new DefaultId("test")
        .withTag("api_name", "foo")
        .withTags(
            Statistic.percentile, new BasicTag("percentile", "2"),
            Statistic.percentile, new BasicTag("percentile", "1"));

    Assertions.assertEquals(id1.hashCode(), id2.hashCode());
    Assertions.assertEquals(id1, id2);
  }

  @Test
  public void issue483_2() {
    // Another sanity check using the full key set
    Id id1 = new DefaultId("test")
        .withTag("api_name", "foo")
        .withTag("api_partner", "3")
        .withTag("api_status", "failed")
        .withTag("api_http_code", "400")
        .withTag("api_error_code", "BadRequest")
        .withTags(Statistic.percentile, new BasicTag("percentile", "1"));

    // Forces deduping to improve coverage
    Id id2 = new DefaultId("test")
        .withTag("api_name", "foo")
        .withTag("api_partner", "3")
        .withTag("api_status", "failed")
        .withTag("api_http_code", "400")
        .withTag("api_error_code", "BadRequest")
        .withTags(
            Statistic.percentile, new BasicTag("percentile", "2"),
            Statistic.percentile, new BasicTag("percentile", "1"));

    Assertions.assertEquals(id1.hashCode(), id2.hashCode());
    Assertions.assertEquals(id1, id2);
  }

  @Test
  public void tagListIterator() {
    List<Tag> expected = new ArrayList<>();
    expected.add(Tag.of("name", "foo"));
    expected.add(Tag.of("k1", "v1"));
    expected.add(Tag.of("k2", "v2"));

    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    List<Tag> actual = new ArrayList<>();
    for (Tag t : id) {
      actual.add(t);
    }

    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void tagListForEach() {
    List<Tag> expected = new ArrayList<>();
    expected.add(Tag.of("name", "foo"));
    expected.add(Tag.of("k1", "v1"));
    expected.add(Tag.of("k2", "v2"));

    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    List<Tag> actual = new ArrayList<>();
    id.forEach((k, v) -> actual.add(Tag.of(k, v)));

    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void tagListFilter() {
    DefaultId id = new DefaultId("foo", ArrayTagSet.create("a", "1", "b", "2"));
    Id expected = Id.create("foo").withTag("a", "1");
    Assertions.assertEquals(expected, id.filter((k, v) -> !k.equals("b")));
  }

  @Test
  public void tagListFilterByKey() {
    DefaultId id = new DefaultId("foo", ArrayTagSet.create("a", "1", "b", "2"));
    Id expected = Id.create("foo").withTag("a", "1");
    Assertions.assertEquals(expected, id.filterByKey(k -> !k.equals("b")));
  }

  @Test
  public void tagListFilterByKeyName() {
    // Name is required and is ignored for filtering
    DefaultId id = new DefaultId("foo", ArrayTagSet.create("a", "1", "b", "2"));
    Assertions.assertEquals(id, id.filterByKey(k -> !k.equals("name")));
  }
}
