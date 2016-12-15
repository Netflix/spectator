/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.Preconditions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Query for matching based on tags. For more information see:
 *
 * https://github.com/Netflix/atlas/wiki/Stack-Language#query
 */
interface Query {

  /** Convert {@code id} to a map. */
  static Map<String, String> toMap(Id id) {
    Map<String, String> tags = new HashMap<>();
    for (Tag t : id.tags()) {
      tags.put(t.key(), t.value());
    }
    tags.put("name", id.name());
    return tags;
  }

  /**
   * Check to see if this query matches a set of tags. Common tags or changes to fix
   * invalid characters should be performed prior to checking for a match.
   *
   * @param tags
   *     Tags to use when checking for a match.
   * @return
   *     True if the query expression matches the tag map.
   */
  boolean matches(Map<String, String> tags);

  /**
   * Check to see if this query matches an id. Equivalent to calling {@link #matches(Map)}
   * with the result of {@link #toMap(Id)}.
   *
   * @param id
   *     Id to use when checking for a match.
   * @return
   *     True if the query expression matches the id.
   */
  default boolean matches(Id id) {
    return matches(toMap(id));
  }

  /**
   * Extract the tags from the query that have an exact match for a given value. That
   * is are specified using an {@link Equal} clause.
   *
   * @return
   *     Tags that are exactly matched as part of the query.
   */
  default Map<String, String> exactTags() {
    return Collections.emptyMap();
  }

  /** Returns a new query: {@code this AND q}. */
  default Query and(Query q) {
    return new And(this, q);
  }

  /** Returns a new query: {@code this OR q}. */
  default Query or(Query q) {
    return new Or(this, q);
  }

  /** Returns an inverted version of this query. */
  default Query not() {
    return new Not(this);
  }

  /** Query that always matches. */
  Query TRUE = new Query() {
    @Override public boolean matches(Map<String, String> tags) {
      return true;
    }

    @Override public String toString() {
      return ":true";
    }
  };

  /** Query that never matches. */
  Query FALSE = new Query() {
    @Override public boolean matches(Map<String, String> tags) {
      return false;
    }

    @Override public String toString() {
      return ":false";
    }
  };

  /** Query that matches if both sub-queries match. */
  final class And implements Query {
    private final Query q1;
    private final Query q2;

    /** Create a new instance. */
    And(Query q1, Query q2) {
      this.q1 = Preconditions.checkNotNull(q1, "q1");
      this.q2 = Preconditions.checkNotNull(q2, "q2");
    }

    @Override public boolean matches(Map<String, String> tags) {
      return q1.matches(tags) && q2.matches(tags);
    }

    @Override public Map<String, String> exactTags() {
      Map<String, String> tags = new HashMap<>();
      tags.putAll(q1.exactTags());
      tags.putAll(q2.exactTags());
      return tags;
    }

