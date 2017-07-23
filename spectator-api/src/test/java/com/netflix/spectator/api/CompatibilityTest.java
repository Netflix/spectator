/*
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
package com.netflix.spectator.api;

import com.netflix.spectator.compat.Main;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls in a small test class that uses most of the public api and was compiled with a previous
 * version. It isn't automatically updated, but is simple and catches some of the more egregious
 * compatibility issues that were sometimes missed by the compatibility check task on the build.
 */
@RunWith(JUnit4.class)
public class CompatibilityTest {

  private static List<String> EXPECTED = new ArrayList<>();
  static {
    EXPECTED.add("Measurement(bucket-counter-age:bucket=062ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-age:bucket=125ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-age:bucket=250ms,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-counter-age:bucket=500ms,1234567890,250.0)");
    EXPECTED.add("Measurement(bucket-counter-age:bucket=old,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-counter-ageBiasOld:bucket=250ms,1234567890,251.0)");
    EXPECTED.add("Measurement(bucket-counter-ageBiasOld:bucket=375ms,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-counter-ageBiasOld:bucket=437ms,1234567890,62.0)");
    EXPECTED.add("Measurement(bucket-counter-ageBiasOld:bucket=500ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-ageBiasOld:bucket=old,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-counter-bytes:bucket=062_B,1234567890,1.0)");
    EXPECTED.add("Measurement(bucket-counter-bytes:bucket=large,1234567890,999.0)");
    EXPECTED.add("Measurement(bucket-counter-decimal:bucket=062,1234567890,1.0)");
    EXPECTED.add("Measurement(bucket-counter-decimal:bucket=large,1234567890,999.0)");
    EXPECTED.add("Measurement(bucket-counter-latency:bucket=062ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-latency:bucket=125ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-latency:bucket=250ms,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-counter-latency:bucket=500ms,1234567890,250.0)");
    EXPECTED.add("Measurement(bucket-counter-latency:bucket=slow,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-counter-latencyBiasSlow:bucket=062ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-latencyBiasSlow:bucket=125ms,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-counter-latencyBiasSlow:bucket=250ms,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-counter-latencyBiasSlow:bucket=500ms,1234567890,250.0)");
    EXPECTED.add("Measurement(bucket-counter-latencyBiasSlow:bucket=slow,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=250ms:statistic=count,1234567890,251.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=250ms:statistic=totalAmount,1234567890,3.1375E10)");
    EXPECTED.add("Measurement(bucket-dist:bucket=375ms:statistic=count,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=375ms:statistic=totalAmount,1234567890,3.9125E10)");
    EXPECTED.add("Measurement(bucket-dist:bucket=437ms:statistic=count,1234567890,62.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=437ms:statistic=totalAmount,1234567890,2.5203E10)");
    EXPECTED.add("Measurement(bucket-dist:bucket=500ms:statistic=count,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=500ms:statistic=totalAmount,1234567890,2.9547E10)");
    EXPECTED.add("Measurement(bucket-dist:bucket=slow:statistic=count,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-dist:bucket=slow:statistic=totalAmount,1234567890,3.7425E11)");
    EXPECTED.add("Measurement(bucket-timer:bucket=062ms:statistic=count,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-timer:bucket=062ms:statistic=totalTime,1234567890,1.953E9)");
    EXPECTED.add("Measurement(bucket-timer:bucket=125ms:statistic=count,1234567890,63.0)");
    EXPECTED.add("Measurement(bucket-timer:bucket=125ms:statistic=totalTime,1234567890,5.922E9)");
    EXPECTED.add("Measurement(bucket-timer:bucket=250ms:statistic=count,1234567890,125.0)");
    EXPECTED.add("Measurement(bucket-timer:bucket=250ms:statistic=totalTime,1234567890,2.35E10)");
    EXPECTED.add("Measurement(bucket-timer:bucket=500ms:statistic=count,1234567890,250.0)");
    EXPECTED.add("Measurement(bucket-timer:bucket=500ms:statistic=totalTime,1234567890,9.3875E10)");
    EXPECTED.add("Measurement(bucket-timer:bucket=old:statistic=count,1234567890,499.0)");
    EXPECTED.add("Measurement(bucket-timer:bucket=old:statistic=totalTime,1234567890,3.7425E11)");
    EXPECTED.add("Measurement(collection-size,1234567890,8.0)");
    EXPECTED.add("Measurement(counter,1234567890,127.0)");
    EXPECTED.add("Measurement(counter:a=b,1234567890,381.0)");
    EXPECTED.add("Measurement(dist:a=b:statistic=count,1234567890,15.0)");
    EXPECTED.add("Measurement(dist:a=b:statistic=totalAmount,1234567890,504.0)");
    EXPECTED.add("Measurement(dist:statistic=count,1234567890,5.0)");
    EXPECTED.add("Measurement(dist:statistic=totalAmount,1234567890,168.0)");
    EXPECTED.add("Measurement(gauge,1234567890,49.0)");
    EXPECTED.add("Measurement(gauge-age,1234567890,0.049)");
    EXPECTED.add("Measurement(gauge-function,1234567890,65.0)");
    EXPECTED.add("Measurement(gauge:app=foo:asg=foo-dev-v001:cluster=foo-dev:node=i-12345,1234567890,7.0)");
    EXPECTED.add("Measurement(long-timer:a=b:statistic=activeTasks,1234567890,3.0)");
    EXPECTED.add("Measurement(long-timer:a=b:statistic=duration,1234567890,15120.0)");
    EXPECTED.add("Measurement(long-timer:statistic=activeTasks,1234567890,1.0)");
    EXPECTED.add("Measurement(long-timer:statistic=duration,1234567890,10080.0)");
    EXPECTED.add("Measurement(map-size,1234567890,8.0)");
    EXPECTED.add("Measurement(method-value,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0000:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0056:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0059:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D005C:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D005F:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0060:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0061:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0062:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0063:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0064:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0065:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0066:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0067:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0068:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0069:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006A:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006B:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006C:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006D:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006E:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D006F:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0070:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0071:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0072:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0073:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0074:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0075:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0076:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0077:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0078:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0079:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007A:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007B:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007C:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007D:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007E:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D007F:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0080:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0081:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0082:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-dist:percentile=D0083:statistic=percentile,1234567890,15.0)");
    EXPECTED.add("Measurement(percentile-dist:statistic=count,1234567890,1000.0)");
    EXPECTED.add("Measurement(percentile-dist:statistic=totalAmount,1234567890,4.995E11)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0000:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0056:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0059:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T005C:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T005F:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0060:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0061:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0062:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0063:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0064:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0065:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0066:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0067:statistic=percentile,1234567890,2.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0068:statistic=percentile,1234567890,1.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0069:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006A:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006B:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006C:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006D:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006E:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T006F:statistic=percentile,1234567890,5.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0070:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0071:statistic=percentile,1234567890,6.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0072:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0073:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0074:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0075:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0076:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0077:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0078:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0079:statistic=percentile,1234567890,23.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007A:statistic=percentile,1234567890,22.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007B:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007C:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007D:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007E:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T007F:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0080:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0081:statistic=percentile,1234567890,89.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0082:statistic=percentile,1234567890,90.0)");
    EXPECTED.add("Measurement(percentile-timer:percentile=T0083:statistic=percentile,1234567890,15.0)");
    EXPECTED.add("Measurement(percentile-timer:statistic=count,1234567890,1000.0)");
    EXPECTED.add("Measurement(percentile-timer:statistic=totalTime,1234567890,4.995E11)");
    EXPECTED.add("Measurement(timer:a=b:statistic=count,1234567890,24.0)");
    EXPECTED.add("Measurement(timer:a=b:statistic=totalTime,1234567890,4.53852000126E14)");
    EXPECTED.add("Measurement(timer:statistic=count,1234567890,8.0)");
    EXPECTED.add("Measurement(timer:statistic=totalTime,1234567890,1.51284000042E14)");
  }

  @Test
  public void check() throws Exception {
    List<String> actual = new ArrayList<>(Main.run());

    //for (String s : actual) {
    //  System.out.println("    EXPECTED.add(\"" + s + "\");");
    //}

    int length = Math.max(EXPECTED.size(), actual.size());
    for (int i = 0; i < length; ++i) {
      String exp = (i < EXPECTED.size()) ? EXPECTED.get(i) : null;
      String found = (i < actual.size()) ? actual.get(i) : null;
      Assert.assertEquals(exp, found);
    }
  }
}
