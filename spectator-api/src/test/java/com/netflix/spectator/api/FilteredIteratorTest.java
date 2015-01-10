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
import java.util.List;

@RunWith(JUnit4.class)
public class FilteredIteratorTest {

  private static final Predicate<String> ALL = new Predicate<String>() {
    @Override public boolean apply(String value) {
      return true;
    }
  };

  private static final Predicate<String> NONE = new Predicate<String>() {
    @Override public boolean apply(String value) {
      return false;
    }
  };

  private static final Predicate<String> EVEN = new Predicate<String>() {
    @Override public boolean apply(String value) {
      return Integer.parseInt(value) % 2 == 0;
    }
  };

  private static final Predicate<String> ODD = new Predicate<String>() {
    @Override public boolean apply(String value) {
      return !EVEN.apply(value);
    }
  };

  private List<String> newList(String... vs) {
    List<String> data = new ArrayList<>();
    for (String v : vs) {
      data.add(v);
    }
    return data;
  }

  @Test
  public void matchesAll() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), ALL));
    Assert.assertEquals(vs, out);
  }

  @Test
  public void matchesNone() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), NONE));
    Assert.assertEquals(0, out.size());
  }

  @Test
  public void matchesEven() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), EVEN));
    Assert.assertEquals(newList("2"), out);
  }

  @Test
  public void matchesOdd() {
    List<String> vs = newList("1", "2", "3");
    List<String> out = Utils.toList(new FilteredIterator<>(vs.iterator(), ODD));
    Assert.assertEquals(newList("1", "3"), out);
  }
}
