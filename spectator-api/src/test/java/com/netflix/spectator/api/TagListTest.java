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
public class TagListTest {

    @Test
    public void equalsContractTest() {
        // NOTE: EqualsVerifier doesn't work with cached hash code
        TagList ts1 = new TagList("k1", "v1");
        TagList ts2 = new TagList("k2", "v2").mergeTag(ts1);
        Assert.assertEquals(ts1, ts1);
        Assert.assertEquals(ts2, ts2);
        Assert.assertNotEquals(ts1, null);
        Assert.assertNotEquals(ts1, new Object());
        Assert.assertNotEquals(ts1, new TagList("k1", "v2"));
        Assert.assertNotEquals(ts1, new TagList("k2", "v1"));
        Assert.assertNotEquals(ts1, new TagList("k1", "v1").mergeList(ts2));
        Assert.assertEquals(ts2, new TagList("k2", "v2").mergeTag(ts1));
        Assert.assertEquals(ts2, new TagList("k2", "v2").mergeTag(new TagList("k1", "v1")));
    }

    @Test
    public void testToString() {
        TagList ts1 = new TagList("k1", "v1");
        TagList ts2 = new TagList("k2", "v2").mergeTag(ts1);
        TagList ts3 = new TagList("k3", "v3").mergeList(ts2);

        Assert.assertEquals("k1=v1", ts1.toString());
        Assert.assertEquals("k1=v1:k2=v2", ts2.toString());
        Assert.assertEquals("k1=v1:k2=v2:k3=v3", ts3.toString());
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

    @Test(expected = UnsupportedOperationException.class)
    public void testIteratorRemoveUnsupported() {
        new TagList("k", "v").iterator().remove();
    }

    @Test(expected = NoSuchElementException.class)
    public void testIteratorNext() {
        TagList tag = new TagList("k", "v");
        Iterator<Tag> iter = tag.iterator();

        Assert.assertTrue(iter.hasNext());
        Assert.assertSame(tag, iter.next());
        Assert.assertFalse(iter.hasNext());
        iter.next();
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
    public void testCreateFromMapWithMultipleValues() {
        Map<String, String> m = new HashMap<>();
        m.put("k1", "v1");
        m.put("k2", "v2");
        TagList ts1 = TagList.create(m);
        TagList ts2 = new TagList("k1", "v1").mergeTag(new TagList("k2", "v2"));
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
    public void testCreateFromEmptyIterable() {
        Assert.assertEquals(TagList.EMPTY, TagList.create(Collections.emptyList()));
    }

    @Test
    public void testCreateFromSingleValueIterable() {
        Collection<Tag> coll = Collections.singleton(new TagList("k", "v"));
        TagList ts1 = TagList.create(coll);
        TagList ts2 = new TagList("k", "v");
        Assert.assertEquals(ts1, ts2);
    }

    @Test
    public void testCreateFromMultiValueIterable() {
        List<Tag> coll = new ArrayList<>();
        coll.add(new TagList("k1", "v1"));
        coll.add(new TagList("k2", "v2"));
        TagList ts1 = TagList.create(coll);
        TagList ts2 = new TagList("k1", "v1").mergeTag(new TagList("k2", "v2"));
        Assert.assertEquals(ts1, ts2);
    }

    @Test
    public void testCreateFromEmptyMap() {
        Assert.assertEquals(TagList.EMPTY, TagList.create(Collections.emptyMap()));
    }

    @Test
    public void testCreateFromSingleValueMap() {
        Map<String, String> tags = new HashMap<>();

        tags.put("k", "v");
        Assert.assertEquals(new TagList("k", "v"), TagList.create(tags));
    }

    @Test
    public void testCreateFromMultiValueMap() {
        Map<String, String> tags = new HashMap<>();

        tags.put("k1", "v1");
        tags.put("k2", "v2");
        Assert.assertEquals(new TagList("k1", "v1").mergeTag(new TagList("k2", "v2")), TagList.create(tags));
    }

    @Test
    public void testMergeNullTag() {
        TagList expected = new TagList("k", "v");

        Assert.assertSame(expected, expected.mergeTag(null));
    }

    @Test
    public void testMergeTag() {
        TagList initial = new TagList("k2", "v2");
        TagList update = new TagList("k1", "v1");
        TagList expected = new TagList("k1", "v1").mergeTag(new TagList("k2", "v2"));

        Assert.assertEquals(expected, initial.mergeTag(update));
    }

    @Test
    public void testMergeTagWithSameKey() {
        Iterable<Tag> prefix = Collections.singletonList(new TagList("k1", "v1"));
        TagList initial = new TagList("k1", "v1");
        TagList expected = new TagList("k1", "v2");
        TagList actual = initial.mergeTag(expected);

        Assert.assertNotSame(expected, actual);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testMergeMultipleValuesAsList() {
        ArrayList<Tag> prefix = new ArrayList<>();
        TagList initial = new TagList("k3", "v3");
        TagList expected = new TagList("k1", "v1").mergeTag(new TagList("k2", "v2")).mergeTag(new TagList("k3", "v3"));

        prefix.add(new TagList("k1", "v1"));
        prefix.add(new TagList("k2", "v2"));
        TagList actual = initial.mergeList(prefix);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testMergeMultipleValuesAsMap() {
        Map<String, String> extra = new HashMap<>();
        TagList initial = new TagList("k3", "v3");
        TagList expected = new TagList("k1", "v1").mergeTag(new TagList("k2", "v2")).mergeTag(new TagList("k3", "v3"));

        extra.put("k1", "v1");
        extra.put("k2", "v2");
        TagList actual = initial.mergeMap(extra);
        Assert.assertEquals(expected, actual);
    }
}
