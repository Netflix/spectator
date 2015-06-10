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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class TDigestPluginTest {

  private final ManualClock clock = new ManualClock();

  @Before
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  private Map<Long, List<TDigestMeasurement>> readFromFile(File f) throws IOException {
    Map<Long, List<TDigestMeasurement>> result = new HashMap<>();
    try (TDigestReader in = new FileTDigestReader(f)) {
      List<TDigestMeasurement> ms;
      while (!(ms = in.read()).isEmpty()) {
        for (TDigestMeasurement m : ms) {
          List<TDigestMeasurement> tmp = result.get(m.timestamp());
          if (tmp == null) {
            tmp = new ArrayList<>();
            result.put(m.timestamp(), tmp);
          }
          tmp.add(m);
        }
      }
    }
    return result;
  }

  @Test
  public void writeData() throws Exception {
    final File f = new File("build/TDigestPlugin_writeData.out");
    f.getParentFile().mkdirs();
    final TDigestConfig config = new TDigestConfig() {
      @Override public String endpoint() {
        return "";
      }

      @Override public String stream() {
        return "";
      }

      @Override public long pollingFrequency() {
        return 60L;
      }
    };
    final TDigestRegistry r = new TDigestRegistry(new DefaultRegistry(clock), config);
    final TDigestPlugin p = new TDigestPlugin(r, new FileTDigestWriter(f), config);

    // Adding a bunch of tags to test the effect of setting
    // SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES.
    //
    //            |   true |  false | savings |
    // recorded   | 151150 | 157225 |    3.9% |
    // empty      |   5030 |  11465 |   56.1% |
    //
    // With many recorded values the size of the digest is likely much bigger than the tags
    // and the overall benefit is small. However, with few recorded values it leads to a big
    // reduction.
    Id one = r.createId("one");
    Id many = r.createId("many")
        .withTag("region", "us-east-1")
        .withTag("zone", "us-east-1a")
        .withTag("ami", "ami-12345")
        .withTag("node", "i-123456789")
        .withTag("app", "foo")
        .withTag("cluster", "foo-bar")
        .withTag("asg",     "foo-bar-v001");
    for (int i = 0; i < 10000; ++i) {
      r.timer(one).record(i, TimeUnit.MILLISECONDS);
      r.timer(many.withTag("i", "" + (i / 100))).record(i, TimeUnit.MILLISECONDS);
    }

    clock.setWallTime(61000);
    p.writeData();

    clock.setWallTime(121000);
    p.writeData();

    p.shutdown();

    Map<Long, List<TDigestMeasurement>> result = readFromFile(f);
    Assert.assertEquals(2, result.size());
    Assert.assertNotNull(result.get(60000L));
    Assert.assertNotNull(result.get(120000L));
    for (List<TDigestMeasurement> m : result.values()) {
      checkRecord(r, m);
    }
  }

  private void checkRecord(Registry r, List<TDigestMeasurement> ms) {
    Random random = new Random(42);
    TDigest one = null;
    TDigest many = TDigest.createDigest(100.0);
    for (TDigestMeasurement m : ms) {
      if ("one".equals(m.id().name())) {
        one = m.value();
      } else {
        List<TDigest> vs = new ArrayList<>(2);
        vs.add(many);
        vs.add(m.value());
        many = TreeDigest.merge(100.0, vs, random);
      }
    }
    for (int i = 0; i < 1000; ++i) {
      double q = i / 1000.0;
      double v1 = one.quantile(q);
      double v2 = many.quantile(q);
      //System.err.printf("%5d: %f == %f, delta=%f%n", i, v1, v2, Math.abs(v1 - v2));

      // There seems to be some variation in the error. On my machine I can typically run with 0.1
      // and almost always with 0.2. However on travis/cloudbees we seem to be getting some sporadic
      // failures. Setting to 0.5 to hopefull be high enough to avoid false alarms until we have
      // a better solution.
      Assert.assertEquals(v1, v2, 0.5);
    }
  }
}
