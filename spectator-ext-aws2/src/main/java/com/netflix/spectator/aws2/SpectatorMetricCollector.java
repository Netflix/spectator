/*
 * Copyright 2014-2017 Netflix, Inc.
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

package com.netflix.spectator.aws2;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;
import software.amazon.awssdk.metrics.MetricCollector;
import software.amazon.awssdk.metrics.RequestMetricCollector;
import software.amazon.awssdk.metrics.ServiceMetricCollector;

/**
 * A MetricCollector that captures SDK metrics.
 */
public class SpectatorMetricCollector extends MetricCollector {
    private final RequestMetricCollector requestMetricCollector;

    /**
     * Constructs a new instance.
     */
    public SpectatorMetricCollector(Registry registry) {
        super();
        Preconditions.checkNotNull(registry, "registry");

        this.requestMetricCollector = new SpectatorRequestMetricCollector(registry);
    }

    @Override public boolean start() {
        return true;
    }

    @Override public void stop() {
    }

    @Override public boolean isEnabled() {
        return Boolean.valueOf(System.getProperty("spectator.ext.aws.enabled", "true"));
    }

    @Override public RequestMetricCollector getRequestMetricCollector() {
        return requestMetricCollector;
    }

    @Override public ServiceMetricCollector getServiceMetricCollector() {
        return ServiceMetricCollector.NONE;
    }
}
