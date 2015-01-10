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
package com.netflix.spectator.gc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Fixed size buffer that overwrites previous entries after filling up all slots.
 */
class CircularBuffer<T> {

  private final AtomicInteger nextIndex;
  private final AtomicReferenceArray<T> data;

  /** Create a new instance. */
  CircularBuffer(int length) {
    nextIndex = new AtomicInteger(0);
    data = new AtomicReferenceArray<>(length);
  }

  /** Add a new item to the buffer. If the buffer is full a previous entry will get overwritten. */
  void add(T item) {
    int i = nextIndex.getAndIncrement() % data.length();
    data.set(i, item);
  }

  /** Get the item in the buffer at position {@code i} or return null if it isn't set. */
  T get(int i) {
    return data.get(i);
  }

  /** Return the capacity of the buffer. */
  int size() {
    return data.length();
  }

  /** Return a list with a copy of the data in the buffer. */
  List<T> toList() {
    List<T> items = new ArrayList<>(data.length());
    for (int i = 0; i < data.length(); ++i) {
      T item = data.get(i);
      if (item != null) {
        items.add(item);
      }
    }
    return items;
  }
}
