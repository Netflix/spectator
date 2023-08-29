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
package com.netflix.spectator.ipcservlet;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * This is a hack to work around <a href="https://github.com/google/guice/issues/807">#807</a>.
 * It uses reflection to extract the servlet path based on the pattern that was used for the
 * guice bindings.
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
final class ServletPathHack {

  private ServletPathHack() {
  }

  private static final String PACKAGE = "com.google.inject.servlet";

  private static volatile boolean hackWorks = true;

  private static Object get(Object obj, String name) throws Exception {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(obj);
  }

  private static boolean matches(Object obj, String path) throws Exception {
    Method m = obj.getClass().getDeclaredMethod("matches", String.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(obj, path);
  }

  private static String extractPath(Object obj, String path) throws Exception {
    Method m = obj.getClass().getDeclaredMethod("extractPath", String.class);
    m.setAccessible(true);
    return (String) m.invoke(obj, path);
  }

  /** Helper to get the servlet path for the request. */
  static String getServletPath(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    if (hackWorks && PACKAGE.equals(request.getClass().getPackage().getName())) {
      try {
        // In guice 4.1.0, we need to go through a wrapper object to get to the servlet
        // pipeline
        Object outer;
        String pipelineField = "servletPipeline";
        try {
          outer = get(request, "this$0");
        } catch (NoSuchFieldException e) {
          // For later versions like guice 5.0.1, just use the request and
          outer = request;
          pipelineField = "val$" + pipelineField;
        }
        Object servletPipeline = get(outer, pipelineField);
        Object servletDefs = get(servletPipeline, "servletDefinitions");
        int length = Array.getLength(servletDefs);
        for (int i = 0; i < length; ++i) {
          Object pattern = get(Array.get(servletDefs, i), "patternMatcher");
          if (matches(pattern, servletPath)) {
            servletPath = extractPath(pattern, servletPath);
            break;
          }
        }
      } catch (Exception e) {
        hackWorks = false;
      }
    }
    return servletPath;
  }
}
