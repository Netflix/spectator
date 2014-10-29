package com.netflix.spectator.api;

import java.util.NoSuchElementException;

/**
 * Base class for {@code ConfigMap} implementations.
 */
public abstract class AbstractConfigMap implements ConfigMap {

  @Override public String get(String key, String dflt) {
    final String v = get(key);
    return (v == null) ? dflt : v;
  }

  private String getOrThrow(String key) {
    final String v = get(key);
    if (v == null) {
      throw new NoSuchElementException(key);
    }
    return v;
  }

  @Override public int getInt(String key) {
    return Integer.parseInt(getOrThrow(key));
  }

  @Override public int getInt(String key, int dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Integer.parseInt(v);
  }

  @Override public long getLong(String key) {
    return Long.parseLong(getOrThrow(key));
  }

  @Override public long getLong(String key, long dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Long.parseLong(v);
  }

  @Override public double getDouble(String key) {
    return Double.parseDouble(getOrThrow(key));
  }

  @Override public double getDouble(String key, double dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Double.parseDouble(v);
  }

  @Override public boolean getBoolean(String key) {
    return Boolean.parseBoolean(getOrThrow(key));
  }

  @Override public boolean getBoolean(String key, boolean dflt) {
    final String v = get(key);
    return (v == null) ? dflt : Boolean.parseBoolean(v);
  }
}
