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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Static factory used to access the main global registry. The registry class to use can be
 * set with the system property <code>spectator.api.registryClass</code>.
 */
public final class Spectator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Spectator.class);

  private static final ConfigMap CONFIG = newConfigMapUsingServiceLoader();

  private static final ExtendedRegistry REGISTRY =
    new ExtendedRegistry(newInstance(Config.registryClass()));

  /**
   * Create a new config map instance using {@link java.util.ServiceLoader}. If no implementations
   * are found the default will be used.
   */
  static ConfigMap newConfigMapUsingServiceLoader() {
    final ServiceLoader<ConfigMap> loader = ServiceLoader.load(ConfigMap.class);
    final Iterator<ConfigMap> cfgIterator = loader.iterator();
    if (cfgIterator.hasNext()) {
      ConfigMap cfg = cfgIterator.next();
      LOGGER.info("using config impl found in classpath: {}", cfg.getClass().getName());
      return cfg;
    } else {
      LOGGER.warn("no config impl found in classpath, using default");
      return new SystemConfigMap();
    }
  }

  /**
   * Create a new registry instance using {@link java.util.ServiceLoader}. If no implementations
   * are found the default will be used.
   */
  static Registry newInstanceUsingServiceLoader() {
    final ClassLoader cl = pickClassLoader();
    final ServiceLoader<Registry> loader = ServiceLoader.load(Registry.class, cl);
    final Iterator<Registry> registryIterator = loader.iterator();
    if (registryIterator.hasNext()) {
      StringBuilder desc = new StringBuilder();
      List<Registry> rs = new ArrayList<>();
      while (registryIterator.hasNext()) {
        try {
          Registry r = registryIterator.next();
          desc.append(' ').append(r.getClass().getName());
          rs.add(r);
        } catch (ServiceConfigurationError e) {
          LOGGER.warn("failed to load registry, it will be skipped", e);
        }
      }
      if (rs.isEmpty()) {
        return new DefaultRegistry();
      } else {
        Registry r = (rs.size() == 1)
            ? rs.get(0)
            : new CompositeRegistry(Clock.SYSTEM, rs.toArray(new Registry[rs.size()]));
        LOGGER.info("using registries found in classpath: {}", desc.toString());
        return r;
      }
    } else {
      LOGGER.warn("no registry impl found in classpath, using default");
      return new DefaultRegistry();
    }
  }

  @SuppressWarnings("PMD.UseProperClassLoader")
  private static ClassLoader pickClassLoader() {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      LOGGER.info("Thread.currentThread().getContextClassLoader() is null,"
          + " using Spectator.class.getClassLoader()");
      return Spectator.class.getClassLoader();
    } else {
      return cl;
    }
  }

  /**
   * Create a new registry instance using the class name specified by the system property
   * {@code spectator.api.registryClass}. If no implementations are found the default will be used.
   */
  static Registry newInstanceUsingClassName(String name) {
    try {
      final Class<?> c = Class.forName(name);
      return (Registry) c.newInstance();
    } catch (Exception e) {
      final String msg = "failed to instantiate registry class '" + name
        + "', falling back to default implementation";
      Throwables.propagate(new RuntimeException(msg, e));
      return new DefaultRegistry();
    }
  }

  /** Create a new instance of the registry. */
  static Registry newInstance(String name) {
    return Config.SERVICE_LOADER.equals(name)
      ? newInstanceUsingServiceLoader()
      : newInstanceUsingClassName(name);
  }

  /**
   * Return the config implementation being used.
   */
  public static ConfigMap config() {
    return CONFIG;
  }

  /**
   * Return the default global registry implementation. The implementation used will depend on the
   * system property {@code spectator.api.registryClass}. If not set or set to
   * {@code service-loader} the registry class will be determined by scanning the classpath using
   * {@link java.util.ServiceLoader}. Otherwise an instance of the classname specified will be
   * used. If a registry cannot be loaded the fallback is to use the {@link DefaultRegistry}.
   * When {@code spectator.api.propagateWarnings} is set to {@code true} and an explicit class name
   * is specified a {@link java.lang.RuntimeException} will be thrown if the specified class cannot
   * be used.
   */
  public static ExtendedRegistry registry() {
    return REGISTRY;
  }

  private Spectator() {
  }
}
