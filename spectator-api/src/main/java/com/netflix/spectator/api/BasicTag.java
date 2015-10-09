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

import com.netflix.spectator.impl.Preconditions;

/**
 * Basic container for Tag data.
 *
 * Created on 10/1/15.
 */
public final class BasicTag implements Tag {
  private final String key;
  private final String value;
  private final int hc;

  /**
   * Construct a new instance.
   *
   * @param key
   * @param value
   */
  public BasicTag(String key, String value) {
    this.key = Preconditions.checkNotNull(key, "key");
    this.value = Preconditions.checkNotNull(value, "value");
    this.hc = 31 * key.hashCode() + value.hashCode();
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

  /**
   * This object is immutable and the hash code is precomputed in the constructor. The id object
   * is typically created to lookup a Meter based on dynamic dimensions so we assume that it is
   * highly likely that the hash code method will be called and that it could be in a fairly
   * high volume execution path.
   *
   * {@inheritDoc}
   */
  @Override public int hashCode() {
    return hc;
  }

  @Override
  public String toString() {
    return key + '=' + value;
  }
}
