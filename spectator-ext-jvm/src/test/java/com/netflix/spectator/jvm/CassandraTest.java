/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RunWith(JUnit4.class)
public class CassandraTest {

  private final Config config = ConfigFactory.load("cassandra");

  private List<JmxConfig> configs() {
    List<JmxConfig> cfgs = new ArrayList<>();
    for (Config cfg : config.getConfigList("netflix.spectator.agent.jmx.mappings")) {
      cfgs.add(JmxConfig.from(cfg));
    }
    return cfgs;
  }

  private List<Measurement> measure(Registry registry, List<JmxConfig> configs, JmxData data) {
    List<Measurement> ms = new ArrayList<>();
    for (JmxConfig cfg : configs) {
      if (cfg.getQuery().apply(data.getName())) {
        for (JmxMeasurementConfig c : cfg.getMeasurements()) {
          c.measure(registry, data, ms);
        }
      }
    }
    return ms;
  }

  private JmxData timer(String props, int i) throws Exception {
    ObjectName name = new ObjectName("org.apache.cassandra.metrics:" + props);

    Map<String, String> stringAttrs = new HashMap<>(name.getKeyPropertyList());
    stringAttrs.put("EventType",   "calls");
    stringAttrs.put("LatencyUnit", "MICROSECONDS");
    stringAttrs.put("RateUnit",    "SECONDS");

    Map<String, Number> numAttrs = new HashMap<>();
    numAttrs.put("OneMinuteRate",      100.0 + i);
    numAttrs.put("FiveMinuteRate",     500.0 + i);
    numAttrs.put("FifteenMinuteRate", 1500.0 + i);
    numAttrs.put("MeanRate",           987.0 + i);

    numAttrs.put("Count",               1000 + i);

    numAttrs.put("Min",                   10 + i);
    numAttrs.put("Max",                 9000 + i);
    numAttrs.put("Mean",                1000 + i);
    numAttrs.put("StdDev",                10 + i);
    numAttrs.put("50thPercentile",    5000.0 + i);
    numAttrs.put("75thPercentile",    7500.0 + i);
    numAttrs.put("95thPercentile",    9500.0 + i);
    numAttrs.put("99thPercentile",    9900.0 + i);
    numAttrs.put("999thPercentile",   9990.0 + i);

    return new JmxData(name, stringAttrs, numAttrs);
  }

  @Test
  public void readLatency() throws Exception {
    Registry r = new DefaultRegistry(new ManualClock());
    List<JmxConfig> configs = configs();

    JmxData data = timer("keyspace=test,name=ReadLatency,scope=foo,type=ColumnFamily", 0);
    List<Measurement> ms = measure(r, configs, data);
    Assert.assertEquals(7, ms.size());
    Assert.assertEquals(
        50.0e-4,
        Utils.first(ms, "statistic", "percentile_50").value(),
        1e-12);

    data = timer("keyspace=test,name=ReadLatency,scope=foo,type=ColumnFamily", 1);
    ms = measure(r, configs, data);
    Assert.assertEquals(7, ms.size());
    Assert.assertEquals(
        50.01e-4,
        Utils.first(ms, "statistic", "percentile_50").value(),
        1e-12);
  }

  // Compensate for: https://github.com/dropwizard/metrics/issues/1030
  @Test
  public void readLatencyNoActivity() throws Exception {
    Registry r = new DefaultRegistry(new ManualClock());
    List<JmxConfig> configs = configs();

    JmxData data = timer("keyspace=test,name=ReadLatency,scope=foo,type=ColumnFamily", 0);
    List<Measurement> ms = measure(r, configs, data);
    Assert.assertEquals(7, ms.size());
    Assert.assertEquals(
        50.0e-4,
        Utils.first(ms, "statistic", "percentile_50").value(),
        1e-12);

    data = timer("keyspace=test,name=ReadLatency,scope=foo,type=ColumnFamily", 0);
    ms = measure(r, configs, data);
    Assert.assertEquals(7, ms.size());
    Assert.assertEquals(
        0.0,
        Utils.first(ms, "statistic", "percentile_50").value(),
        1e-12);

    data = timer("keyspace=test,name=ReadLatency,scope=foo,type=ColumnFamily", 1);
    ms = measure(r, configs, data);
    Assert.assertEquals(7, ms.size());
    Assert.assertEquals(
        50.01e-4,
        Utils.first(ms, "statistic", "percentile_50").value(),
        1e-12);
  }

}
