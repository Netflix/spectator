/*
 * Copyright 2014-2021 Netflix, Inc.
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
package com.netflix.spectator.nflx;

import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.config.CompositeConfig;
import com.netflix.archaius.api.exceptions.ConfigException;
import com.netflix.archaius.config.DefaultCompositeConfig;
import com.netflix.archaius.config.EnvironmentConfig;
import com.netflix.archaius.config.MapConfig;
import com.netflix.archaius.config.SystemConfig;
import com.netflix.spectator.nflx.tagging.NetflixTagging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Utility class for creating a configuration to be used in the Netflix environment.
 * This class is not intended to be relied on by others, and is subject to change at any time.
 */
public final class NetflixConfig {
  private NetflixConfig() {
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(NetflixConfig.class);

  /**
   * Create a config to be used in the Netflix Cloud.
   */
  public static Config createConfig(Config config) {
    try {
      // We cannot use the libraries layer as the nf-archaius2-platform-bridge
      // will delegate the Config binding to archaius1 and the libraries layer
      // then wraps that. So anything added to the libraries layer will not get
      // picked up by anything injecting Config.
      //
      // Instead we just create a composite where the injected config will override
      // the prop files. If no config binding is present use the environment and
      // system properties config.
      CompositeConfig cc = new DefaultCompositeConfig(true);
      cc.addConfig("ATLAS", loadPropFiles());
      cc.addConfig("ENVIRONMENT", EnvironmentConfig.INSTANCE);
      cc.addConfig("SYS", SystemConfig.INSTANCE);

      if (config != null) {
        LOGGER.debug("found injected config, adding as override layer");
        cc.addConfig("INJECTED", config);
      } else {
        LOGGER.debug("no injected config found");
      }

      return cc;
    } catch (ConfigException e) {
      throw new IllegalStateException("failed to load atlas config", e);
    }
  }

  static Config loadPropFiles() {
    final Properties props = new Properties();
    Env.addDefaults(props);
    final String env = Env.environment();
    final String acctId = Env.accountId();
    tryLoadingConfig(props, "atlas_plugin");
    tryLoadingConfig(props, "atlas_plugin-" + env);
    tryLoadingConfig(props, "atlas_plugin-acct-" + acctId);
    return MapConfig.from(props);
  }

  private static void tryLoadingConfig(Properties props, String name) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Enumeration<URL> resources = loader.getResources(name + ".properties");
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        LOGGER.info("loading config file: {}", url);
        try (InputStream in = url.openStream()) {
          props.load(in);
        }
      }
    } catch (IOException e) {
      LOGGER.warn("failed to load config file: " + name, e);
    }
  }

  /**
   * Infrastructure tags that are common across all metrics. Used for deduplication
   * and for providing a scope for the metrics produced.
   */
  public static Map<String, String> commonTags() {
    return NetflixTagging.commonTagsForAtlas();
  }

  private static final class Env {
    private static final String OWNER = "EC2_OWNER_ID";
    private static final String REGION = "EC2_REGION";

    private static final String ENVIRONMENT = "NETFLIX_ENVIRONMENT";
    private static final String APP = "NETFLIX_APP";
    private static final String CLUSTER = "NETFLIX_CLUSTER";

    private static String getenv(String k, String dflt) {
      String v = System.getenv(k);
      return (v == null) ? dflt : v;
    }

    private static String environment() {
      return getenv(ENVIRONMENT, "test");
    }

    private static String accountId() {
      return getenv(OWNER, "unknown");
    }

    /**
     * Set default values for environment variables that are used for the basic context
     * of where an app is running. Helps avoid startup issues when running locally and
     * these are not set.
     */
    private static void addDefaults(Properties props) {
      props.put(ENVIRONMENT, "test");
      props.put(OWNER, "unknown");
      props.put(REGION, "us-east-1");
      props.put(APP, "local");
      props.put(CLUSTER, "local-dev");
    }
  }
}
