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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import com.tdunning.math.stats.TDigest;
import com.tdunning.math.stats.TreeDigest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RunWith(JUnit4.class)
public class StepDigestTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final Id id = registry.createId("foo");

  @Before
  public void init() {
    clock.setWallTime(0L);
  }

  @Test
  public void none() throws Exception {
    StepDigest digest = new StepDigest(registry, id, 100.0, 10);
    clock.setWallTime(10);
    Assert.assertEquals(Double.NaN, digest.poll().quantile(0.5), 0.2);
  }

  @Test
  public void addOne() throws Exception {
    StepDigest digest = new StepDigest(registry, id, 100.0, 10);
    digest.add(1.0);
    clock.setWallTime(10);
    Assert.assertEquals(1.0, digest.poll().quantile(0.5), 0.2);
  }

  @Test
  public void addTwoValues() throws Exception {
    StepDigest digest = new StepDigest(registry, id, 100.0, 10);
    digest.add(1.0);
    digest.add(100.0);
    clock.setWallTime(10);
    Assert.assertEquals(50.5, digest.poll().quantile(0.5), 0.2);
  }

  @Test
  public void merge() throws Exception {
    Random r = new Random();
    double[] values = new double[1000];
    for (int i = 0; i < 1000; ++i) {
      values[i] = r.nextDouble();
    }

    TDigest d1 = TreeDigest.createDigest(10.0);
    for (int i = 0; i < 1000; ++i) {
      d1.add(values[i]);
    }
    double v = d1.quantile(0.99);
    Assert.assertEquals(v, d1.quantile(0.99), 1e-12);

    TDigest d2 = TreeDigest.createDigest(10.0);
    TDigest d3 = TreeDigest.createDigest(10.0);
    for (int i = 0; i < 1000; ++i) {
      if (i % 2 == 0)
        d2.add(values[i]);
      else
        d3.add(values[i]);
    }
    List<TDigest> vs = new ArrayList<>();
    vs.add(d2);
    vs.add(d3);

    TDigest m1 = TreeDigest.merge(10.0, vs, r);
    double v2 = m1.quantile(0.99);
    Assert.assertEquals(v2, m1.quantile(0.99), 1e-12);

    for (int i = 0; i < 10; ++i) {
      Assert.assertEquals(v2, TreeDigest.merge(10.0, vs, r).quantile(0.99), v2 / 100);
    }

    Assert.assertEquals(v, v2, Math.abs(v) / 10);
  }
}
