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
package com.netflix.spectator.api;

import com.netflix.spectator.compat.Main;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Pulls in a small test class that uses most of the public api and was compiled with a previous
 * version. It isn't automatically updated, but is simple and catches some of the more egregious
 * compatibility issues that were sometimes missed by the compatibility check task on the build.
 */
@RunWith(JUnit4.class)
public class CompatibilityTest {

  private static Collection<String> EXPECTED = new ArrayList<>();
  static {
    EXPECTED.add("Measurement(collection-size,1234567890,8.0)");
    EXPECTED.add("Measurement(counter,1234567890,127.0)");
    EXPECTED.add("Measurement(counter:a=b,1234567890,381.0)");
    EXPECTED.add("Measurement(dist:statistic=count,1234567890,5.0)");
    EXPECTED.add("Measurement(dist:statistic=count:a=b,1234567890,15.0)");
    EXPECTED.add("Measurement(dist:statistic=totalAmount,1234567890,168.0)");
    EXPECTED.add("Measurement(dist:statistic=totalAmount:a=b,1234567890,504.0)");
    EXPECTED.add("Measurement(gauge,1234567890,49.0)");
    EXPECTED.add("Measurement(gauge-age,1234567890,0.049)");
    EXPECTED.add("Measurement(gauge-function,1234567890,35.0)");
    EXPECTED.add("Measurement(gauge:node=i-12345:asg=foo-dev-v001:cluster=foo-dev:app=foo,1234567890,7.0)");
    EXPECTED.add("Measurement(long-timer:statistic=activeTasks,1234567890,1.0)");
    EXPECTED.add("Measurement(long-timer:statistic=activeTasks:a=b,1234567890,3.0)");
    EXPECTED.add("Measurement(long-timer:statistic=duration,1234567890,10080.0)");
    EXPECTED.add("Measurement(long-timer:statistic=duration:a=b,1234567890,15120.0)");
    EXPECTED.add("Measurement(map-size,1234567890,8.0)");
    EXPECTED.add("Measurement(method-value,1234567890,22.0)");
    EXPECTED.add("Measurement(timer:statistic=count,1234567890,8.0)");
    EXPECTED.add("Measurement(timer:statistic=count:a=b,1234567890,24.0)");
    EXPECTED.add("Measurement(timer:statistic=totalTime,1234567890,1.51284000042E14)");
    EXPECTED.add("Measurement(timer:statistic=totalTime:a=b,1234567890,4.53852000126E14)");
  }

  @Test
  public void check() throws Exception {
    Assert.assertEquals(EXPECTED, Main.run());
  }

}
