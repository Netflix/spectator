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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Wraps an iterator with one that will filter the values using the specified predicate.
 */
class FilteredIterator<T> implements Iterator<T> {

  private final Iterator<T> it;
  private final Predicate<T> p;
  private T item;

  /**
   * Create a new instance.
   *
   * @param it
   *     Inner iterator that should be filtered.
   * @param p
   *     Predicate for finding matching results.
   */
  FilteredIterator(Iterator<T> it, Predicate<T> p) {
    this.it = it;
    this.p = p;
    findNext();
  }

  private void findNext() {
    while (it.hasNext()) {
      item = it.next();
      if (p.test(item)) {
        return;
      }
    }
    item = null;
  }

  @Override public boolean hasNext() {
    return item != null;
  }

  @SuppressWarnings("PMD.UnnecessaryLocalBeforeReturn")
  @Override public T next() throws NoSuchElementException {
    if (item == null) {
      throw new NoSuchElementException("next() called after reaching end of iterator");
    }
    // Note: PMD flags this, but the local tmp is necessary because findNext will change
    // the value of item
    T tmp = item;
    findNext();
    return tmp;
  }

  @Override public void remove() {
    throw new UnsupportedOperationException("remove");
  }
}
