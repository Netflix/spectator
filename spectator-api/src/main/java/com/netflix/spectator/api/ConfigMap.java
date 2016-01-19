/**
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
package com.netflix.spectator.api;

import java.util.NoSuchElementException;

/**
 * Configuration settings.
 *
 * @deprecated Scheduled to be removed in February 2016.
 */
@Deprecated
public interface ConfigMap {
  /**
   * Returns the property value associated with a given key or null if no value is set. The
   * implementation should be thread-safe and may change over time if attached to a source that
   * can dynamically update.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or null if not set.
   */
  String get(String key);

  /**
   * Returns the property value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @param dflt
   *     Default value to return if the property is not set.
   * @return
   *     Value associated with the key or {@code dflt} if not set.
   */
  default String get(String key, String dflt) {
    final String v = get(key);
    return (v == null) ? dflt : v;
  }

  /**
   * Get an int value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  default int getInt(String key) {
    final String v = get(key);
    if (v == null) {
      throw new NoSuchElementException(key);
    }
    return Integer.parseInt(v);
  }

  /**
   * Get an int value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @param dflt
   *     Default value to return if the property is not set.
   * @return
   *     Value associated with the key or {@code dflt} if not set.
   */
  default int getInt(String key, int dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Integer.parseInt(v);
  }

  /**
   * Get an long value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  default long getLong(String key) {
    final String v = get(key);
    if (v == null) {
      throw new NoSuchElementException(key);
    }
    return Long.parseLong(v);
  }

  /**
   * Get an long value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @param dflt
   *     Default value to return if the property is not set.
   * @return
   *     Value associated with the key or {@code dflt} if not set.
   */
  default long getLong(String key, long dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Long.parseLong(v);
  }

  /**
   * Get an double value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  default double getDouble(String key) {
    final String v = get(key);
    if (v == null) {
      throw new NoSuchElementException(key);
    }
    return Double.parseDouble(v);
  }

  /**
   * Get an double value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @param dflt
   *     Default value to return if the property is not set.
   * @return
   *     Value associated with the key or {@code dflt} if not set.
   */
  default double getDouble(String key, double dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Double.parseDouble(v);
  }

  /**
   * Get an boolean value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  default boolean getBoolean(String key) {
    final String v = get(key);
    if (v == null) {
      throw new NoSuchElementException(key);
    }
    return Boolean.parseBoolean(v);
  }

  /**
   * Get an boolean value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @param dflt
   *     Default value to return if the property is not set.
   * @return
   *     Value associated with the key or {@code dflt} if not set.
   */
  default boolean getBoolean(String key, boolean dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Boolean.parseBoolean(v);
  }
}
