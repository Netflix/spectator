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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class DefaultIdTest {

  @Test(expected = NullPointerException.class)
  public void testNullName() {
    new DefaultId(null);
  }

  @Test
  public void testName() {
    Id id = new DefaultId("foo");
    Assert.assertEquals(id.name(), "foo");
  }

  @Test
  public void testTags() {
    ArrayTagSet ts = ArrayTagSet.create("k1", "v1");
    Id id = new DefaultId("foo", ts);
    Assert.assertEquals(id.name(), "foo");
    Assert.assertEquals(id.tags(), ts);
  }

  @Test
  public void testTagsEmpty() {
    Id id = new DefaultId("foo");
    Assert.assertFalse(id.tags().iterator().hasNext());
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
    Assert.assertEquals(id12, id21);
    Assert.assertEquals(id12, id21.normalize());
  }

  @Test
  public void testRollup() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    keys.add("foo");
    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    DefaultId keepId = (new DefaultId("foo")).withTag("k1", "v1");
    DefaultId dropId = (new DefaultId("foo")).withTag("k2", "v2");
    Assert.assertEquals(keepId, id.rollup(keys, true));
    Assert.assertEquals(dropId, id.rollup(keys, false));
  }

  @Test
  public void testRollupJustName() {
    DefaultId id = new DefaultId("foo");
    Assert.assertSame(id, id.normalize());
  }

  @Test
  public void testRollupDeduping() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    DefaultId idWithDupes = (new DefaultId("foo")).withTag("k1", "v1").withTag("k1", "v2");
    DefaultId expectedId = (new DefaultId("foo")).withTag("k1", "v2");
    Assert.assertEquals(expectedId, idWithDupes.rollup(keys, true));
  }

  @Test
  public void testRollupDedupingOfExcludedKey() {
    Set<String> keys = new HashSet<>();
    keys.add("k1");
    DefaultId idWithDupes = (new DefaultId("foo")).withTag("k1", "v1").withTag("k1", "v2");
    DefaultId expectedId = new DefaultId("foo");
    Assert.assertEquals(expectedId, idWithDupes.rollup(keys, false));
  }

  @Test
  public void testToString() {
    DefaultId id = (new DefaultId("foo")).withTag("k1", "v1").withTag("k2", "v2");
    Assert.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void testToStringNameOnly() {
    DefaultId id = new DefaultId("foo");
    Assert.assertEquals(id.toString(), "foo");
  }

  @Test
  public void testWithTagsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    DefaultId id = (new DefaultId("foo")).withTags(map);
    Assert.assertEquals("foo:k1=v1:k2=v2", id.toString());
  }

  @Test
  public void addTagAppend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("exception.thrown", "pvr");
    Assert.assertEquals("TotalTime:app=foo:exception.thrown=pvr", id.toString());
  }

  @Test
  public void addTagPrepend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("aaa", "pvr");
    Assert.assertEquals("TotalTime:aaa=pvr:app=foo", id.toString());
  }

  @Test
  public void addTagInsert() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTag("exception.thrown", "pvr")
        .withTag("bbb", "bar");
    Assert.assertEquals("TotalTime:app=foo:bbb=bar:exception.thrown=pvr", id.toString());
  }

  @Test
  public void dedupAndAppend() {
    Id id = new DefaultId("TotalTime")
        .withTag("app", "foo")
        .withTags("app", "foo")
        .withTag("exception.thrown", "pvr");
    Assert.assertEquals("TotalTime:app=foo:exception.thrown=pvr", id.toString());
  }
}
