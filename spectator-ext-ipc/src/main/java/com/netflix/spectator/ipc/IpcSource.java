package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Tag;

public enum IpcSource implements Tag {
    /**
     * No call was made due to errors potentially.
     */
    none,

    /**
     * Data source directly from EVCache as the cache implementation (when the exact cache is known).
     */
    evcache,

    /**
     * Data sourced from a cache where the cache implementation is not directly known or abstracted.
     */
    cache,

    /**
     * Static fallback used to fetch the data.
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
    memory;

    @Override
    public String key() {
        return IpcTagKey.source.key();
    }

    @Override
    public String value() {
        return name();
    }
}
