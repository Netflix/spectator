/**
 * Copyright 2024 Netflix, Inc.
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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Tag;

public enum IpcSource implements Tag {

    /**
     * No call was made due to errors potentially.
     */
    none,

    /**
     * Data sourced directly from EVCache as the cache implementation (when the exact cache is known).
     */
    evcache,

    /**
     * Data sourced from a cache where the cache implementation is not directly known or abstracted.
     */
    cache,

    /**
     * Static fallback was used to fetch the data.
     */
    fallback,

    /**
     * Response fetched using mesh.
     */
    mesh,

    /**
     * Response fetched directly from the downstream service (or if not known to be mesh).
     */
    direct,

    /**
     * Data sourced from a validation handler that may short-circuit the response immediately for failed validation.
     */
    validation,

    /**
     * Data sourced and returned directly by a filter or interceptor.
     */
    filter,

    /**
     * Data fetched from an in-memory cache.
     */
    memory,

    /**
     * Data sourced from a user defined business logic handler or root data fetcher.
     */
    application;

    @Override
    public String key() {
        return IpcTagKey.source.key();
    }

    @Override
    public String value() {
        return name();
    }
}
