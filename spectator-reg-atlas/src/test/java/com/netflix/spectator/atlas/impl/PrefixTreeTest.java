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

  private void assertEquals(List<Query.KeyQuery> expected, PrefixTree tree, String key) {
    List<Query.KeyQuery> actual = sort(tree.get(key));
    if (!actual.isEmpty()) {
      Assertions.assertTrue(tree.exists(key, k -> true));
    } else {
      Assertions.assertFalse(tree.exists(key, k -> true));
    }
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void nullPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put(null, re("1"));
    assertSize(tree, 1);

    assertEquals(list("1"), tree, "foo");
    assertEquals(list("1"), tree, "bar");
    assertEquals(list("1"), tree, "");

    Assertions.assertFalse(tree.remove(null, re("2")));
    Assertions.assertTrue(tree.remove(null, re("1")));
    assertSize(tree, 0);
    assertEquals(Collections.emptyList(), tree, "foo");
  }

  @Test
  public void emptyPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("", re("1"));
    assertEquals(list("1"), tree, "foo");
    assertEquals(list("1"), tree, "bar");
    assertEquals(list("1"), tree, "");

    Assertions.assertFalse(tree.remove("", re("2")));
    Assertions.assertTrue(tree.remove("", re("1")));
    assertEquals(Collections.emptyList(), tree, "foo");
  }

  @Test
  public void simplePrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    assertSize(tree, 1);

    assertEquals(list("1"), tree, "abcdef");
    assertEquals(list("1"), tree, "abcghi");
    assertEquals(list("1"), tree, "abc");
    assertEquals(Collections.emptyList(), tree, "abd");
    assertEquals(Collections.emptyList(), tree, "ab");

    Assertions.assertTrue(tree.remove("abc", re("1")));
    Assertions.assertFalse(tree.remove("abc", re("1")));
    assertSize(tree, 0);
    assertEquals(Collections.emptyList(), tree, "abcdef");
  }

  @Test
  public void multipleMatches() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    tree.put("ab", re("2"));
    tree.put("a", re("3"));
    tree.put("abc", re("4"));
    assertSize(tree, 4);

    assertEquals(list("1", "2", "3", "4"), tree, "abcdef");
    assertEquals(list("2", "3"), tree, "abdef");
    assertEquals(list("3"), tree, "adef");
    assertEquals(Collections.emptyList(), tree, "bcdef");

    Assertions.assertFalse(tree.remove("ab", re("1")));
    Assertions.assertTrue(tree.remove("abc", re("1")));
    assertSize(tree, 3);
    assertEquals(list("2", "3", "4"), tree, "abcdef");
  }

  @Test
  public void unicodeCharInPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("aβc", re("1"));
    assertSize(tree, 1);

    assertEquals(list("1"), tree, "aβcdef");
    assertEquals(list("1"), tree, "aβcghi");
    assertEquals(list("1"), tree, "aβc");
    assertEquals(Collections.emptyList(), tree, "abcdef");
    assertEquals(Collections.emptyList(), tree, "abcghi");
    assertEquals(Collections.emptyList(), tree, "abc");
    assertEquals(Collections.emptyList(), tree, "abd");
    assertEquals(Collections.emptyList(), tree, "ab");
    assertEquals(Collections.emptyList(), tree, "aβ");
    assertEquals(Collections.emptyList(), tree, "b");

    Assertions.assertTrue(tree.remove("aβc", re("1")));
    assertSize(tree, 0);
    assertEquals(Collections.emptyList(), tree, "abcdef");
  }

  @Test
  public void emptyRootPrefix() {
    PrefixTree tree = new PrefixTree();
    tree.put("ab", re("ab"));
    tree.put("cd", re("cd"));

    assertEquals(list("ab"), tree, "ab");
    assertEquals(list("cd"), tree, "cd");
    assertEquals(Collections.emptyList(), tree, "ef");

    Assertions.assertTrue(tree.remove("ab", re("ab")));
    PrefixTree tree2 = new PrefixTree();
    tree2.put("cd", re("cd"));
    Assertions.assertEquals(tree2, tree);

    assertEquals(Collections.emptyList(), tree, "ab");
    assertEquals(list("cd"), tree, "cd");
    assertEquals(Collections.emptyList(), tree, "ef");
  }

  @Test
  public void updateExistingNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abcdef", re("1"));
    tree.put("abcdef", re("2"));

    assertEquals(list("1", "2"), tree, "abcdef");
  }

  @Test
  public void addChildNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("1"));
    tree.put("abcdefghi", re("2"));
    tree.put("abcdef", re("3"));

    assertEquals(list("1", "3"), tree, "abcdef");
    assertEquals(list("1", "2", "3"), tree, "abcdefghi");
  }

  @Test
  public void splitInteriorNode() {
    PrefixTree tree = new PrefixTree();
    tree.put("abcdef", re("abcdef"));
    tree.put("abcghi", re("abcghi"));

    assertEquals(list("abcdef"), tree, "abcdef");
    assertEquals(list("abcghi"), tree, "abcghi");
  }

  @Test
  public void manyDifferentRootPrefixes() {
    PrefixTree tree = new PrefixTree();
    tree.put("abc", re("abc"));
    tree.put("def", re("def"));
    tree.put("ghi", re("ghi"));
    assertEquals(list("abc"), tree, "abcdef");
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
