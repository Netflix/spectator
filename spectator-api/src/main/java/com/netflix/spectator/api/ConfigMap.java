/**
 * Copyright 2014 Netflix, Inc.
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

/**
 * Configuration settings.
 */
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
  String get(String key, String dflt);

  /**
   * Get an int value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  int getInt(String key);

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
  int getInt(String key, int dflt);

  /**
   * Get an long value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  long getLong(String key);

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
  long getLong(String key, long dflt);

  /**
   * Get an double value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  double getDouble(String key);

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
  double getDouble(String key, double dflt);

  /**
   * Get an boolean value associated with a given key.
   *
   * @param key
   *     Property name to lookup.
   * @return
   *     Value associated with the key or throws NoSuchElementException if not found.
   */
  boolean getBoolean(String key);

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
  boolean getBoolean(String key, boolean dflt);
}
