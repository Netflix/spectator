/**
 * Copyright 2014-2016 Netflix, Inc.
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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class TDigestRegistryTest {

  private final ManualClock clock = new ManualClock();
  private final DefaultRegistry registry = new DefaultRegistry(clock);
  private final TDigestConfig config = new TDigestConfig(
      ConfigFactory.parseString("polling-frequency = 60s"));

  @Test
  public void gaugesAreRegisteredWithUnderlying() throws Exception {
    TDigestRegistry tdr = new TDigestRegistry(registry, config);
    Id id = tdr.createId("test");
    AtomicInteger value = new AtomicInteger(0);
    tdr.gauge(id, value);
    Assert.assertNotNull(registry.get(id));
  }
}
