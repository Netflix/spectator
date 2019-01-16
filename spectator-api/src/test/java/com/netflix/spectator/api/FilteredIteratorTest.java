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
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class FilteredIteratorTest {

  private static final Predicate<String> ALL = v -> true;

  private static final Predicate<String> NONE = v -> false;

  private static final Predicate<String> EVEN = v -> Integer.parseInt(v) % 2 == 0;

  private static final Predicate<String> ODD = v -> !EVEN.test(v);

  private List<String> newList(String... vs) {
    List<String> data = new ArrayList<>();
    Collections.addAll(data, vs);
    return data;
  }

  @Test
  public void matchesAll() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), ALL));
    Assertions.assertEquals(vs, out);
  }

  @Test
  public void matchesNone() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), NONE));
    Assertions.assertEquals(0, out.size());
  }

  @Test
  public void matchesEven() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), EVEN));
    Assertions.assertEquals(newList("2"), out);
  }

  @Test
  public void matchesOdd() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), ODD));
    Assertions.assertEquals(newList("1", "3"), out);
  }
}
