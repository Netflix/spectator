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

import com.netflix.spectator.api.DefaultId;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;

@RunWith(JUnit4.class)
public class StepDigestTest {

  private final ManualClock clock = new ManualClock();

  @Before
  public void init() {
    clock.setWallTime(0L);
  }

  @Test
  public void none() throws Exception {
    Id id = new DefaultId("foo");
    StepDigest digest = new StepDigest(id, 100.0, clock, 10);
    clock.setWallTime(10);
    Assert.assertEquals(Double.NaN, digest.poll().quantile(0.5), 0.2);
  }

  @Test
  public void addOne() throws Exception {
    Id id = new DefaultId("foo");
    StepDigest digest = new StepDigest(id, 100.0, clock, 10);
    digest.add(1.0);
    clock.setWallTime(10);
    Assert.assertEquals(1.0, digest.poll().quantile(0.5), 0.2);
  }

  @Test
  public void addTwoValues() throws Exception {
    Id id = new DefaultId("foo");
    StepDigest digest = new StepDigest(id, 100.0, clock, 10);
    digest.add(1.0);
    digest.add(100.0);
    clock.setWallTime(10);
    Assert.assertEquals(50.5, digest.poll().quantile(0.5), 0.2);
  }
}
