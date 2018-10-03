/*
 * Copyright 2018 Netflix, Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
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

  private static Config loadPropFiles() {
    final Properties props = new Properties();
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

  private static void put(Map<String, String> map, String key, String maybeNullValue) {
    if (maybeNullValue != null) {
      map.put(key, maybeNullValue);
    }
  }

  /**
   * Infrastructure tags that are common across all metrics. Used for deduplication
   * and for providing a scope for the metrics produced.
   */
  public static Map<String, String> commonTags() {
    final Map<String, String> commonTags = new HashMap<>();
    put(commonTags, "nf.app", Env.app());
    put(commonTags, "nf.cluster", Env.cluster());
    put(commonTags, "nf.asg", Env.asg());
    put(commonTags, "nf.stack", Env.stack());
    put(commonTags, "nf.zone", Env.zone());
    put(commonTags, "nf.vmtype", Env.vmtype());
    put(commonTags, "nf.node", Env.instanceId());
    put(commonTags, "nf.region", Env.region());
    put(commonTags, "nf.account", Env.accountId());
    put(commonTags, "nf.task", Env.task());
    put(commonTags, "nf.job", Env.job());
    return commonTags;
  }

  private static class Env {
    private static final String OWNER = "EC2_OWNER_ID";
    private static final String REGION = "EC2_REGION";
    private static final String ZONE = "EC2_AVAILABILITY_ZONE";
    private static final String INSTANCE_ID = "EC2_INSTANCE_ID";
    private static final String VM_TYPE = "EC2_INSTANCE_TYPE";

    private static final String ENVIRONMENT = "NETFLIX_ENVIRONMENT";
    private static final String APP = "NETFLIX_APP";
    private static final String CLUSTER = "NETFLIX_CLUSTER";
    private static final String ASG = "NETFLIX_AUTO_SCALE_GROUP";
    private static final String STACK = "NETFLIX_STACK";

    private static final String TASK = "TITUS_TASK_ID";
    private static final String JOB = "TITUS_JOB_ID";
    private static final String TITUS_INSTANCE_ID = "TITUS_TASK_INSTANCE_ID";

    private static String getenv(String k, String dflt) {
      String v = System.getenv(k);
      return (v == null) ? dflt : v;
    }

    private static String environment() {
      return getenv(ENVIRONMENT, "dev");
    }

    private static String accountId() {
      return getenv(OWNER, "dc");
    }

    private static String region() {
      return System.getenv(REGION);
    }

    private static String zone() {
      return System.getenv(ZONE);
    }

    private static String app() {
      return System.getenv(APP);
    }

    private static String cluster() {
      return System.getenv(CLUSTER);
    }

    private static String asg() {
      return System.getenv(ASG);
    }

    private static String task() {
      return System.getenv(TASK);
    }

    private static String job() {
      return System.getenv(JOB);
    }

    private static String stack() {
      return System.getenv(STACK);
    }

    private static String vmtype() {
      return System.getenv(VM_TYPE);
    }

    private static String instanceId() {
      String id = getenv(TITUS_INSTANCE_ID, System.getenv(INSTANCE_ID));
      if (id != null) {
        return id;
      }

      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
