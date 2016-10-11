/*
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
package com.netflix.spectator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.management.ObjectName;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to read sample files created using {@code jmx-dump}.
 *
 * <pre>
 * $ curl -o jmx-dump.jar -L 'https://bintray.com/r4um/generic/download_file?file_path=jmx-dump-0.4.2-standalone.jar'
 * $ java -jar jmx-dump.jar --local ${pid} --dump-all
 * </pre>
 */
public class Json {

  private static final ObjectMapper mapper = new ObjectMapper();

  @SuppressWarnings("unchecked")
  static List<JmxData> load(String resource) {
    try {
      List<JmxData> results = new ArrayList<>();
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      try (InputStream in = cl.getResourceAsStream(resource)) {
        Map<String, Object> data = (Map<String, Object>) mapper.readValue(in, Map.class);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
          ObjectName name = new ObjectName(entry.getKey());
          Map<String, Object> attrs = (Map<String, Object>) entry.getValue();
          Map<String, String> strings = new HashMap<>(name.getKeyPropertyList());
          Map<String, Number> numbers = new HashMap<>();
          for (Map.Entry<String, Object> attr : attrs.entrySet()) {
            Object obj = attr.getValue();
            if (obj instanceof Number) {
              numbers.put(attr.getKey(), (Number) obj);
            } else if (obj != null) {
              strings.put(attr.getKey(), obj.toString());
            }
          }
          results.add(new JmxData(name, strings, numbers));
        }
      }
      return results;
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid json resource: " + resource, e);
    }
  }
}
