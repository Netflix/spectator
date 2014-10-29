package com.netflix.spectator.nflx;

import com.netflix.config.DynamicStringProperty;
import com.netflix.spectator.api.AbstractConfigMap;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Config map implementation backed by Archaius to support dynamic properties.
 */
public class ArchaiusConfigMap extends AbstractConfigMap {

  private final ConcurrentHashMap<String, DynamicStringProperty> dynProps =
      new ConcurrentHashMap<>();

  @Override public String get(String key) {
    DynamicStringProperty p = dynProps.get(key);
    if (p == null) {
      DynamicStringProperty tmp = new DynamicStringProperty(key, null);
      p = dynProps.putIfAbsent(key, tmp);
      if (p == null) {
        p = tmp;
      }
    }
    return p.get();
  }
}