    @Override public String toString() {
      return q1 + "," + q2 + ",:and";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof And)) return false;
      And other = (And) obj;
      return q1.equals(other.q1) && q2.equals(other.q2);
    }

    @Override public int hashCode() {
      int result = q1.hashCode();
      result = 31 * result + q2.hashCode();
      return result;
    }
  }

  /** Query that matches if either sub-queries match. */
  final class Or implements Query {
    private final Query q1;
    private final Query q2;

    /** Create a new instance. */
    Or(Query q1, Query q2) {
      this.q1 = Preconditions.checkNotNull(q1, "q1");
      this.q2 = Preconditions.checkNotNull(q2, "q2");
    }

    @Override public boolean matches(Map<String, String> tags) {
      return q1.matches(tags) || q2.matches(tags);
    }

    @Override public String toString() {
      return q1 + "," + q2 + ",:or";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof Or)) return false;
      Or other = (Or) obj;
      return q1.equals(other.q1) && q2.equals(other.q2);
    }

    @Override public int hashCode() {
      int result = q1.hashCode();
      result = 31 * result + q2.hashCode();
      return result;
    }
  }

  /** Query that matches if the sub-query does not match. */
  final class Not implements Query {
    private final Query q;

    /** Create a new instance. */
    Not(Query q) {
      this.q = Preconditions.checkNotNull(q, "q");
    }

    @Override public boolean matches(Map<String, String> tags) {
      return !q.matches(tags);
    }

    @Override public String toString() {
      return q + ",:not";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof Not)) return false;
      Not other = (Not) obj;
      return q.equals(other.q);
    }

    @Override public int hashCode() {
      return q.hashCode();
    }
  }

  /** Query that matches if the tag map contains a specified key. */
  final class Has implements Query {
    private final String k;

    /** Create a new instance. */
    Has(String k) {
      this.k = Preconditions.checkNotNull(k, "k");
    }

    @Override public boolean matches(Map<String, String> tags) {
      return tags.containsKey(k);
    }

    @Override public String toString() {
      return k + ",:has";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof Has)) return false;
      Has other = (Has) obj;
      return k.equals(other.k);
    }

    @Override public int hashCode() {
      return k.hashCode();
    }
  }

  /** Query that matches if the tag map contains key {@code k} with value {@code v}. */
  final class Equal implements Query {
    private final String k;
    private final String v;

    /** Create a new instance. */
    Equal(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public boolean matches(Map<String, String> tags) {
      return v.equals(tags.get(k));
    }

    @Override public Map<String, String> exactTags() {
      Map<String, String> tags = new HashMap<>();
      tags.put(k, v);
      return tags;
    }

    @Override public String toString() {
      return k + "," + v + ",:eq";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof Equal)) return false;
      Equal other = (Equal) obj;
      return k.equals(other.k) && v.equals(other.v);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value in the set
   * {@code vs}.
   */
  final class In implements Query {
    private final String k;
    private final Set<String> vs;

    /** Create a new instance. */
    In(String k, Set<String> vs) {
      Preconditions.checkArg(!vs.isEmpty(), "list of values for :in cannot be empty");
      this.k = Preconditions.checkNotNull(k, "k");
      this.vs = Preconditions.checkNotNull(vs, "vs");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && vs.contains(tags.get(k));
    }

    @Override public String toString() {
      String values = vs.stream().collect(Collectors.joining(","));
      return k + ",(," + values + ",),:in";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof In)) return false;
      In other = (In) obj;
      return k.equals(other.k) && vs.equals(other.vs);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + vs.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value that is lexically
   * less than {@code v}.
   */
  final class LessThan implements Query {
    private final String k;
    private final String v;

    /** Create a new instance. */
    LessThan(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && s.compareTo(v) < 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:lt";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof LessThan)) return false;
      LessThan other = (LessThan) obj;
      return k.equals(other.k) && v.equals(other.v);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value that is lexically
   * less than or equal to {@code v}.
   */
  final class LessThanEqual implements Query {
    private final String k;
    private final String v;

    /** Create a new instance. */
    LessThanEqual(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && s.compareTo(v) <= 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:le";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof LessThanEqual)) return false;
      LessThanEqual other = (LessThanEqual) obj;
      return k.equals(other.k) && v.equals(other.v);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value that is lexically
   * greater than {@code v}.
   */
  final class GreaterThan implements Query {
    private final String k;
    private final String v;

    /** Create a new instance. */
    GreaterThan(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && s.compareTo(v) > 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:gt";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof GreaterThan)) return false;
      GreaterThan other = (GreaterThan) obj;
      return k.equals(other.k) && v.equals(other.v);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value that is lexically
   * greater than or equal to {@code v}.
   */
  final class GreaterThanEqual implements Query {
    private final String k;
    private final String v;

    /** Create a new instance. */
    GreaterThanEqual(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && s.compareTo(v) >= 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:ge";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof GreaterThanEqual)) return false;
      GreaterThanEqual other = (GreaterThanEqual) obj;
      return k.equals(other.k) && v.equals(other.v);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      return result;
    }
  }

  /**
   * Query that matches if the tag map contains key {@code k} with a value that matches the
   * regex in {@code v}. The expression will be automatically anchored to the start to encourage
   * prefix matches.
   *
   * <p><b>Warning:</b> regular expressions are often expensive and can add a lot of overhead.
   * Use them sparingly.</p>
   */
  final class Regex implements Query {
    private final String k;
    private final String v;
    private final Pattern pattern;
    private final String name;

    /** Create a new instance. */
    Regex(String k, String v) {
      this(k, v, 0, ":re");
    }

    /** Create a new instance. */
    Regex(String k, String v, int flags, String name) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
      this.pattern = Pattern.compile("^" + v, flags);
      this.name = Preconditions.checkNotNull(name, "name");
    }

    @Override public boolean matches(Map<String, String> tags) {
      String s = tags.get(k);
      return s != null && pattern.matcher(s).find();
    }

    @Override public String toString() {
      return k + "," + v + "," + name;
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || !(obj instanceof Regex)) return false;
      Regex other = (Regex) obj;
      return k.equals(other.k)
          && v.equals(other.v)
          && pattern.flags() == other.pattern.flags()
          && name.equals(other.name);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      result = 31 * result + pattern.flags();
      result = 31 * result + name.hashCode();
      return result;
    }
  }
}
