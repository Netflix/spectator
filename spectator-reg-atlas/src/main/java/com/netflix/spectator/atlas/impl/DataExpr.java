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

import com.netflix.spectator.impl.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Data expressions for defining how to aggregate values. For more information see:
 *
 * https://github.com/Netflix/atlas/wiki/Reference-data
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public interface DataExpr {

  /** Query for selecting the input measurements that should be aggregated. */
  Query query();

  /**
   * Get an aggregator that can be incrementally fed values. See {@link #eval(Iterable)} if
   * you already have the completed list of values.
   *
   * @param tags
   *     The set of tags for the final aggregate.
   * @param shouldCheckQuery
   *     If true, then values will be checked against the query before applying to the
   *     aggregate. Otherwise, it is assumed that the user has already verified that the
   *     datapoint matches before passing it in.
   * @return
   *     Aggregator for this data expression.
   */
  Aggregator aggregator(Map<String, String> tags, boolean shouldCheckQuery);

  /**
   * Get an aggregator using the default set of tags for the final result. The tags will
   * be extracted based on the exact matches for the underlying query.
   */
  default Aggregator aggregator() {
    return aggregator(query().exactTags(), true);
  }

  /**
   * Evaluate the data expression over the input.
   *
   * @param input
   *     Set of data values. The data will get filtered based on the query, that does
   *     not need to be done in advance.
   * @return
   *     Aggregated data values.
   */
  default Iterable<TagsValuePair> eval(Iterable<TagsValuePair> input) {
    Aggregator aggr = aggregator();
    for (TagsValuePair p : input) {
      aggr.update(p);
    }
    return aggr.result();
  }

  /** Helper for incrementally computing an aggregate of a set of tag values. */
  interface Aggregator {
    /** Update the aggregate with the provided value. */
    void update(TagsValuePair p);

    /** Returns the aggregated data values. */
    Iterable<TagsValuePair> result();
  }

  /**
   * Includes all datapoints that match the query expression. See also:
   * https://github.com/Netflix/atlas/wiki/data-all
   */
  final class All implements DataExpr {

    private final Query query;

    /** Create a new instance. */
    All(Query query) {
      this.query = query;
    }

    @Override public Query query() {
      return query;
    }

    @Override public Aggregator aggregator(Map<String, String> ignored, boolean shouldCheckQuery) {
      return new Aggregator() {
        private final List<TagsValuePair> pairs = new ArrayList<>();

        @Override public void update(TagsValuePair p) {
          Map<String, String> tags = p.tags();
          if (!shouldCheckQuery || query.matches(tags)) {
            pairs.add(new TagsValuePair(tags, p.value()));
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return pairs;
        }
      };
    }

    @Override public Aggregator aggregator() {
      return aggregator(null, true);
    }

    @Override public String toString() {
      return query.toString() + ",:all";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof All)) return false;
      All other = (All) obj;
      return query.equals(other.query);
    }

    @Override public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + ":all".hashCode();
      return result;
    }
  }

  /** Base type for simple aggregate functions. */
  interface AggregateFunction extends DataExpr {
  }

  /**
   * Aggregates all datapoints that match the query to a single datapoint that is the
   * sum of the input values. See also: https://github.com/Netflix/atlas/wiki/data-sum
   */
  final class Sum implements AggregateFunction {

    private final Query query;

    /** Create a new instance. */
    Sum(Query query) {
      this.query = query;
    }

    @Override public Query query() {
      return query;
    }

    @Override public Aggregator aggregator(Map<String, String> tags, boolean shouldCheckQuery) {
      return new Aggregator() {
        private double aggr = 0.0;
        private int count = 0;

        @Override public void update(TagsValuePair p) {
          if (!shouldCheckQuery || query.matches(p.tags())) {
            aggr += p.value();
            ++count;
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return (count > 0)
              ? Collections.singletonList(new TagsValuePair(tags, aggr))
              : Collections.emptyList();
        }
      };
    }

    @Override public String toString() {
      return query.toString() + ",:sum";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Sum)) return false;
      Sum other = (Sum) obj;
      return query.equals(other.query);
    }

    @Override public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + ":sum".hashCode();
      return result;
    }
  }

  /**
   * Aggregates all datapoints that match the query to a single datapoint that is the
   * minimum of the input values. See also: https://github.com/Netflix/atlas/wiki/data-min
   */
  final class Min implements AggregateFunction {

    private final Query query;

    /** Create a new instance. */
    Min(Query query) {
      this.query = query;
    }

    @Override public Query query() {
      return query;
    }

    @Override public Aggregator aggregator(Map<String, String> tags, boolean shouldCheckQuery) {
      return new Aggregator() {
        private double aggr = Double.MAX_VALUE;
        private int count = 0;

        @Override public void update(TagsValuePair p) {
          if ((!shouldCheckQuery || query.matches(p.tags())) && p.value() < aggr) {
            aggr = p.value();
            ++count;
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return (count > 0)
              ? Collections.singletonList(new TagsValuePair(tags, aggr))
              : Collections.emptyList();
        }
      };
    }

    @Override public String toString() {
      return query.toString() + ",:min";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Min)) return false;
      Min other = (Min) obj;
      return query.equals(other.query);
    }

    @Override public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + ":min".hashCode();
      return result;
    }
  }

  /**
   * Aggregates all datapoints that match the query to a single datapoint that is the
   * maximum of the input values. See also: https://github.com/Netflix/atlas/wiki/data-max
   */
  final class Max implements AggregateFunction {

    private final Query query;

    /** Create a new instance. */
    Max(Query query) {
      this.query = query;
    }

    @Override public Query query() {
      return query;
    }

    @Override public Aggregator aggregator(Map<String, String> tags, boolean shouldCheckQuery) {
      return new Aggregator() {
        private double aggr = -Double.MAX_VALUE;
        private int count = 0;

        @Override public void update(TagsValuePair p) {
          if ((!shouldCheckQuery || query.matches(p.tags())) && p.value() > aggr) {
            aggr = p.value();
            ++count;
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return (count > 0)
              ? Collections.singletonList(new TagsValuePair(tags, aggr))
              : Collections.emptyList();
        }
      };
    }

    @Override public String toString() {
      return query.toString() + ",:max";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Max)) return false;
      Max other = (Max) obj;
      return query.equals(other.query);
    }

    @Override public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + ":max".hashCode();
      return result;
    }
  }

  /**
   * Aggregates all datapoints that match the query to a single datapoint that is the
   * number of input values. See also: https://github.com/Netflix/atlas/wiki/data-count
   */
  final class Count implements AggregateFunction {

    private final Query query;

    /** Create a new instance. */
    Count(Query query) {
      this.query = query;
    }

    @Override public Query query() {
      return query;
    }

    @Override public Aggregator aggregator(Map<String, String> tags, boolean shouldCheckQuery) {
      return new Aggregator() {
        private int aggr = 0;

        @Override public void update(TagsValuePair p) {
          if (!shouldCheckQuery || query.matches(p.tags())) {
            ++aggr;
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return (aggr > 0)
              ? Collections.singletonList(new TagsValuePair(tags, aggr))
              : Collections.emptyList();
        }
      };
    }

    @Override public String toString() {
      return query.toString() + ",:count";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Count)) return false;
      Count other = (Count) obj;
      return query.equals(other.query);
    }

    @Override public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + ":count".hashCode();
      return result;
    }
  }

  /**
   * Compute a set of time series matching the query and grouped by the specified keys.
   * See also: https://github.com/Netflix/atlas/wiki/data-by
   */
  final class GroupBy implements DataExpr {

    private final AggregateFunction af;
    private final List<String> keys;

    /** Create a new instance. */
    GroupBy(AggregateFunction af, List<String> keys) {
      Preconditions.checkArg(!keys.isEmpty(), "key list for group by cannot be empty");
      this.af = af;
      this.keys = keys;
    }

    private Map<String, String> keyTags(Map<String, String> tags) {
      Map<String, String> result = new HashMap<>();
      for (String k : keys) {
        String v = tags.get(k);
        if (v == null) {
          return null;
        }
        result.put(k, v);
      }
      return result;
    }

    @Override public Query query() {
      return af.query();
    }

    @Override public Aggregator aggregator(Map<String, String> queryTags, boolean shouldCheckQuery) {
      return new Aggregator() {
        private final Map<Map<String, String>, Aggregator> aggrs = new HashMap<>();

        @Override public void update(TagsValuePair p) {
          Map<String, String> tags = p.tags();
          if (!shouldCheckQuery || af.query().matches(tags)) {
            Map<String, String> k = keyTags(tags);
            if (k != null) {
              k.putAll(queryTags);
              aggrs.computeIfAbsent(k, ks -> af.aggregator(ks, false)).update(p);
            }
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return aggrs.values().stream()
              .flatMap(a -> StreamSupport.stream(a.result().spliterator(), false))
              .collect(Collectors.toList());
        }
      };
    }

    @Override public String toString() {
      final String keyList = String.join(",", keys);
      return af.toString() + ",(," + keyList + ",),:by";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof GroupBy)) return false;
      GroupBy other = (GroupBy) obj;
      return af.equals(other.af) && keys.equals(other.keys);
    }

    @Override public int hashCode() {
      int result = af.hashCode();
      result = 31 * result + keys.hashCode();
      result = 31 * result + ":by".hashCode();
      return result;
    }
  }

  /**
   * Rollup inputs by dropping the specified keys. This is typically used with
   * a rollup config to reduce the amount of data going out. If a whitelist
   * of keys is needed, then see {@link KeepRollup}.
   */
  final class DropRollup implements DataExpr {

    private final AggregateFunction af;
    private final List<String> keys;

    /** Create a new instance. */
    DropRollup(AggregateFunction af, List<String> keys) {
      Preconditions.checkArg(!keys.contains("name"), "name is required and cannot be dropped");
      this.af = af;
      this.keys = keys;
    }

    @Override public Query query() {
      return af.query();
    }

    @Override public Aggregator aggregator(Map<String, String> ignored, boolean shouldCheckQuery) {
      return new Aggregator() {
        private final Map<Map<String, String>, Aggregator> aggrs = new HashMap<>();

        @Override public void update(TagsValuePair p) {
          Map<String, String> tags = new HashMap<>(p.tags());
          if (!shouldCheckQuery || af.query().matches(tags)) {
            for (String k : keys) {
              tags.remove(k);
            }
            aggrs.computeIfAbsent(tags, ks -> af.aggregator(ks, false)).update(p);
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return aggrs.values().stream()
              .flatMap(a -> StreamSupport.stream(a.result().spliterator(), false))
              .collect(Collectors.toList());
        }
      };
    }

    @Override public Aggregator aggregator() {
      return aggregator(null, true);
    }

    @Override public String toString() {
      final String keyList = String.join(",", keys);
      return af.toString() + ",(," + keyList + ",),:rollup-drop";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof DropRollup)) return false;
      DropRollup other = (DropRollup) obj;
      return af.equals(other.af) && keys.equals(other.keys);
    }

    @Override public int hashCode() {
      int result = af.hashCode();
      result = 31 * result + keys.hashCode();
      result = 31 * result + ":by".hashCode();
      return result;
    }
  }

  /**
   * Rollup inputs by only keeping the specified keys. This is typically used with
   * a rollup config to reduce the amount of data going out. If a blacklist of
   * keys is needed, then see {@link DropRollup}.
   */
  final class KeepRollup implements DataExpr {

    private final AggregateFunction af;
    private final Set<String> keys;

    /** Create a new instance. */
    KeepRollup(AggregateFunction af, List<String> keys) {
      this.af = af;
      this.keys = new HashSet<>(keys);
      this.keys.add("name");
    }

    @Override public Query query() {
      return af.query();
    }

    @Override public Aggregator aggregator(Map<String, String> ignored, boolean shouldCheckQuery) {
      return new Aggregator() {
        private final Map<Map<String, String>, Aggregator> aggrs = new HashMap<>();

        @Override public void update(TagsValuePair p) {
          Map<String, String> tags = p.tags();
          if (!shouldCheckQuery || af.query().matches(tags)) {
            Map<String, String> newTags = tags.entrySet().stream()
                .filter(e -> keys.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            aggrs.computeIfAbsent(newTags, ks -> af.aggregator(ks, false)).update(p);
          }
        }

        @Override public Iterable<TagsValuePair> result() {
          return aggrs.values().stream()
              .flatMap(a -> StreamSupport.stream(a.result().spliterator(), false))
              .collect(Collectors.toList());
        }
      };
    }

    @Override public Aggregator aggregator() {
      return aggregator(null, true);
    }

    @Override public String toString() {
      final String keyList = String.join(",", keys);
      return af.toString() + ",(," + keyList + ",),:rollup-keep";
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof KeepRollup)) return false;
      KeepRollup other = (KeepRollup) obj;
      return af.equals(other.af) && keys.equals(other.keys);
    }

    @Override public int hashCode() {
      int result = af.hashCode();
      result = 31 * result + keys.hashCode();
      result = 31 * result + ":by".hashCode();
      return result;
    }
  }
}
