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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Guice module to configure the plugin.
 */
public final class TDigestTestModule extends AbstractModule {

  /** Create a new instance of the test module. */
  public static TDigestTestModule create() {
    return new TDigestTestModule(null);
  }

  /** Create a new instance of the test module that writes to a file. */
  static TDigestTestModule file(File output) {
    return new TDigestTestModule(output);
  }

  private final File output;

  private TDigestTestModule(File output) {
    super();
    this.output = output;
  }

  @Override protected void configure() {
    bind(TDigestPlugin.class).asEagerSingleton();
  }

  @Provides
  @Singleton
  private TDigestConfig providesDigestConfig() {
    Config cfg = ConfigFactory.parseResources("test.conf");
    return new TDigestConfig(cfg.getConfig("spectator.tdigest"));
  }

  @Provides
  @Singleton
  private TDigestRegistry providesRegistry(OptionalInjections opts, TDigestConfig config) {
    return new TDigestRegistry(opts.getRegistry(), config);
  }

  @Provides
  @Singleton
  private TDigestWriter providesWriter(OptionalInjections opts, TDigestConfig config) throws Exception {
    if (output != null) {
      return new FileTDigestWriter(opts.getRegistry(), config, output);
    } else {
      return new TDigestWriter(opts.getRegistry(), config) {
        @Override
        void write(ByteBuffer buffer) throws IOException {
        }
      };
    }
  }

  private static class OptionalInjections {
    @Inject(optional = true)
    private Registry registry;

    Registry getRegistry() {
      if (registry == null) {
        registry = new DefaultRegistry();
      }
      return registry;
    }
  }
}
