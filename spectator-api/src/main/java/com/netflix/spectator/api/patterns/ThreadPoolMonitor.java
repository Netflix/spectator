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
  public static final String TASK_COUNT = "threadpool.taskCount";

  /**
   * Completed task count meter name.
   */
  public static final String COMPLETED_TASK_COUNT = "threadpool.completedTaskCount";

  /**
   * Current threads busy meter name.
   */
  public static final String CURRENT_THREADS_BUSY = "threadpool.currentThreadsBusy";

  /**
   * Max threads meter name.
   */
  public static final String MAX_THREADS = "threadpool.maxThreads";

  /**
   * Pool size meter name.
   */
  public static final String POOL_SIZE = "threadpool.poolSize";

  /**
   * Core pool size meter name.
   */
  public static final String CORE_POOL_SIZE = "threadpool.corePoolSize";

  /**
   * Queue size meter name.
   */
  public static final String QUEUE_SIZE = "threadpool.queueSize";

  // prevent direct instantiation.
  private ThreadPoolMonitor() { }

  /**
   * Register the provided thread pool, optionally tagged with a name.
   *
   * @param registry the registry to use
   * @param threadPool the thread pool to monitor
   * @param threadPoolName a name with which to tag the metrics or {@code null} for the default name
   */
  public static void monitor(
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
