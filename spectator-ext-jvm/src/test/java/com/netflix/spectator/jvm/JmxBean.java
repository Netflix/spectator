/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.TreeMap;

class JmxBean implements DynamicMBean {

  private static MBeanServer MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

  static void register(JmxBean bean) throws Exception {
    if (MBEAN_SERVER.isRegistered(bean.id)) {
      MBEAN_SERVER.unregisterMBean(bean.id);
    }
    MBEAN_SERVER.registerMBean(bean, bean.id);
  }

  static void unregister(JmxBean bean) throws Exception {
    if (MBEAN_SERVER.isRegistered(bean.id)) {
      MBEAN_SERVER.unregisterMBean(bean.id);
    }
  }

  private final ObjectName id;
  private final Map<String, Object> attributes;

  JmxBean(ObjectName id, Map<String, Object> attributes) {
    this.id = id;
    this.attributes = new TreeMap<>(attributes);
  }

  @Override
  public Object getAttribute(String name)
      throws AttributeNotFoundException, MBeanException, ReflectionException {
    Object value = attributes.get(name);
    if (value == null) {
      throw new AttributeNotFoundException("no attribute '" + name + "' for jmx bean '" + id + "'");
    }
    return value;
  }

  @Override
  public AttributeList getAttributes(String[] names) {
    AttributeList list = new AttributeList();
    for (String name : names) {
      try {
        list.add(new Attribute(name, getAttribute(name)));
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }
    return list;
  }

  @Override
  public void setAttribute(Attribute attribute)
      throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
    throw new UnsupportedOperationException("mbean '" + id + "' is read-only");
  }

  @Override
  public AttributeList setAttributes(AttributeList attributes) {
    throw new UnsupportedOperationException("mbean '" + id + "' is read-only");
  }

  @Override
  public Object invoke(String actionName, Object[] params, String[] signature)
      throws MBeanException, ReflectionException {
    throw new UnsupportedOperationException("mbean '" + id + "' is read-only");
  }

  @Override
  public MBeanInfo getMBeanInfo() {
    MBeanAttributeInfo[] mbeanAttributes = new MBeanAttributeInfo[attributes.size()];
    int i = 0;
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      String attrName = entry.getKey();
      Object attrValue = entry.getValue();
      String typeName = (attrValue instanceof Number)
          ? Number.class.getName()
          : String.class.getName();
      boolean isReadable = true;
      boolean isWritable = false;
      boolean isIs = false;
      mbeanAttributes[i++] = new MBeanAttributeInfo(
          attrName, typeName, "???", isReadable, isWritable, isIs);
    }
    return new MBeanInfo(getClass().getName(), "???", mbeanAttributes, null, null, null);
  }
}
