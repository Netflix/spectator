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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.PatternMatcher;
import com.netflix.spectator.impl.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Query for matching based on tags. For more information see:
 *
 * https://github.com/Netflix/atlas/wiki/Stack-Language#query
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public interface Query {

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
    return (q == TRUE || q == FALSE) ? q.and(this) : new And(this, q);
  }

  /** Returns a new query: {@code this OR q}. */
  default Query or(Query q) {
    return (q == TRUE || q == FALSE) ? q.or(this) : new Or(this, q);
  }

  /** Returns an inverted version of this query. */
  default Query not() {
    return (this instanceof KeyQuery)
        ? new InvertedKeyQuery((KeyQuery) this)
        : new Not(this);
  }

  /**
   * Converts this query into disjunctive normal form. The return value is a list of
   * sub-queries that should be ORd together.
   */
  default List<Query> dnfList() {
    return Collections.singletonList(this);
  }

  /**
   * Converts this query into a list of sub-queries that can be ANDd together. The query will
   * not be normalized first, it will only expand top-level AND clauses.
   */
  default List<Query> andList() {
    return Collections.singletonList(this);
  }

  /**
   * Return a new query that has been simplified by pre-evaluating the conditions for a set
   * of tags that are common to all metrics.
   */
  default Query simplify(Map<String, String> tags) {
    return this;
  }

  /** Query that always matches. */
  Query TRUE = new Query() {

    @Override public Query and(Query q) {
      return q;
    }

    @Override public Query or(Query q) {
      return TRUE;
    }

    @Override public Query not() {
      return FALSE;
    }

    @Override public boolean matches(Map<String, String> tags) {
      return true;
    }

    @Override public String toString() {
      return ":true";
    }
  };

  /** Query that never matches. */
  Query FALSE = new Query() {

    @Override public Query and(Query q) {
      return FALSE;
    }

    @Override public Query or(Query q) {
      return q;
    }

    @Override public Query not() {
      return TRUE;
    }

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

    @Override public Query not() {
      Query nq1 = q1.not();
      Query nq2 = q2.not();
      return nq1.or(nq2);
    }

    @Override public List<Query> dnfList() {
      return crossAnd(q1.dnfList(), q2.dnfList());
    }

    @Override public List<Query> andList() {
      List<Query> tmp = new ArrayList<>(q1.andList());
      tmp.addAll(q2.andList());
      return tmp;
    }

    private List<Query> crossAnd(List<Query> qs1, List<Query> qs2) {
      List<Query> tmp = new ArrayList<>();
      for (Query q1 : qs1) {
        for (Query q2 : qs2) {
          tmp.add(q1.and(q2));
        }
      }
      return tmp;
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

    @Override public Query simplify(Map<String, String> tags) {
      Query sq1 = q1.simplify(tags);
      Query sq2 = q2.simplify(tags);
      return (sq1 != q1 || sq2 != q2) ? sq1.and(sq2) : this;
    }

    @Override public String toString() {
      return q1 + "," + q2 + ",:and";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof And)) return false;
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

    @Override public Query not() {
      Query nq1 = q1.not();
      Query nq2 = q2.not();
      return nq1.and(nq2);
    }

    @Override public List<Query> dnfList() {
      List<Query> qs = new ArrayList<>(q1.dnfList());
      qs.addAll(q2.dnfList());
      return qs;
    }

    @Override public boolean matches(Map<String, String> tags) {
      return q1.matches(tags) || q2.matches(tags);
    }

    @Override public Query simplify(Map<String, String> tags) {
      Query sq1 = q1.simplify(tags);
      Query sq2 = q2.simplify(tags);
      return (sq1 != q1 || sq2 != q2) ? sq1.or(sq2) : this;
    }

    @Override public String toString() {
      return q1 + "," + q2 + ",:or";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Or)) return false;
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

    @Override public Query not() {
      return q;
    }

    @Override public List<Query> dnfList() {
      if (q instanceof And) {
        And query = (And) q;
        List<Query> qs = new ArrayList<>(query.q1.not().dnfList());
        qs.addAll(query.q2.not().dnfList());
        return qs;
      } else if (q instanceof Or) {
        Or query = (Or) q;
        Query q1 = query.q1.not();
        Query q2 = query.q2.not();
        return q1.and(q2).dnfList();
      } else {
        return Collections.singletonList(this);
      }
    }

    @Override public boolean matches(Map<String, String> tags) {
      return !q.matches(tags);
    }

    @Override public Query simplify(Map<String, String> tags) {
      Query sq = q.simplify(tags);
      return (sq != q) ? sq.not() : this;
    }

    @Override public String toString() {
      return q + ",:not";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Not)) return false;
      Not other = (Not) obj;
      return q.equals(other.q);
    }

    @Override public int hashCode() {
      return q.hashCode();
    }
  }

  /** Base interface for simple queries that check the value associated with a single key. */
  interface KeyQuery extends Query {
    /** Key checked by this query. */
    String key();

    /** Returns true if the value matches for this query clause. */
    boolean matches(String value);

    @Override default boolean matches(Map<String, String> tags) {
      return matches(tags.get(key()));
    }

    @Override default Query simplify(Map<String, String> tags) {
      String v = tags.get(key());
      if (v == null) {
        return this;
      }
      return matches(v) ? Query.TRUE : Query.FALSE;
    }
  }

  /** Checks all of a set of conditions for the same key match the specified value. */
  final class CompositeKeyQuery implements KeyQuery {
    private final String k;
    private final List<KeyQuery> queries;

    /** Create a new instance. */
    CompositeKeyQuery(KeyQuery query) {
      Preconditions.checkNotNull(query, "query");
      this.k = query.key();
      this.queries = new ArrayList<>();
      this.queries.add(query);
    }

    /** Add another query to the list. */
    void add(KeyQuery query) {
      Preconditions.checkArg(k.equals(query.key()), "key mismatch: " + k + " != " + query.key());
      queries.add(query);
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      for (KeyQuery kq : queries) {
        if (!kq.matches(value)) {
          return false;
        }
      }
      return true;
    }

    @Override public String toString() {
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (KeyQuery kq : queries) {
        if (first) {
          first = false;
          builder.append(kq);
        } else {
          builder.append(',').append(kq).append(",:and");
        }
      }
      return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CompositeKeyQuery that = (CompositeKeyQuery) o;
      return Objects.equals(queries, that.queries) && Objects.equals(k, that.k);
    }

    @Override
    public int hashCode() {
      return Objects.hash(queries, k);
    }
  }

  /** Query that matches if the underlying key query does not match. */
  final class InvertedKeyQuery implements KeyQuery {
    private final KeyQuery q;

    /** Create a new instance. */
    InvertedKeyQuery(KeyQuery q) {
      this.q = Preconditions.checkNotNull(q, "q");
    }

    @Override public Query not() {
      return q;
    }

    @Override public String key() {
      return q.key();
    }

    @Override public boolean matches(String value) {
      return !q.matches(value);
    }

    @Override public Query simplify(Map<String, String> tags) {
      Query sq = q.simplify(tags);
      return (sq != q) ? sq.not() : this;
    }

    @Override public String toString() {
      return q + ",:not";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof InvertedKeyQuery)) return false;
      InvertedKeyQuery other = (InvertedKeyQuery) obj;
      return q.equals(other.q);
    }

    @Override public int hashCode() {
      return q.hashCode();
    }
  }

  /** Query that matches if the tag map contains a specified key. */
  final class Has implements KeyQuery {
    private final String k;

    /** Create a new instance. */
    Has(String k) {
      this.k = Preconditions.checkNotNull(k, "k");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null;
    }

    @Override public boolean matches(Map<String, String> tags) {
      return tags.containsKey(k);
    }

    @Override public String toString() {
      return k + ",:has";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Has)) return false;
      Has other = (Has) obj;
      return k.equals(other.k);
    }

    @Override public int hashCode() {
      return k.hashCode();
    }
  }

  /** Query that matches if the tag map contains key {@code k} with value {@code v}. */
  final class Equal implements KeyQuery {
    private final String k;
    private final String v;

    /** Create a new instance. */
    Equal(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public String key() {
      return k;
    }

    public String value() {
      return v;
    }

    @Override public boolean matches(String value) {
      return v.equals(value);
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
      if (!(obj instanceof Equal)) return false;
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
  final class In implements KeyQuery {
    private final String k;
    private final Set<String> vs;

    /** Create a new instance. */
    In(String k, Set<String> vs) {
      Preconditions.checkArg(!vs.isEmpty(), "list of values for :in cannot be empty");
      this.k = Preconditions.checkNotNull(k, "k");
      this.vs = Preconditions.checkNotNull(vs, "vs");
    }

    @Override public String key() {
      return k;
    }

    public Set<String> values() {
      return vs;
    }

    @Override public boolean matches(String value) {
      return value != null && vs.contains(value);
    }

    @Override public String toString() {
      String values = String.join(",", vs);
      return k + ",(," + values + ",),:in";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof In)) return false;
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
  final class LessThan implements KeyQuery {
    private final String k;
    private final String v;

    /** Create a new instance. */
    LessThan(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null && value.compareTo(v) < 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:lt";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof LessThan)) return false;
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
  final class LessThanEqual implements KeyQuery {
    private final String k;
    private final String v;

    /** Create a new instance. */
    LessThanEqual(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null && value.compareTo(v) <= 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:le";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof LessThanEqual)) return false;
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
  final class GreaterThan implements KeyQuery {
    private final String k;
    private final String v;

    /** Create a new instance. */
    GreaterThan(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null && value.compareTo(v) > 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:gt";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof GreaterThan)) return false;
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
  final class GreaterThanEqual implements KeyQuery {
    private final String k;
    private final String v;

    /** Create a new instance. */
    GreaterThanEqual(String k, String v) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null && value.compareTo(v) >= 0;
    }

    @Override public String toString() {
      return k + "," + v + ",:ge";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof GreaterThanEqual)) return false;
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
  final class Regex implements KeyQuery {
    private final String k;
    private final String v;
    private final PatternMatcher pattern;
    private final String name;

    /** Create a new instance. */
    Regex(String k, String v) {
      this(k, v, false, ":re");
    }

    /** Create a new instance. */
    Regex(String k, String v, boolean ignoreCase, String name) {
      this.k = Preconditions.checkNotNull(k, "k");
      this.v = Preconditions.checkNotNull(v, "v");
      if (ignoreCase) {
        this.pattern = PatternMatcher.compile("^" + v).ignoreCase();
      } else {
        this.pattern = PatternMatcher.compile("^" + v);
      }
      this.name = Preconditions.checkNotNull(name, "name");
    }

    @Override public String key() {
      return k;
    }

    @Override public boolean matches(String value) {
      return value != null && pattern.matches(value);
    }

    /** Returns true if the pattern will always match. */
    public boolean alwaysMatches() {
      return pattern.alwaysMatches();
    }

    @Override public String toString() {
      return k + "," + v + "," + name;
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Regex)) return false;
      Regex other = (Regex) obj;
      return k.equals(other.k)
          && v.equals(other.v)
          && pattern.equals(other.pattern)
          && name.equals(other.name);
    }

    @Override public int hashCode() {
      int result = k.hashCode();
      result = 31 * result + v.hashCode();
      result = 31 * result + pattern.hashCode();
      result = 31 * result + name.hashCode();
      return result;
    }
  }
}
