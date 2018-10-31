/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;

import java.util.Iterator;
import java.util.Map;

/** Id implementation for the servo registry. */
final class ServoId implements Id {

  private final MonitorConfig config;

  /** Create a new instance. */
  ServoId(MonitorConfig config) {
    this.config = config;
  }

  /** Return the monitor config this id is based on. */
  MonitorConfig config() {
    return config;
  }

  @Override public String name() {
    return config.getName();
  }

  @Override public Iterable<Tag> tags() {
    return () -> new Iterator<Tag>() {
      private final Iterator<com.netflix.servo.tag.Tag> iter = config.getTags().iterator();

      public boolean hasNext() {
        return iter.hasNext();
      }

      public Tag next() {
        return new ServoTag(iter.next());
      }

      public void remove() {
        iter.remove();
      }
    };
  }

  @Override public Id withTag(String k, String v) {
    return new ServoId((new MonitorConfig.Builder(config)).withTag(k, v).build());
  }

  @Override public Id withTag(Tag t) {
    return withTag(t.key(), t.value());
  }

  @Override public Id withTags(Iterable<Tag> ts) {
    MonitorConfig.Builder builder = new MonitorConfig.Builder(config);
    for (Tag t : ts) {
      builder.withTag(t.key(), t.value());
    }
    return new ServoId(builder.build());
  }

  @Override public Id withTags(Map<String, String> ts) {
    MonitorConfig.Builder builder = new MonitorConfig.Builder(config);
    for (Map.Entry<String, String> t : ts.entrySet()) {
      builder.withTag(t.getKey(), t.getValue());
    }
    return new ServoId(builder.build());
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof ServoId)) return false;
    ServoId other = (ServoId) obj;
    return config.equals(other.config);
  }

  @Override public int hashCode() {
    return config.hashCode();
  }

  @Override public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(config.getName());
    for (com.netflix.servo.tag.Tag t : config.getTags()) {
      buf.append(':').append(t.getKey()).append('=').append(t.getValue());
    }
    return buf.toString();
  }
}
