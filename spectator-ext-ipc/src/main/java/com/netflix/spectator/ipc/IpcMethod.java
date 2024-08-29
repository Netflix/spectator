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

public enum IpcMethod implements Tag {

    /**
     * Represents a unary gRPC method.
     */
    unary,

    /**
     * Represents a client streaming gRPC method.
     */
    client_streaming,

    /**
     * Represents a server streaming gRPC method.
     */
    server_streaming,

    /**
     * Represents a bidirectional streaming gRPC method.
     */
    bidi_streaming,

    /**
     * Represents an HTTP GET request.
     */
    get,

    /**
     * Represents an HTTP POST request.
     */
    post,

    /**
     * Represents an HTTP PUT request.
     */
    put,

    /**
     * Represents an HTTP PATCH request.
     */
    patch,

    /**
     * Represents an HTTP DELETE request.
     */
    delete,

    /**
     * Represents an HTTP OPTIONS request.
     */
    options,

    /**
     * Represents a GraphQL query.
     */
    query,

    /**
     * Represents a GraphQL mutation.
     */
    mutation,

    /**
     * Represents a GraphQL subscription.
     */
    subscription;

    @Override public String key() {
        return IpcTagKey.method.key();
    }

    @Override public String value() {
        return name();
    }
}
