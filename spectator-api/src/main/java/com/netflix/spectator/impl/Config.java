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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.RegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Function;

/**
 * Helper methods for accessing configuration settings.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class Config {

  private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

  private static final String PREFIX = "spectator.api.";

  private static final Map<Class<?>, Function<String, Object>> PARSERS = new HashMap<>();

  static {
    PARSERS.put(Boolean.class,        Boolean::valueOf);
    PARSERS.put(Boolean.TYPE,         Boolean::valueOf);

    PARSERS.put(Byte.class,           Byte::valueOf);
    PARSERS.put(Byte.TYPE,            Byte::valueOf);
    PARSERS.put(Short.class,          Short::valueOf);
    PARSERS.put(Short.TYPE,           Short::valueOf);
    PARSERS.put(Integer.class,        Integer::valueOf);
    PARSERS.put(Integer.TYPE,         Integer::valueOf);
    PARSERS.put(Long.class,           Long::valueOf);
    PARSERS.put(Long.TYPE,            Long::valueOf);

    PARSERS.put(Float.class,          Float::valueOf);
    PARSERS.put(Float.TYPE,           Float::valueOf);
    PARSERS.put(Double.class,         Double::valueOf);
    PARSERS.put(Double.TYPE,          Double::valueOf);

    PARSERS.put(Character.class,      s -> s.charAt(0));
    PARSERS.put(Character.TYPE,       s -> s.charAt(0));
    PARSERS.put(String.class,         s -> s);

    PARSERS.put(Duration.class,       Duration::parse);
    PARSERS.put(Period.class,         Period::parse);
    PARSERS.put(Instant.class,        Instant::parse);
    PARSERS.put(ZonedDateTime.class,  ZonedDateTime::parse);
    PARSERS.put(ZoneId.class,         ZoneId::of);
  }

  private static final RegistryConfig DEFAULT_CONFIG = usingSystemProperties("spectator.api.");

  private Config() {
  }

  @SuppressWarnings("PMD.UseProperClassLoader")
  private static ClassLoader classLoader() {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    return (cl == null) ? Config.class.getClassLoader() : cl;
  }

  private static Object valueOf(Class<?> to, String value) {
    return PARSERS.get(to).apply(value);
  }

  private static Constructor<MethodHandles.Lookup> getConstructor() {
    // https://rmannibucau.wordpress.com/2014/03/27/java-8-default-interface-methods-and-jdk-dynamic-proxies/
    try {
      final Constructor<MethodHandles.Lookup> constructor =
          MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      return constructor;
    } catch (Exception e) {
      LOGGER.error("failed to make MethodHandles.Lookup accessible, config proxy may not work", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a proxy class that implements the inferface specified.
   *
   * @param cls
   *     Interface that represents configuration settings.
   * @param props
   *     A function that maps a string key to a string value. This is the source of configuration
   *     settings. It is expected that the function is thread safe. The key used to lookup a
   *     value is the method name that is invoked on the interface.
   * @return
   *     Instance of the interface that maps methods to the corresponding key in the {@code props}
   *     function.
   */
  @SuppressWarnings("unchecked")
  public static <T> T createProxy(Class<T> cls, Function<String, String> props) {
    final Constructor<MethodHandles.Lookup> constructor = getConstructor();
    final Class<?>[] interfaces = new Class<?>[] {cls};
    return (T) Proxy.newProxyInstance(classLoader(), interfaces, (proxy, method, args) -> {
      final String name = method.getName();
      if (method.isDefault()) {
        final Class<?> declaringClass = method.getDeclaringClass();
        return constructor.newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
            .unreflectSpecial(method, declaringClass)
            .bindTo(proxy)
            .invokeWithArguments(args);
      } else if ("get".equals(method.getName())) {
        return props.apply((String) args[0]);
      } else {
        Class<?> rt = method.getReturnType();
        String v = props.apply(name);
        if (v == null) {
          throw new NoSuchElementException("could not find value for config setting: " + name);
        }
        return valueOf(rt, v);
      }
    });
  }

  /**
   * Create a proxy class that implements the inferface specified.
   *
   * @param cls
   *     Interface that represents configuration settings.
   * @param props
   *     Map storing the properties. It is expected that the map implementation is thread safe.
   *     The key used to lookup a value is the method name that is invoked on the interface.
   * @return
   *     Instance of the interface that maps methods to the corresponding key in the {@code props}
   *     map.
   */
  public static <T> T usingMap(Class<T> cls, Map<String, String> props) {
    return createProxy(cls, props::get);
  }

  /**
   * Create a proxy class that implements the inferface specified.
   *
   * @param cls
   *     Interface that represents configuration settings.
   * @param props
   *     Properties object storing the settings. The key used to lookup a value is the method name
   *     that is invoked on the interface.
   * @param prefix
   *     Prefix applied to the key before looking it up in the properties object.
   * @return
   *     Instance of the interface that maps methods to the corresponding key in the {@code props}
   *     object.
   */
  public static <T> T usingProperties(Class<T> cls, Properties props, String prefix) {
    return createProxy(cls, k -> props.getProperty(prefix + k));
  }

  /**
   * Create a proxy class that implements the inferface specified. Data will come from
   * {@link System#getProperties()}.
   *
   * @param cls
   *     Interface that represents configuration settings.
   * @param prefix
   *     Prefix applied to the key before looking it up in the properties object.
   * @return
   *     Instance of the interface that maps methods to the corresponding key in the {@code props}
   *     object.
   */
  public static <T> T usingSystemProperties(Class<T> cls, String prefix) {
    return createProxy(cls, k -> System.getProperty(prefix + k));
  }

  /**
   * Create an instance of RegistryConfig backed by map. The map implementation should be
   * thread-safe or not get modified after the config instance is created.
   *
   * @return
   *     Instance of RegistryConfig.
   */
  public static RegistryConfig usingMap(Map<String, String> props) {
    return usingMap(RegistryConfig.class, props);
  }

  /**
   * Create an instance of RegistryConfig backed by the provided properties object.
   *
   * @param prefix
   *     Prefix applied to the key before looking it up in the properties object.
   * @return
   *     Instance of RegistryConfig.
   */
  public static RegistryConfig usingProperties(Properties props, String prefix) {
    return usingProperties(RegistryConfig.class, props, prefix);
  }

  /**
   * Create an instance of RegistryConfig backed by system properties.
   *
   * @param prefix
   *     Prefix applied to the key before looking it up in the properties object.
   * @return
   *     Instance of RegistryConfig.
   */
  public static RegistryConfig usingSystemProperties(String prefix) {
    return usingSystemProperties(RegistryConfig.class, prefix);
  }

  /**
   * Returns a default implementation of the registry config backed by system properties.
   */
  public static RegistryConfig defaultConfig() {
    return DEFAULT_CONFIG;
  }

  private static String get(String k) {
    return System.getProperty(k);
  }

  private static String get(String k, String dflt) {
    final String v = get(k);
    return (v == null) ? dflt : v;
  }

  /** Should an exception be thrown for warnings? */
  public static boolean propagateWarnings() {
    return Boolean.valueOf(get(PREFIX + "propagateWarnings", "false"));
  }

  /**
   * For classes based on {@link com.netflix.spectator.api.AbstractRegistry} this setting is used
   * to determine the maximum number of registered meters permitted. This limit is used to help
   * protect the system from a memory leak if there is a bug or irresponsible usage of registering
   * meters.
   *
   * @return
   *     Maximum number of distinct meters that can be registered at a given time. The default is
   *     {@link java.lang.Integer#MAX_VALUE}.
   */
  public static int maxNumberOfMeters() {
    final String v = get(PREFIX + "maxNumberOfMeters");
    return (v == null) ? Integer.MAX_VALUE : Integer.parseInt(v);
  }
}
