/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;

import java.util.concurrent.TimeUnit;

/**
 * A ServiceMetricCollector that captures service metrics in a Registry.
 */
public class SpectatorServiceMetricCollector extends ServiceMetricCollector {
    private static final long NANOS_PER_MILLI = TimeUnit.MILLISECONDS.toNanos(1);
    private final Registry registry;

    /**
     * Constructs a new SpectatorServiceMetricCollector.
     *
     * @param registry the Registry for this SpectatorServiceMetricCollector.
     */
    public SpectatorServiceMetricCollector(Registry registry) {
        super();
        this.registry = Preconditions.checkNotNull(registry, "registry");
    }

    @Override
    public void collectByteThroughput(ByteThroughputProvider provider) {
        if (provider.getByteCount() > 0) {
            registry.counter(getThroughputBytesId(provider)).increment(provider.getByteCount());
            registry.timer(getThroughputDurationId(provider)).record(provider.getDurationNano(), TimeUnit.NANOSECONDS);
        }

    }

    @Override
    public void collectLatency(ServiceLatencyProvider provider) {
        final long durationNanos = toNanos(provider.getDurationMilli());
        registry.timer(getServiceLatencyId(provider)).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Id getServiceLatencyId(ServiceLatencyProvider provider) {
        return registry.createId(getServiceLatencyMetric(), getServiceLatencyTags(provider));
    }

    private String getServiceLatencyMetric() {
        return "AWS_Latency";
    }

    private String[] getServiceLatencyTags(ServiceLatencyProvider provider) {
        return new String[] {
                "serviceName", provider.getServiceMetricType().getServiceName(),
                "serviceMetricType", provider.getServiceMetricType().toString()
        };
    }

    private Id getThroughputBytesId(ByteThroughputProvider provider) {
        return registry.createId(getThroughputBytesMetric(), getThroughputTags(provider));
    }

    private Id getThroughputDurationId(ByteThroughputProvider provider) {
        return registry.createId(getThroughputDurationMetric(), getThroughputTags(provider));
    }

    private String getThroughputBytesMetric() {
        return "AWS_Throughput_Bytes";
    }

    private String getThroughputDurationMetric() {
        return "AWS_Throughput_Duration";
    }

    private String[] getThroughputTags(ByteThroughputProvider provider) {
        return new String[] {
                "serviceName", provider.getThroughputMetricType().getServiceName(),
                "byteCountMetricType", provider.getThroughputMetricType().getByteCountMetricType().toString(),
        };
    }

    private long toNanos(double milliseconds) {
        return (long) (milliseconds * NANOS_PER_MILLI);
    }
}
