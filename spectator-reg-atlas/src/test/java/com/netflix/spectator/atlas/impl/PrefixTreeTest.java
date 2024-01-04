/*
 * Copyright 2014-2024 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PrefixTreeTest {

  private List<Query.KeyQuery> list(String... values) {
    return Arrays.stream(values).map(PrefixTreeTest::re).collect(Collectors.toList());
  }

  private List<Query.KeyQuery> sort(List<Query.KeyQuery> values) {
    values.sort(Comparator.comparing(Object::toString));
    return values;
  }

  private static Query.KeyQuery re(String s) {
    return new Query.Regex("name", "^" + s);
  }

  private void assertSize(PrefixTree tree, int expected) {
    Assertions.assertEquals(expected, tree.size());
    Assertions.assertEquals(expected == 0, tree.isEmpty());
  }

  @Test
  public void nullPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put(null, re("1"));
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("foo"));
    Assertions.assertEquals(list("1"), tree.get("bar"));
    Assertions.assertEquals(list("1"), tree.get(""));

    Assertions.assertFalse(tree.remove(null, re("2")));
    Assertions.assertTrue(tree.remove(null, re("1")));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("foo"));
  }

  @Test
  public void emptyPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("", re("1"));
    Assertions.assertEquals(list("1"), tree.get("foo"));
    Assertions.assertEquals(list("1"), tree.get("bar"));
    Assertions.assertEquals(list("1"), tree.get(""));

    Assertions.assertFalse(tree.remove("", re("2")));
    Assertions.assertTrue(tree.remove("", re("1")));
    Assertions.assertEquals(Collections.emptyList(), tree.get("foo"));
  }

  @Test
  public void simplePrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("abcdef"));
    Assertions.assertEquals(list("1"), tree.get("abcghi"));
    Assertions.assertEquals(list("1"), tree.get("abc"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abd"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("ab"));

    Assertions.assertTrue(tree.remove("abc", re("1")));
    Assertions.assertFalse(tree.remove("abc", re("1")));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcdef"));
  }

  @Test
  public void multipleMatches() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    tree.put("ab", re("2"));
    tree.put("a", re("3"));
    tree.put("abc", re("4"));
    assertSize(tree, 4);

    Assertions.assertEquals(list("1", "2", "3", "4"), sort(tree.get("abcdef")));
    Assertions.assertEquals(list("2", "3"), sort(tree.get("abdef")));
    Assertions.assertEquals(list("3"), tree.get("adef"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("bcdef"));

    Assertions.assertFalse(tree.remove("ab", re("1")));
    Assertions.assertTrue(tree.remove("abc", re("1")));
    assertSize(tree, 3);
    Assertions.assertEquals(list("2", "3", "4"), sort(tree.get("abcdef")));
  }

  @Test
  public void unicodeCharInPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("aβc", re("1"));
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("aβcdef"));
    Assertions.assertEquals(list("1"), tree.get("aβcghi"));
    Assertions.assertEquals(list("1"), tree.get("aβc"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcdef"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcghi"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abc"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abd"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("ab"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("aβ"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("b"));

    Assertions.assertTrue(tree.remove("aβc", re("1")));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcdef"));
  }

  @Test
  public void emptyRootPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("ab", re("ab"));
    tree.put("cd", re("cd"));

    Assertions.assertEquals(list("ab"), tree.get("ab"));
    Assertions.assertEquals(list("cd"), tree.get("cd"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("ef"));

    Assertions.assertTrue(tree.remove("ab", re("ab")));
    PrefixTree tree2 = new PrefixTree();
    tree2.put("cd", re("cd"));
    Assertions.assertEquals(tree2, tree);

    Assertions.assertEquals(Collections.emptyList(), tree.get("ab"));
    Assertions.assertEquals(list("cd"), tree.get("cd"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("ef"));
  }

  @Test
  public void updateExistingNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abcdef", re("1"));
    tree.put("abcdef", re("2"));

    Assertions.assertEquals(list("1", "2"), tree.get("abcdef"));
  }

  @Test
  public void addChildNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    tree.put("abcdefghi", re("2"));
    tree.put("abcdef", re("3"));

    Assertions.assertEquals(list("1", "3"), tree.get("abcdef"));
    Assertions.assertEquals(list("1", "3", "2"), tree.get("abcdefghi"));
  }

  @Test
  public void splitInteriorNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abcdef", re("abcdef"));
    tree.put("abcghi", re("abcghi"));

    Assertions.assertEquals(list("abcdef"), tree.get("abcdef"));
    Assertions.assertEquals(list("abcghi"), tree.get("abcghi"));
  }

  @Test
  public void manyDifferentRootPrefixes() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("abc"));
    tree.put("def", re("def"));
    tree.put("ghi", re("ghi"));
    Assertions.assertEquals(list("abc"), tree.get("abcdef"));
  }

  @Test
  public void commonPrefixNoMatch() {
    Assertions.assertEquals(0, PrefixTree.commonPrefixLength("abcdef", "defghi", 0));
    Assertions.assertEquals(0, PrefixTree.commonPrefixLength("abcdef", "abcdef", 3));
  }

  @Test
  public void commonPrefixEmpty() {
    Assertions.assertEquals(0, PrefixTree.commonPrefixLength("abcdef", "", 0));
    Assertions.assertEquals(0, PrefixTree.commonPrefixLength("", "abcdef", 0));
  }

  @Test
  public void commonPrefix() {
    Assertions.assertEquals(3, PrefixTree.commonPrefixLength("abcdef", "abcghi", 0));
    Assertions.assertEquals(6, PrefixTree.commonPrefixLength("defghi", "abcdefghi", 3));
  }
}
