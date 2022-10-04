/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.netflix.spectator.impl.Preconditions;

/**
 * Immutable implementation of Tag.
 */
public final class BasicTag implements Tag {

  /**
   * Convert an arbitrary implementation of {@link Tag} to BasicTag. When
   * used as part of an id this is needed to ensure correct behavior. Alternative
   * implementations will likely vary the hashCode/equals causing ids that should
   * be equivalent to not match as expected.
   */
  static BasicTag convert(Tag t) {
    return (t instanceof BasicTag) ? (BasicTag) t : new BasicTag(t.key(), t.value());
  }

  private final String key;
  private final String value;
  private int hc;

  /**
   * Construct a new instance.
   */
  public BasicTag(String key, String value) {
    this.key = Preconditions.checkNotNull(key, "key");
    this.value = value;
    if (value == null) {
      String msg = String.format("parameter 'value' cannot be null (key=%s)", key);
      throw new NullPointerException(msg);
    }
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public String value() {
    return value;
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof BasicTag)) return false;
    BasicTag other = (BasicTag) obj;
    return key.equals(other.key) && value.equals(other.value);
  }

  @Override
  public int hashCode() {
    int h = hc;
    if (h == 0) {
      h = 31 * key.hashCode() + value.hashCode();
      hc = h;
    }
    return h;
  }

  @Override
  public String toString() {
    return key + '=' + value;
  }
}
