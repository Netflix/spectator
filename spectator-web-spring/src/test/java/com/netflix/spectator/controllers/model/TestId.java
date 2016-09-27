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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public class TestId implements Id {
  private String name;
  private List<Tag> tags;

  public TestId(String name) {
    this.name = name;
    this.tags = new ArrayList<Tag>();
  }
  public TestId(String name, List<Tag> tags) {
   this.name = name;
   this.tags = tags;
  }

  public String name() {
    return this.name;
  }

  public Iterable<Tag> tags() {
    return this.tags;
  }

  public Id withTag(String k, String v) {
    ArrayList<Tag> newList = new ArrayList<Tag>();
    newList.addAll(this.tags);
    newList.add(new BasicTag(k, v));
    return new TestId(this.name, newList);
  }

  public Id withTag(Tag t) {
    ArrayList<Tag> newList = new ArrayList<Tag>();
    newList.addAll(this.tags);
    newList.add(t);
    return new TestId(this.name, newList);
  }

  public Id withTags(Iterable<Tag> tags) {
    ArrayList<Tag> newList = new ArrayList<Tag>();
    newList.addAll(this.tags);
    for (Tag tag : tags) {
      newList.add(tag);
    }
    return new TestId(this.name, newList);
  }

  public Id withTags(Map<String, String> tags) {
    ArrayList<Tag> newList = new ArrayList<Tag>();
    newList.addAll(this.tags);
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      newList.add(new BasicTag(entry.getKey(), entry.getValue()));
    }
    return new TestId(this.name, newList);
  }

  public int hashCode() {
    return Objects.hash(name, tags);
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TestId)) {
        return false;
    }
    TestId other = (TestId) obj;
    return name.equals(other.name) && tags.equals(other.tags);
  }
};
