# Timers

A timer is used to measure how long some event is taking. Two types of timers
are supported:

* `Timer`: for frequent short duration events.
* `LongTaskTimer`: for long running tasks.

The long duration timer is setup so that you can track the time while an
event being measured is still running. A regular timer just records the
duration and has no information until the task is complete.

As an example, consider a chart showing request latency to a typical web
server. The expectation is many short requests so the timer will be getting
updated many times per second.

![Request Latency](../images/request_latency.png)

Now consider a background process to refresh metadata from a data store. For
example, Edda caches AWS resources such as instances, volumes, auto-scaling
groups etc. Normally all data can be refreshed in a few minutes. If the AWS
services are having problems it can take much longer. A long duration timer
can be used to track the overall time for refreshing the metadata.

The charts below show max latency for the refresh using a regular timer and
a long task timer. Regular timer, note that the y-axis is using a logarithmic
scale:

![Regular Timer](../images/regular_timer.png)

Long task timer:

![Long Task Timer](../images/duration_timer.png)

## Timer

To get started create an instance using the registry:

```java
public class Server {

  private final Registry registry;
  private final Timer requestLatency;

  @Inject
  public Server(Registry registry) {
    this.registry = registry;
    requestLatency = registry.timer("server.requestLatency");
  }
```

Then wrap the call you need to measure, preferably using a lambda:

```java
  public Response handle(Request request) {
    return requestLatency.call(() -> handleImpl(request));
  }
```

The lambda variants will handle exceptions for you and ensure the
record happens as part of a finally block using the monotonic time.
It could also have been done more explicitly like:

```java
  public Response handle(Request request) {
    final long start = registry.clock().monotonicTime();
    try {
      return handleImpl(request);
    } finally {
      final long end = registry.clock().monotonicTime();
      requestLatency.record(end - start, TimeUnit.NANOSECONDS);
    }
  }
```

This example uses the clock from the registry which can be useful for
testing if you need to control the timing. In actual usage it will typically
get mapped to the system clock. It is recommended to use a monotonically
increasing source for measuring the times to avoid occasionally having bogus
measurements due to time adjustments. For more information see the
[Clock documentation](clock.md).

## LongTaskTimer

To get started create an instance using the registry:

```java
public class MetadataService {

  private final LongTaskTimer metadataRefresh;

  @Inject
  public MetadataService(Registry registry) {
    metadataRefresh = registry.longTaskTimer("metadata.refreshDuration");
    // setup background thread to call refresh()
  }

  private void refresh() {
    final int id = metadataRefresh.start();
    try {
      refreshImpl();
    } finally {
      metadataRefresh.stop(id);
    }
  }
```

The id is used to keep track of a particular task being measured by the timer.
It must be stopped using the provided id. Note that unlike a regular timer
that does not do anything until the final duration is recorded, a long duration
timer will report as two gauges:

* `duration`: total duration spent within all currently running tasks.
* `activeTasks`: number of currently running tasks.

This means that you can see what is happening while the task is running, but
you need to keep in mind:

* The id is fixed before the task begins. There is no way to change tags based
  on the run, e.g., update a different timer if an exception is thrown.
* Being a guage it is inappropriate for short tasks. In particular, gauges are
  sampled and if it is not sampled during the execution or the sampling period
  is a significant subset of the expected duration, then the duration value
  will not be meaningful.

Like a regular timer, the duration timer also supports using a lambda to
simplify the common case:

```java
  private void refresh() {
    metadataRefresh.run(this::refreshImpl);
  }
```
