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
package com.netflix.spectator.jvm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JmxDataTest {

  @Test
  public void objectNamePropOverridesAttributes() throws Exception {
    ObjectName id = new ObjectName("CatalinaTest:type=ThreadPool,name=\"http-nio\"");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("modelType", "nio");
    attributes.put("name", "http-nio");
    JmxBean bean = new JmxBean(id, attributes);
    JmxBean.register(bean);

    List<JmxData> results = JmxData.query("CatalinaTest:type=ThreadPool,*");
    Assertions.assertEquals(1, results.size());

    results.forEach(data -> {
      Assertions.assertEquals("nio", data.getStringAttrs().get("modelType"));
      Assertions.assertEquals("\"http-nio\"", data.getStringAttrs().get("name"));
    });

    JmxBean.unregister(bean);
  }
}
