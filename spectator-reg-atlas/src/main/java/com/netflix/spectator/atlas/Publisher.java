/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.netflix.spectator.atlas.impl.EvalPayload;
import com.netflix.spectator.atlas.impl.PublishPayload;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * <strong>Alpha:</strong> this method is experimental and may change or be completely
 * removed with no notice.
 *
 * Publisher for submitting data to Atlas.
 */
public interface Publisher extends Closeable {

  /** Initialize the publisher and get it ready to send data. */
  void init();

  /** Send a payload to an Atlas backend. */
  CompletableFuture<Void> publish(PublishPayload payload);

  /** Send a evaluation payload to an Atlas LWC service. */
  CompletableFuture<Void> publish(EvalPayload payload);
}
