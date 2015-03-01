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
package com.netflix.spectator.tdigest;

import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.google.inject.AbstractModule;
import com.netflix.config.ConfigurationManager;
import com.netflix.spectator.api.Spectator;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Guice module to configure the plugin.
 */
public class TDigestModule extends AbstractModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(TDigestModule.class);

  private static final String ENDPOINT_PROP = "spectator.tdigest.kinesis.endpoint";
  private static final String STREAM_PROP = "spectator.tdigest.kinesis.stream";

  private void loadProperties(String name) {
    try {
      ConfigurationManager.loadCascadedPropertiesFromResources(name);
    } catch (IOException e) {
      LOGGER.warn("failed to load properties for '" + name + "'");
    }
  }

  private AmazonKinesisClient newKinesisClient(AbstractConfiguration cfg) {
    String endpoint = cfg.getString(ENDPOINT_PROP);
    AmazonKinesisClient client = new AmazonKinesisClient();
    client.setEndpoint(endpoint);
    return client;
  }

  @Override protected void configure() {
    loadProperties("spectator-tdigest");
    AbstractConfiguration cfg = ConfigurationManager.getConfigInstance();
    String stream = cfg.getString(STREAM_PROP);
    if (stream == null) {
      throw new IllegalStateException("stream name property, " + STREAM_PROP + ", is not set");
    }

    TDigestRegistry registry = Spectator.registry().underlying(TDigestRegistry.class);
    if (registry == null) {
      throw new IllegalStateException("TDigestRegistry is not being used");
    }

    KinesisTDigestWriter writer = new KinesisTDigestWriter(newKinesisClient(cfg), stream);
    TDigestPlugin plugin = new TDigestPlugin(registry, writer);
    plugin.init();
    bind(TDigestPlugin.class).toInstance(plugin);
  }
}
