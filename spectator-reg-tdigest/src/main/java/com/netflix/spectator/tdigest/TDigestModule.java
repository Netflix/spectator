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
import com.google.inject.Provides;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * Guice module to configure the plugin.
 */
public class TDigestModule extends AbstractModule {

  @Override protected void configure() {
    install(ArchaiusModule.forProxy(TDigestConfig.class));
    bind(TDigestPlugin.class).asEagerSingleton();
  }

  @Provides private TDigestRegistry providesRegistry(ExtendedRegistry registry) {
    return registry.underlying(TDigestRegistry.class);
  }

  @Provides private TDigestWriter providesWriter(TDigestConfig config) {
    AmazonKinesisClient client = new AmazonKinesisClient();
    client.setEndpoint(config.endpoint());
    return new KinesisTDigestWriter(client, config.stream());
  }
}
