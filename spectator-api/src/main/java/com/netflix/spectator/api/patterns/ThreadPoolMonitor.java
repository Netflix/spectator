/*
 * Copyright 2014-2017 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.Preconditions;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Monitors and reports a common set of metrics for thread pools.
 *
 * <p>The following is the list of metrics reported followed by which {@link ThreadPoolExecutor}
 * method populates it.</p>
 *
 * <pre>
 *  +-----------------------------------------------------------------------------+
 *  | Metric Name                  | Data Source                                  |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.taskCount          | {@link ThreadPoolExecutor#getTaskCount()}            |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.completedTaskCount | {@link ThreadPoolExecutor#getCompletedTaskCount()}   |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.currentThreadsBusy | {@link ThreadPoolExecutor#getActiveCount()}          |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.maxThreads         | {@link ThreadPoolExecutor#getMaximumPoolSize()}      |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.poolSize           | {@link ThreadPoolExecutor#getPoolSize()}             |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.corePoolSize       | {@link ThreadPoolExecutor#getCorePoolSize()}         |
 *  +------------------------------+----------------------------------------------+
 *  |threadpool.queueSize          | {@link ThreadPoolExecutor#getQueue()}.size()         |
 *  +-----------------------------------------------------------------------------+
 * </pre>
 */
public final class ThreadPoolMonitor {

  /**
   * The default ID tag name.
   */
  // visible for testing
  static final String ID_TAG_NAME = "id";

  /**
   * The default ID value.
   */
  private static final String DEFAULT_ID = "default";

  /**
   * Task count meter name.
   */
  static final String TASK_COUNT = "threadpool.taskCount";

  /**
   * Completed task count meter name.
   */
  static final String COMPLETED_TASK_COUNT = "threadpool.completedTaskCount";

  /**
   * Current threads busy meter name.
   */
  static final String CURRENT_THREADS_BUSY = "threadpool.currentThreadsBusy";

  /**
   * Max threads meter name.
   */
  static final String MAX_THREADS = "threadpool.maxThreads";

  /**
   * Pool size meter name.
   */
  static final String POOL_SIZE = "threadpool.poolSize";

  /**
   * Core pool size meter name.
   */
  static final String CORE_POOL_SIZE = "threadpool.corePoolSize";

  /**
   * Queue size meter name.
   */
  static final String QUEUE_SIZE = "threadpool.queueSize";

  // prevent direct instantiation.
  private ThreadPoolMonitor() { }

  /**
   * Register the provided thread pool, optionally tagged with a name.
   *
   * @param registry the registry to use
   * @param threadPool the thread pool on which to attach monitoring
   * @param threadPoolName a name with which to tag the metrics or {@code null} for the default name
   */
  public static void attach(
      final Registry registry,
      final ThreadPoolExecutor threadPool,
      final String threadPoolName) {

    Preconditions.checkNotNull(registry, "registry");
    Preconditions.checkNotNull(threadPool, "threadPool");

    final Tag idTag = new BasicTag(ID_TAG_NAME, threadPoolName != null ? threadPoolName : DEFAULT_ID);

    PolledMeter.using(registry)
        .withName(TASK_COUNT)
        .withTag(idTag)
        .monitorMonotonicCounter(threadPool, ThreadPoolExecutor::getTaskCount);
    PolledMeter.using(registry)
        .withName(COMPLETED_TASK_COUNT)
        .withTag(idTag)
        .monitorMonotonicCounter(threadPool, ThreadPoolExecutor::getCompletedTaskCount);
    PolledMeter.using(registry)
        .withName(CURRENT_THREADS_BUSY)
        .withTag(idTag)
        .monitorValue(threadPool, ThreadPoolExecutor::getActiveCount);
    PolledMeter.using(registry)
        .withName(MAX_THREADS)
        .withTag(idTag)
        .monitorValue(threadPool, ThreadPoolExecutor::getMaximumPoolSize);
    PolledMeter.using(registry)
        .withName(POOL_SIZE)
        .withTag(idTag)
        .monitorValue(threadPool, ThreadPoolExecutor::getPoolSize);
    PolledMeter.using(registry)
        .withName(CORE_POOL_SIZE)
        .withTag(idTag)
        .monitorValue(threadPool, ThreadPoolExecutor::getCorePoolSize);
    PolledMeter.using(registry)
        .withName(QUEUE_SIZE)
        .withTag(idTag)
        .monitorValue(threadPool, tp -> tp.getQueue().size());
  }
}
