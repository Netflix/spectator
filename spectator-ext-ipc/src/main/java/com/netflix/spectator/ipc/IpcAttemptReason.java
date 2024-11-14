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

public enum IpcAttemptReason implements Tag {

    /**
     * Represents the initial attempt for the request.
     */
    initial,

    /**
     * Represents a retry attempt for the request.
     */
    retry,

    /**
     * Represents a hedge attempt for the request.
     */
    hedge;

    @Override public String key() {
        return IpcTagKey.attemptFinal.key();
    }

    @Override public String value() {
        return name();
    }
}
