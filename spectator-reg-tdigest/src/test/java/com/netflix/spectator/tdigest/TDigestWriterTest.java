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
import com.tdunning.math.stats.TDigest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class TDigestWriterTest {

  private final ManualClock clock = new ManualClock();

  private ByteArrayOutputStream baos;
  private TDigestWriter writer;

  private Id bigId() {
    Id id = new DefaultId("foo");
    for (int i = 0; i < 10000; ++i) {
      id = id.withTag("" + i, "" + i);
    }
    return id;
  }

  @Before
  public void init() {
    baos = new ByteArrayOutputStream();
    writer = new StreamTDigestWriter(baos);
  }

  @Test
  public void emptyDigest() throws Exception {
    Id id = new DefaultId("foo");
    TDigestMeasurement m = new TDigestMeasurement(id, 0L, TDigest.createDigest(100.0));
    writer.write(Collections.singletonList(m));
    writer.close();

    ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
    List<List<TDigestMeasurement>> data = StreamTDigestReader.readAll(in);
    Assert.assertEquals(1, data.size());
    Assert.assertEquals(1, data.get(0).size());
    Assert.assertEquals(Double.NaN, data.get(0).get(0).value().quantile(0.5), 0.2);
  }

  @Test
  public void simpleDigest() throws Exception {
    Id id = new DefaultId("foo");
    TDigest d = TDigest.createDigest(100.0);
    d.add(1.0);
    TDigestMeasurement m = new TDigestMeasurement(id, 0L, d);
    writer.write(Collections.singletonList(m));
    writer.close();

    ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
    List<List<TDigestMeasurement>> data = StreamTDigestReader.readAll(in);
    Assert.assertEquals(1, data.size());
    Assert.assertEquals(1, data.get(0).size());
    Assert.assertEquals(1.0, data.get(0).get(0).value().quantile(0.5), 0.2);
  }

  @Test
  public void overflow() throws Exception {
    Id id = new DefaultId("foo");
    TDigestMeasurement m = new TDigestMeasurement(id, 0L, TDigest.createDigest(100.0));
    List<TDigestMeasurement> ms = new ArrayList<>();
    for (int i = 0; i < 50000; ++i) {
      ms.add(m);
    }
    writer.write(ms);
    writer.close();

    ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
    List<List<TDigestMeasurement>> data = StreamTDigestReader.readAll(in);
    int count = 0;
    for (List<TDigestMeasurement> vs : data) {
      for (TDigestMeasurement v : vs) {
        ++count;
        Assert.assertEquals(Double.NaN, data.get(0).get(0).value().quantile(0.5), 0.2);
      }
    }
    Assert.assertEquals(50000, count);
  }

  @Test
  public void singleMeasurementOverflow() throws Exception {
    TDigestMeasurement m = new TDigestMeasurement(bigId(), 0L, TDigest.createDigest(100.0));
    writer.write(Collections.singletonList(m));
    writer.close();

    ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
    List<List<TDigestMeasurement>> data = StreamTDigestReader.readAll(in);
    Assert.assertEquals(0, data.size());
  }
}
