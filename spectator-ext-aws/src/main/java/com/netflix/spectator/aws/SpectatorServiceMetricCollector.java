/*
 * Copyright 2014-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.aws;

import com.amazonaws.metrics.ByteThroughputProvider;
import com.amazonaws.metrics.ServiceLatencyProvider;
import com.amazonaws.metrics.ServiceMetricCollector;
import com.amazonaws.util.AWSServiceMetrics;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;

import java.util.concurrent.TimeUnit;

/**
 * A {@link ServiceMetricCollector} that captures the time it takes to get a connection
 * from the pool.
 */
class SpectatorServiceMetricCollector extends ServiceMetricCollector {

  private final Timer clientGetConnectionTime;

  /** Create a new instance. */
  SpectatorServiceMetricCollector(Registry registry) {
    super();
    this.clientGetConnectionTime = registry.timer("aws.request.httpClientGetConnectionTime");
  }

  @Override
  public void collectByteThroughput(ByteThroughputProvider provider) {
  }

  @Override
  public void collectLatency(ServiceLatencyProvider provider) {
    if (provider.getServiceMetricType() == AWSServiceMetrics.HttpClientGetConnectionTime) {
      long nanos = (long) (provider.getDurationMilli() * 1e6);
      clientGetConnectionTime.record(nanos, TimeUnit.NANOSECONDS);
    }
  }
}
