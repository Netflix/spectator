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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Helper functions for working with a sequence of measurements.
 */
public final class Utils {

  private static final Logger REGISTRY_LOGGER = LoggerFactory.getLogger(Registry.class);

  private Utils() {
  }

  /** Search for a method in the class and all superclasses. */
  static Method getMethod(Class<?> cls, String name) throws NoSuchMethodException {
    NoSuchMethodException firstExc = null;
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      try {
        return c.getDeclaredMethod(name);
      } catch (NoSuchMethodException e) {
        if (firstExc == null) {
          firstExc = e;
        }
      }
    }
    throw firstExc;
  }

  /** Return a method supplying a value for a gauge. */
  static Method getGaugeMethod(Registry registry, Id id, Object obj, String method) {
    try {
      final Method m = Utils.getMethod(obj.getClass(), method);
      try {
        // Make sure we can cast the response to a Number
        final Number n = (Number) m.invoke(obj);
        REGISTRY_LOGGER.debug(
            "registering gauge {}, using method [{}], with initial value {}", id, m, n);
        return m;
      } catch (Exception e) {
        final String msg = "exception thrown invoking method [" + m
            + "], skipping registration of gauge " + id;
        registry.propagate(msg, e);
      }
    } catch (NoSuchMethodException e) {
      final String mname = obj.getClass().getName() + "." + method;
      final String msg = "invalid method [" + mname + "], skipping registration of gauge " + id;
      registry.propagate(msg, e);
    }
    return null;
  }

  /**
   * Returns a new id with the tag list sorted by key and with no duplicate keys.
   */
  public static Id normalize(Id id) {
    return (new DefaultId(id.name())).withTags(id.tags()).normalize();
  }

  /**
   * Returns the value associated with with a given key or null if no such key is present in the
   * set of tags.
   *
   * @param id
   *     Identifier with a set of tags to search.
   * @param k
   *     Key to search for.
   * @return
   *     Value for the key or null if the key is not present.
   */
  public static String getTagValue(Id id, String k) {
    Preconditions.checkNotNull(id, "id");
    return getTagValue(id.tags(), k);
  }

  /**
   * Returns the value associated with with a given key or null if no such key is present in the
   * set of tags.
   *
   * @param tags
   *     Set of tags to search.
   * @param k
   *     Key to search for.
   * @return
   *     Value for the key or null if the key is not present.
   */
  public static String getTagValue(Iterable<Tag> tags, String k) {
    Preconditions.checkNotNull(tags, "tags");
    Preconditions.checkNotNull(k, "key");
    for (Tag t : tags) {
      if (k.equals(t.key())) {
        return t.value();
      }
    }
    return null;
  }

  /**
   * Returns the first measurement with a given tag value.
   *
   * @param ms
   *     A set of measurements.
   * @param t
   *     The key and value to search for.
   * @return
   *     Measurement or null if no matches are found.
   */
  public static Measurement first(Iterable<Measurement> ms, Tag t) {
    return first(ms, t.key(), t.value());
  }

  /**
   * Returns the first measurement with a given tag value.
   *
   * @param ms
   *     A set of measurements.
   * @param k
   *     Key to search for.
   * @param v
   *     Value that should be associated with k on the ids.
   * @return
   *     Measurement or null if no matches are found.
   */
  public static Measurement first(final Iterable<Measurement> ms, final String k, final String v) {
    return first(ms, value -> v.equals(getTagValue(value.id(), k)));
  }

  /**
   * Returns the first measurement that matches the predicate.
   *
   * @param ms
   *     A set of measurements.
   * @param p
   *     Predicate to use for selecting values.
   * @return
   *     Measurement or null if no matches are found.
   */
  public static Measurement first(Iterable<Measurement> ms, Predicate<Measurement> p) {
    Iterator<Measurement> it = filter(ms, p).iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * Returns a new iterable restricted to measurements that match the predicate.
   *
   * @param ms
   *     A set of measurements.
   * @param t
   *     The key and value to search for.
   * @return
   *     Measurements matching the predicate.
   */
  public static Iterable<Measurement> filter(Iterable<Measurement> ms, Tag t) {
    return filter(ms, t.key(), t.value());
  }

  /**
   * Returns a new iterable restricted to measurements that match the predicate.
   *
   * @param ms
   *     A set of measurements.
   * @param k
   *     Key to search for.
   * @param v
   *     Value that should be associated with k on the ids.
   * @return
   *     Measurements matching the predicate.
   */
  public static Iterable<Measurement> filter(
      final Iterable<Measurement> ms, final String k, final String v) {
    return filter(ms, value -> v.equals(getTagValue(value.id(), k)));
  }

  /**
   * Returns a new iterable restricted to measurements that match the predicate.
   *
   * @param ms
   *     A set of measurements.
   * @param p
   *     Predicate to use for selecting values.
   * @return
   *     Measurements matching the predicate.
   */
  public static Iterable<Measurement> filter(
      final Iterable<Measurement> ms, final Predicate<Measurement> p) {
    return () -> new FilteredIterator<>(ms.iterator(), p);
  }

  /**
   * Returns a list with a copy of the data from the iterable.
   */
  public static <T> List<T> toList(Iterable<T> iter) {
    List<T> buf = new ArrayList<>();
    for (T v : iter) {
      buf.add(v);
    }
    return buf;
  }

  /**
   * Returns a list with the data from the iterator.
   */
  public static <T> List<T> toList(Iterator<T> iter) {
    List<T> buf = new ArrayList<>();
    while (iter.hasNext()) {
      buf.add(iter.next());
    }
    return buf;
  }

  /**
   * Returns an iterable of tags based on a string array.
   */
  static Iterable<Tag> toIterable(String[] tags) {
    if (tags.length % 2 == 1) {
      throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
    }
    ArrayList<Tag> ts = new ArrayList<>(tags.length);
    for (int i = 0; i < tags.length; i += 2) {
      ts.add(new BasicTag(tags[i], tags[i + 1]));
    }
    return ts;
  }

  /**
   * This method should be used instead of the
   * {@link ConcurrentMap#computeIfAbsent(Object, Function)} call to minimize
   * thread contention. This method does not require locking for the common case
   * where the key exists, but potentially performs additional computation when
   * absent.
   */
  public static <K, V> V computeIfAbsent(ConcurrentMap<K, V> map, K k, Function<K, V> f) {
    V v = map.get(k);
    if (v == null) {
      V tmp = f.apply(k);
      v = map.putIfAbsent(k, tmp);
      if (v == null) {
        v = tmp;
      }
    }
    return v;
  }

  /**
   * Propagate a type error exception.
   * Used in situations where an existing id has already been registered but with a different
   * class.
   */
  public static void propagateTypeError(Registry registry, Id id,
                                        Class<?> desiredClass, Class<?> actualClass) {
    final String dType = desiredClass.getName();
    final String aType = actualClass.getName();
    final String msg = String.format("cannot access '%s' as a %s, it already exists as a %s",
        id, dType, aType);
    registry.propagate(new IllegalStateException(msg));
  }

}
