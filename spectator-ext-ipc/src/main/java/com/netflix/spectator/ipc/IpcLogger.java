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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.api.patterns.CardinalityLimiters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Logger for recording IPC metrics and providing a basic access log. A single logger instance
 * should be reused for all requests in a given context because it maintains the state such as
 * number of inflight requests.
 */
public class IpcLogger {

  private static final Marker CLIENT = MarkerFactory.getMarker("ipc-client");
  private static final Marker SERVER = MarkerFactory.getMarker("ipc-server");

  private final Registry registry;
  private final Clock clock;
  private final Logger logger;

  private final ConcurrentHashMap<Id, AtomicInteger> inflightRequests;
  private final ConcurrentHashMap<String, Function<String, String>> limiters;

  private final LinkedBlockingQueue<IpcLogEntry> entries;

  /**
   * Create a new instance. Allows the clock to be explicitly set for unit tests.
   */
  IpcLogger(Registry registry, Clock clock, Logger logger) {
    this.registry = registry;
    this.clock = clock;
    this.logger = logger;
    this.inflightRequests = new ConcurrentHashMap<>();
    this.limiters = new ConcurrentHashMap<>();
    this.entries = new LinkedBlockingQueue<>(1000);
  }

  /** Create a new instance. */
  public IpcLogger(Registry registry, Logger logger) {
    this(registry, Clock.SYSTEM, logger);
  }

  /** Create a new instance. */
  public IpcLogger(Registry registry) {
    this(registry, Clock.SYSTEM, LoggerFactory.getLogger(IpcLogger.class));
  }

  /** Return the number of inflight requests associated with the given id. */
  AtomicInteger inflightRequests(Id id) {
    return Utils.computeIfAbsent(inflightRequests, id, i -> new AtomicInteger());
  }

  /**
   * Return the cardinality limiter for a given key. This is used to protect the metrics
   * backend from a metrics explosion if some dimensions have a high cardinality.
   */
  Function<String, String> limiterForKey(String key) {
    return Utils.computeIfAbsent(limiters, key, k -> CardinalityLimiters.mostFrequent(25));
  }

  private IpcLogEntry newEntry() {
    IpcLogEntry entry = entries.poll();
    return (entry == null) ? new IpcLogEntry(clock) : entry;
  }

  /**
   * Create a new log entry for client requests. Log entry objects may be reused to minimize
   * the number of allocations so they should only be modified in the context of a single
   * request.
   */
  public IpcLogEntry createClientEntry() {
    return newEntry()
        .withRegistry(registry)
        .withLogger(this)
        .withMarker(CLIENT);
  }

  /**
   * Create a new log entry for server requests. Log entry objects may be reused to minimize
   * the number of allocations so they should only be modified in the context of a single
   * request.
   */
  public IpcLogEntry createServerEntry() {
    return newEntry()
        .withRegistry(registry)
        .withLogger(this)
        .withMarker(SERVER);
  }

  /**
   * Called by the entry to log the request.
   */
  void log(IpcLogEntry entry) {
    Level level = entry.getLevel();
    Predicate<Marker> isEnabled;
    BiConsumer<Marker, String> log;
    switch (level) {
      case TRACE:
        isEnabled = logger::isTraceEnabled;
        log = logger::trace;
        break;
      case DEBUG:
        isEnabled = logger::isDebugEnabled;
        log = logger::debug;
        break;
      case INFO:
        isEnabled = logger::isInfoEnabled;
        log = logger::info;
        break;
      case WARN:
        isEnabled = logger::isWarnEnabled;
        log = logger::warn;
        break;
      case ERROR:
        isEnabled = logger::isErrorEnabled;
        log = logger::error;
        break;
      default:
        isEnabled = logger::isDebugEnabled;
        log = logger::debug;
        break;
    }

    if (isEnabled.test(entry.getMarker())) {
      log.accept(entry.getMarker(), entry.toString());
    }

    // For successful responses we can reuse the entry to avoid additional allocations. Failed
    // requests might have retries so we just reset the response portion to avoid incorrectly
    // having state bleed through from one request to the next.
    if (entry.isSuccessful()) {
      entry.reset();
      entries.offer(entry);
    } else {
      entry.resetForRetry();
    }
  }
}
