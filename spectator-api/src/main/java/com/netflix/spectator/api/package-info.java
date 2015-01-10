/**
 * Copyright 2015 Netflix, Inc.
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
/**
 * Primary interfaces for working with spectator. To get started, here is a small code sample:
 *
 * <pre>
 * Server s = new Server(Spectator.registry());
 *
 * class Server {
 *   private final ExtendedRegistry registry;
 *   private final Id requestCountId;
 *   private final Timer requestLatency;
 *   private final DistributionSummary responseSizes;
 *
 *   public Server(ExtendedRegistry registry) {
 *     this.registry = registry;
 *     requestCountId = registry.createId("server.requestCount");
 *     requestLatency = registry.timer("server.requestLatency");
 *     responseSizes = registry.distributionSummary("server.responseSizes");
 *     registry.methodValue("server.numConnections", this, "getNumConnections");
 *   }
 *
 *   public Response handle(Request req) {
 *     final long s = System.nanoTime();
 *     try {
 *       Response res = doSomething(req);
 *
 *       final Id cntId = requestCountId
 *         .withTag("country", req.country())
 *         .withTag("status", res.status());
 *       registry.counter(cntId).increment();
 *
 *       responseSizes.record(res.body().size());
 *
 *       return res;
 *     } catch (Exception e) {
 *       final Id cntId = requestCountId
 *         .withTag("country", req.country())
 *         .withTag("status", "exception")
 *         .withTag("error", e.getClass().getSimpleName());
 *       registry.counter(cntId).increment();
 *       throw e;
 *     } finally {
 *       requestLatency.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
 *     }
 *   }
 *
 *   public int getNumConnections() {
 *     // however we determine the current number of connections on the server
 *   }
 * }
 * </pre>
 *
 * The main classes you will need to understand:
 *
 * <ul>
 *   <li>{@link com.netflix.spectator.api.Spectator}: static entrypoint to access the registry.</li>
 *   <li>{@link com.netflix.spectator.api.ExtendedRegistry}: registry class used to create
 *       meters.</li>
 *   <li>{@link com.netflix.spectator.api.Counter}: meter type for measuring a rate of change.</li>
 *   <li>{@link com.netflix.spectator.api.Timer}: meter type for measuring the time for many short
 *       events.</li>
 *   <li>{@link com.netflix.spectator.api.LongTaskTimer}: meter type for measuring the time for a
 *       few long events.</li>
 *   <li>{@link com.netflix.spectator.api.DistributionSummary}: meter type for measuring the sample
 *       distribution of some type of events.</li>
 * </ul>
 */
package com.netflix.spectator.api;
