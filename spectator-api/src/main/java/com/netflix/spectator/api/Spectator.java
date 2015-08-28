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

  private static final CompositeRegistry COMPOSITE_REGISTRY = new CompositeRegistry(Clock.SYSTEM);
  private static final ExtendedRegistry REGISTRY = new ExtendedRegistry(COMPOSITE_REGISTRY);

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
  static void addRegistriesFromServiceLoader() {
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
        COMPOSITE_REGISTRY.add(new DefaultRegistry());
      } else {
        for (Registry r : rs) {
          COMPOSITE_REGISTRY.add(r);
        }
        LOGGER.info("using registries found in classpath: {}", desc.toString());
      }
    } else {
      LOGGER.warn("no registry impl found in classpath, using default");
      COMPOSITE_REGISTRY.add(new DefaultRegistry());
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
  static void addRegistryUsingClassName(String name) {
    try {
      final Class<?> c = Class.forName(name);
      COMPOSITE_REGISTRY.add((Registry) c.newInstance());
    } catch (Exception e) {
      final String msg = "failed to instantiate registry class '" + name
        + "', falling back to default implementation";
      Throwables.propagate(new RuntimeException(msg, e));
      COMPOSITE_REGISTRY.add(new DefaultRegistry());
    }
  }

  /** Create a new instance of the registry. */
  static void addRegistries(String name) {
    if (Config.SERVICE_LOADER.equals(name)) {
      addRegistriesFromServiceLoader();
    } else {
      addRegistryUsingClassName(name);
    }
  }

  /**
   * Return the config implementation being used.
   */
  public static ConfigMap config() {
    return CONFIG;
  }

  /**
   * Setup the default global registry implementation. The implementation used will depend on the
   * system property {@code spectator.api.registryClass}. If not set or set to
   * {@code service-loader} the registry class will be determined by scanning the classpath using
   * {@link java.util.ServiceLoader}. Otherwise an instance of the classname specified will be
   * used. If a registry cannot be loaded the fallback is to use the {@link DefaultRegistry}.
   * When {@code spectator.api.propagateWarnings} is set to {@code true} and an explicit class name
   * is specified a {@link java.lang.RuntimeException} will be thrown if the specified class cannot
   * be used.
   *
   * @deprecated This provides the legacy behavior if needed. It is preferred to setup a registry
   * and inject. If the static
   */
  @Deprecated
  public static void initializeUsingServiceLoader() {
    addRegistries(Config.registryClass());
  }

  /**
   * Returns the global registry.
   *
   * @deprecated Use injection or {@link #globalRegistry()} instead.
   */
  @Deprecated
  public static ExtendedRegistry registry() {
    return REGISTRY;
  }

  /**
   * Returns the global composite registry. This method can be used for use-cases where it is
   * necessary to get a static reference to a registry. It will not do anything unless other
   * registries are added. Example:
   *
   * <pre>
   * class Main {
   *   public static void main(String[] args) {
   *     // This is the preferred usage and works well with DI libraries like guice. Setup a
   *     // registry and pass it in as needed.
   *     Registry registry = new DefaultRegistry();
   *     (new Example1(registry)).start();
   *
   *     // If it is desirable to get data from things using the global registry, then the
   *     // registry for the application can be added to the global context.
   *     Spectator.globalRegistry().add(registry);
   *     Example2 ex2 = new Example2();
   *     ex2.start();
   *
   *     // If the lifecycle is not the same as the jvm, then the registry should be removed
   *     // when shutting down.
   *     ex2.onShutdown(() -> Spectator.globalRegistry().remove(registry));
   *   }
   * }
   *
   * class Example1 {
   *   private final Counter c;
   *
   *   Example1(Registry registry) {
   *     c = registry.counter("example1");
   *   }
   *   ...
   * }
   *
   * class Example2 {
   *   private final Counter c;
   *
   *   Example2() {
   *     c = Spectator.globalRegistry().counter("example1");
   *   }
   *   ...
   * }
   * </pre>
   */
  public static CompositeRegistry globalRegistry() {
    return COMPOSITE_REGISTRY;
  }

  private Spectator() {
  }
}
