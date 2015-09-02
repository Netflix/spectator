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

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.TimingInfo;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.Preconditions;

import java.util.concurrent.TimeUnit;

/**
 * A RequestMetricCollector that captures request level metrics in a Registry.
 */
public class SpectatorRequestMetricCollector extends RequestMetricCollector {
    private final Registry registry;

    /**
     * Constructs a new SpectatorRequestMetricCollector.
     *
     * @param registry the Registry for this SpectatorRequestMetricCollector.
     */
    public SpectatorRequestMetricCollector(Registry registry) {
        super();
        this.registry = Preconditions.checkNotNull(registry, "registry");
    }

    @Override
    public void collectMetrics(Request<?> request, Response<?> response) {
        final AWSRequestMetrics metrics = request.getAWSRequestMetrics();
        if (metrics.isEnabled()) {
            final String[] tags = getTags(request, response);
            metrics.getTimingInfo().getAllCounters().forEach((name, count) -> {
                registry.gauge(registry.createId(counterName(name), tags), count);
            });
            metrics.getTimingInfo().getSubMeasurementsByName().forEach((name, measurements) -> {
                final Timer timer = registry.timer(timerName(name), tags);
                measurements
                        .stream()
                        .filter(TimingInfo::isEndTimeKnown)
                        .map(t -> t.getEndTimeNano() - t.getStartTimeNano())
                        .forEach(t -> timer.record(t, TimeUnit.NANOSECONDS));
            });
        }
    }

    private String counterName(String name) {
        return "AWS_" + name;
    }

    private String timerName(String name) {
        return "AWS_" + name;
    }

    private String[] getTags(Request<?> request, Response<?> response) {
        return new String[] {
                "endpoint", request.getEndpoint().toASCIIString(),
                "serviceName", request.getServiceName(),
                "requestType", request.getOriginalRequest().getClass().getSimpleName(),
                "statusCode", Integer.toString(response.getHttpResponse().getStatusCode())
        };
    }
}
