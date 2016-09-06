/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spectator.controllers.model;


/**
 * A TagValue is an NameValue pair for a tag and its binding.
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class TagValue {
  public String getKey() { return key; }
  public String getValue() { return value; }

  public TagValue(String key, String value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TagValue)) return false;
    TagValue other = (TagValue)obj;
    return key.equals(other.key) && value.equals(other.value);
  }

  @Override
  public String toString() {
    return String.format("%s=%s", key, value);
  }

  private String key;
  private String value;
}
