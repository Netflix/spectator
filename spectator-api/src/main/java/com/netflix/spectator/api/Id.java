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

import java.util.Map;

/**
 * Identifier for a meter or measurement.
 */
public interface Id {
  /** Description of the measurement that is being collected. */
  String name();

  /** Other dimensions that can be used to classify the measurement. */
  Iterable<Tag> tags();

  /** Return a new id with an additional tag value. */
  Id withTag(String k, String v);

  /** Return a new id with an additional tag value. */
  Id withTag(Tag t);

  /**
   * Return a new id with an additional tag value using {@link Boolean#toString(boolean)} to
   * convert the boolean value to a string representation. This is merely a convenience function
   * for:
   *
   * <pre>
   *   id.withTag("key", Boolean.toString(value))
   * </pre>
   */
  default Id withTag(String k, boolean v) {
    return withTag(k, Boolean.toString(v));
  }

  /**
   * Return a new id with an additional tag value using {@link Enum#name()} to
   * convert the Enum to a string representation. This is merely a convenience function
   * for:
   *
   * <pre>
   *   id.withTag("key", myEnum.name())
   * </pre>
   */
  default <E extends Enum<E>> Id withTag(String k, Enum<E> v) {
    return withTag(k, v.name());
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  default Id withTags(String k1, String v1) {
    return withTag(k1, v1);
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  default Id withTags(String k1, String v1, String k2, String v2) {
    final Tag[] ts = new Tag[] {
        new BasicTag(k1, v1),
        new BasicTag(k2, v2)
    };
    return withTags(ts);
  }

  /**
   * Return a new id with additional tag values. This overload is to avoid allocating a
   * parameters array for the more generic varargs method {@link #withTags(String...)}.
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  default Id withTags(String k1, String v1, String k2, String v2, String k3, String v3) {
    final Tag[] ts = new Tag[] {
        new BasicTag(k1, v1),
        new BasicTag(k2, v2),
        new BasicTag(k3, v3)
    };
    return withTags(ts);
  }

  /** Return a new id with additional tag values. */
  default Id withTags(String... tags) {
    Id tmp = this;
    for (int i = 0; i < tags.length; i += 2) {
      tmp = tmp.withTag(tags[i], tags[i + 1]);
    }
    return tmp;
  }

  /** Return a new id with additional tag values. */
  default Id withTags(Tag... tags) {
    Id tmp = this;
    for (Tag t : tags) {
      tmp = tmp.withTag(t);
    }
    return tmp;
  }

  /** Return a new id with additional tag values. */
  default Id withTags(Iterable<Tag> tags) {
    Id tmp = this;
    for (Tag t : tags) {
      tmp = tmp.withTag(t);
    }
    return tmp;
  }

  /** Return a new id with additional tag values. */
  default Id withTags(Map<String, String> tags) {
    Id tmp = this;
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      tmp = tmp.withTag(entry.getKey(), entry.getValue());
    }
    return tmp;
  }
}
