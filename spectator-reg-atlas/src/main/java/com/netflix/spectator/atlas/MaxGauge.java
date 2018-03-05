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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Gauge;

/**
 * <p><b>Experimental:</b> This type may be removed in a future release.</p>
 *
 * Gauge that reports the maximum value submitted during an interval to Atlas. Main use-case
 * right now is for allowing the max stat used internally to AtlasDistributionSummary and
 * AtlasTimer to be transferred to a remote AtlasRegistry.
 */
public interface MaxGauge extends Gauge {
}
