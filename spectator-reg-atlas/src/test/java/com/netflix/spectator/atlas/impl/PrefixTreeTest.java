/*
 * Copyright 2014-2022 Netflix, Inc.
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
import java.util.List;

public class PrefixTreeTest {

  private List<String> list(String... values) {
    return Arrays.asList(values);
  }

  private List<String> sort(List<String> values) {
    values.sort(String::compareTo);
    return values;
  }

  private void assertSize(PrefixTree<?> tree, int expected) {
    Assertions.assertEquals(expected, tree.size());
    Assertions.assertEquals(expected == 0, tree.isEmpty());
  }

  @Test
  public void nullPrefix() {
    PrefixTree<String> tree = new PrefixTree<>();
    tree.put(null, "1");
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("foo"));
    Assertions.assertEquals(list("1"), tree.get("bar"));
    Assertions.assertEquals(list("1"), tree.get(""));

    Assertions.assertFalse(tree.remove(null, "2"));
    Assertions.assertTrue(tree.remove(null, "1"));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("foo"));
  }

  @Test
  public void emptyPrefix() {
    PrefixTree<String> tree = new PrefixTree<>();
    tree.put("", "1");
    Assertions.assertEquals(list("1"), tree.get("foo"));
    Assertions.assertEquals(list("1"), tree.get("bar"));
    Assertions.assertEquals(list("1"), tree.get(""));

    Assertions.assertFalse(tree.remove("", "2"));
    Assertions.assertTrue(tree.remove("", "1"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("foo"));
  }

  @Test
  public void simplePrefix() {
    PrefixTree<String> tree = new PrefixTree<>();
    tree.put("abc", "1");
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("abcdef"));
    Assertions.assertEquals(list("1"), tree.get("abcghi"));
    Assertions.assertEquals(list("1"), tree.get("abc"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("abd"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("ab"));

    Assertions.assertTrue(tree.remove("abc", "1"));
    Assertions.assertFalse(tree.remove("abc", "1"));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcdef"));
  }

  @Test
  public void multipleMatches() {
    PrefixTree<String> tree = new PrefixTree<>();
    tree.put("abc", "1");
    tree.put("ab", "2");
    tree.put("a", "3");
    tree.put("abc", "4");
    assertSize(tree, 4);

    Assertions.assertEquals(list("1", "2", "3", "4"), sort(tree.get("abcdef")));
    Assertions.assertEquals(list("2", "3"), sort(tree.get("abdef")));
    Assertions.assertEquals(list("3"), tree.get("adef"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("bcdef"));

    Assertions.assertFalse(tree.remove("ab", "1"));
    Assertions.assertTrue(tree.remove("abc", "1"));
    assertSize(tree, 3);
    Assertions.assertEquals(list("2", "3", "4"), sort(tree.get("abcdef")));
  }

  @Test
  public void unsupportedCharInPrefix() {
    PrefixTree<String> tree = new PrefixTree<>();
    tree.put("aβc", "1");
    assertSize(tree, 1);

    Assertions.assertEquals(list("1"), tree.get("abcdef"));
    Assertions.assertEquals(list("1"), tree.get("abcghi"));
    Assertions.assertEquals(list("1"), tree.get("abc"));
    Assertions.assertEquals(list("1"), tree.get("abd"));
    Assertions.assertEquals(list("1"), tree.get("ab"));
    Assertions.assertEquals(Collections.emptyList(), tree.get("b"));

    Assertions.assertTrue(tree.remove("aβc", "1"));
    assertSize(tree, 0);
    Assertions.assertEquals(Collections.emptyList(), tree.get("abcdef"));
  }
}
