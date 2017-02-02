/*
 * Copyright 2014-2017 Netflix, Inc.
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
package com.netflix.spectator.agent;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Represents the results from querying data out of JMX.
 */
class JmxData {

  /** Get data from JMX using object name query expression. */
  static List<JmxData> query(String query) throws Exception {
    return query(new ObjectName(query));
  }

  /** Get data from JMX using object name query expression. */
  static List<JmxData> query(ObjectName query) throws Exception {
    return query(ManagementFactory.getPlatformMBeanServer(), query);
  }

  /** Get data from JMX using object name query expression. */
  static List<JmxData> query(MBeanServer server, ObjectName query) throws Exception {
    List<JmxData> data = new ArrayList<>();

    Set<ObjectName> names = server.queryNames(query, null);
    for (ObjectName name : names) {
      MBeanInfo info = server.getMBeanInfo(name);
      MBeanAttributeInfo[] attrs = info.getAttributes();
      String[] attrNames = new String[attrs.length];
      for (int i = 0; i < attrs.length; ++i) {
        attrNames[i] = attrs[i].getName();
      }

      Map<String, String> stringAttrs = new HashMap<>(name.getKeyPropertyList());
      stringAttrs.put("domain", name.getDomain());
      Map<String, Number> numberAttrs = new HashMap<>();
      for (Attribute attr : server.getAttributes(name, attrNames).asList()) {
        Object obj = attr.getValue();
        if (obj instanceof String) {
          stringAttrs.put(attr.getName(), (String) obj);
        } else if (obj instanceof Number) {
          numberAttrs.put(attr.getName(), ((Number) obj).doubleValue());
        } else if (obj instanceof TimeUnit) {
          stringAttrs.put(attr.getName(), obj.toString());
        }
      }

      data.add(new JmxData(name, stringAttrs, numberAttrs));
    }

    return data;
  }

  private final ObjectName name;
  private final Map<String, String> stringAttrs;
  private final Map<String, Number> numberAttrs;

  /**
   * Create a new instance.
   */
  JmxData(ObjectName name, Map<String, String> stringAttrs, Map<String, Number> numberAttrs) {
    this.name = name;
    this.stringAttrs = Collections.unmodifiableMap(stringAttrs);
    this.numberAttrs = Collections.unmodifiableMap(numberAttrs);
  }

  /** Return the name of the bean. */
  ObjectName getName() {
    return name;
  }

  /** Return attributes with string values. */
  Map<String, String> getStringAttrs() {
    return stringAttrs;
  }

  /** Return attributes with numeric values. */
  Map<String, Number> getNumberAttrs() {
    return numberAttrs;
  }

  @Override public String toString() {
    StringBuilder buf = new StringBuilder(256);
    buf.append(name.toString())
        .append("\n- string attributes\n");
    for (Map.Entry<String, String> entry : new TreeMap<>(stringAttrs).entrySet()) {
      buf.append("  - ").append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
    }
    buf.append("- number attributes\n");
    for (Map.Entry<String, Number> entry : new TreeMap<>(numberAttrs).entrySet()) {
      buf.append("  - ").append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
    }
    return buf.toString();
  }
}
