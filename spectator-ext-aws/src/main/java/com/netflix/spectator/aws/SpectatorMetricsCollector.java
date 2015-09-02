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

import com.amazonaws.metrics.MetricCollector;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.metrics.ServiceMetricCollector;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.impl.Preconditions;

/**
 * A MetricCollector that captures SDK metrics in a Registry.
 *
 */
public class SpectatorMetricsCollector extends MetricCollector {
    private final RequestMetricCollector requestMetricCollector;
    private final ServiceMetricCollector serviceMetricCollector;

    /**
     * Constructs a new SpectatorMetricsCollector.
     *
     * @param registry the Registry for this SpectatorMetricsCollector
     */
    public SpectatorMetricsCollector(Registry registry) {
        super();
        Preconditions.checkNotNull(registry, "registry");

        this.requestMetricCollector = new SpectatorRequestMetricCollector(registry);
        this.serviceMetricCollector = new SpectatorServiceMetricCollector(registry);
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Spectator.config().getBoolean("spectator.ext.aws.enabled", true);
    }

    @Override
    public RequestMetricCollector getRequestMetricCollector() {
        return requestMetricCollector;
    }

    @Override
    public ServiceMetricCollector getServiceMetricCollector() {
        return serviceMetricCollector;
    }
}
