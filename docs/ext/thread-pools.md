# Thread Pools

Java's [ThreadPoolExecutor](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
exposes several properties that are useful to monitor to assess the health, performance, and configuration of the pool.

## Getting Started

To report thread pool metrics, one can attach a
[ThreadPoolMonitor](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/patterns/ThreadPoolMonitor.html)
in the following manner:

```java
import com.netflix.spectator.api.patterns.ThreadPoolMonitor;
import com.netflix.spectator.api.Spectator;

ThreadPoolMonitor.attach(Spectator.registry(), myThreadPoolExecutor, "my-thread-pool");

```

The provided thread pool will be registered using the provided registry and polled. The metrics will be additionally
tagged with a key of `id` and the value of the third parameter, if provided, or a default if not.

## Metrics

### threadpool.taskCount

Counter of the total number of tasks that have been scheduled.

**Data Source:** `ThreadPoolExecutor#getTaskCount()`

### threadpool.completedTaskCount

Counter of the total number of tasks that have completed.

**Data Source:** `ThreadPoolExecutor#getCompletedTaskCount()`

### threadpool.currentThreadsBusy

Gauge showing the current number of threads acvtively doing work.

**Data Source:** `ThreadPoolExecutor#getActiveCount()`

### threadpool.maxThreads

Gauge showing the current maximum number of threads configured for the pool.

**Data Source:** `ThreadPoolExecutor#getMaximumPoolSize()`

### threadpool.poolSize

Gauge showing the current size of the pool.

**Data Source:** `ThreadPoolExecutor#getPoolSize()`

### threadpool.corePoolSize

Gauge showing the current maximum number of core threads configured for the pool.

**Data Source:** `ThreadPoolExecutor#getCorePoolSize()`

### threadpool.queueSize

Gauge showing the current number of threads queued for execution.

**Data Source:** `ThreadPoolExecutor#getQueue().size()`
