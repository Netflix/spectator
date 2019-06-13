# Thread Pools

Java's [ThreadPoolExecutor] exposes several properties that are useful to monitor to assess
the health, performance, and configuration of the pool.

## Getting Started

To report thread pool metrics, one can attach a [ThreadPoolMonitor] in the following manner:

```java
import com.netflix.spectator.api.patterns.ThreadPoolMonitor;

ThreadPoolMonitor.attach(registry, myThreadPoolExecutor, "my-thread-pool");
```

The thread pool's properties will be polled regularly in the background and will report metrics to the provided
registry. The third parameter will be added to each metric as an `id` dimension, if provided. However, if the value is
`null` or an empty string, then a default will be used as the `id`.

## Metrics

### threadpool.taskCount

Counter of the total number of tasks that have been scheduled.

**Unit:** tasks/second

**Data Source:** `ThreadPoolExecutor#getTaskCount()`

### threadpool.completedTaskCount

Counter of the total number of tasks that have completed.

**Unit:** tasks/second

**Data Source:** `ThreadPoolExecutor#getCompletedTaskCount()`

### threadpool.currentThreadsBusy

Gauge showing the current number of threads actively doing work.

**Unit:** count

**Data Source:** `ThreadPoolExecutor#getActiveCount()`

### threadpool.maxThreads

Gauge showing the current maximum number of threads configured for the pool.

**Unit:** count

**Data Source:** `ThreadPoolExecutor#getMaximumPoolSize()`

### threadpool.poolSize

Gauge showing the current size of the pool.

**Unit:** count

**Data Source:** `ThreadPoolExecutor#getPoolSize()`

### threadpool.corePoolSize

Gauge showing the current maximum number of core threads configured for the pool.

**Unit:** count

**Data Source:** `ThreadPoolExecutor#getCorePoolSize()`

### threadpool.queueSize

Gauge showing the current number of threads queued for execution.

**Unit:** count

**Data Source:** `ThreadPoolExecutor#getQueue().size()`

[ThreadPoolExecutor]: http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html
[ThreadPoolMonitor]: http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/patterns/ThreadPoolMonitor.html
