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

package com.netflix.spectator.controllers.model

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Meter
import com.netflix.spectator.api.Measurement
import com.netflix.spectator.api.Tag
import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilter
import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilterSpecification
import spock.lang.Specification
import java.util.regex.Pattern


class TestMeter implements Meter {
  private Id id
  public TestMeter(Id id) { this.id = id }
  public Id id() { return this.id }
  public Iterable<Measurement> measure() { return null }
  public boolean hasExpired() { return false }
}

class TestId implements Id {
  private String name
  private List<Tag> tags

  public TestId(name, List<Tag> tags) {
    this.name = name
    this.tags = tags
  }
  public String name() { return this.name }
  Iterable<Tag> tags() { return this.tags }
  Id withTag(String k, String v) {
    ArrayList newList = new ArrayList()
    newList.addAll(this.tags)
    newList.add(new Tag() {
      public String key() { return k }
      public String value() { return v }
    })
    return new TestId(this.name, newList)
  }
  Id withTag(Tag t) {
    ArrayList newList = new ArrayList()
    newList.addAll(this.tags)
    newList.add(t)
    return new TestId(this.name, newList)
  }
  Id withTags(Iterable<Tag> tags) {
    return Id.withTags(tags);
  }
  Id withTags(Map<String, String> tags) {
    return Id.withTags(tags);
  }
}

// This is just an alias for readability in the tests.

// This is just an alias for readability in the tests.

// This is just an alias for readability in the tests.


