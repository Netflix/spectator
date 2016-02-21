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
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import javax.inject.Singleton;

/**
 * Guice module to configure the plugin.
 *
 * @deprecated Use {@link com.netflix.spectator.api.histogram.PercentileTimer} instead.
 */
@Deprecated
public class TDigestModule extends AbstractModule {

  @Override protected void configure() {
    bind(TDigestPlugin.class).asEagerSingleton();
  }

  @Provides
  @Singleton
  private TDigestConfig providesDigestConfig(OptionalInjections opts) {
    return new TDigestConfig(opts.getConfig().getConfig("spectator.tdigest"));
  }

  @Provides
  @Singleton
  private TDigestRegistry providesRegistry(Registry registry, TDigestConfig config) {
    return new TDigestRegistry(registry, config);
  }

  @Provides
  @Singleton
  private TDigestWriter providesWriter(Registry registry, TDigestConfig config) {
    AmazonKinesisClient client = new AmazonKinesisClient();
    client.setEndpoint(config.getEndpoint());
    return new KinesisTDigestWriter(registry, client, config);
  }

  private static class OptionalInjections {
    @Inject(optional = true)
    private Config config;

    Config getConfig() {
      return (config == null) ? ConfigFactory.load() : config;
    }
  }
}
