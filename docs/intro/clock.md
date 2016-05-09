# Clock

When taking measurements or working with timers it is recommended to use the
[clock](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/Clock.html)
interface. It provides two methods for measuring time:

## Wall Time

This is what most users think of for time. It can be used to get the current
time like what you would see on a wall clock. In most cases when not running
in tests this will call [System.currentTimeMillis()](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#currentTimeMillis--).

Note that the values returned by this method may not be monontonically increasing.
Just like a clock on your wall, this value can go back in time or jump forward
at unpredictable intervals if someone sets the time. On many systems
[ntpd](https://en.wikipedia.org/wiki/Ntpd) will be constantly keeping the time
synced up with an authoritative source.

With spectator, the clock is typically accessed via the [regstry](registry.md).
Example of usage:

```java
// Current time in milliseconds since the epoch
long currentTime = registry.clock().wallTime();
```

## Monotonic Time

While it is good in general for the wall clock to show the correct time, the
unpredictable changes mean it is not a good choice for measuring how long an
operation took. Consider a simple example of measuring request latency on a
server:

```java
long start = registry.clock().wallTime();
handleRequest(request, response);
long end = registry.clock().wallTime();
reqLatencyTimer.record(end - start, TimeUnit.MILLISECONDS);
```

If ntp fixes the server time between `start` and `end`, then the recorded
latency will be wrong. Spectator will protect against obviously wrong
measurements like negative latencies by dropping those values when they are
recorded. However, the change could incorrectly shorten or lengthen the
measured latency.

The clock interface also provides access to a monotonic source that is
only useful for measuring elapsed time, for example:

```java
long start = registry.clock().monotonicTime();
handleRequest(request, response);
long end = registry.clock().monotonicTime();
reqLatencyTimer.record(end - start, TimeUnit.NANOSECONDS);
```

In most cases this will map to
[System.nanoTime()](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--).
Note the actual value returned is not meaningful unless compared with
another sample to get a delta.

## Manual Clock

If timing code is written to the clock interface, then alternative implemenations
can be plugged in. For test cases it is common to use
[ManualClock](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/ManualClock.html)
so that tests can be reliable and fast without having to rely on hacks like sleep or
assuming something will run in less than a certain amount of time. 

```java
ManualClock clock = new ManualClock();
Registry registry = new DefaultRegistry(clock);

Timer timer = registry.timer("test");
timer.record(() -> {
  doSomething();
  clock.setMonotonicTime(42L);
});

Assert.assertEquals(timer.totalTime(), 42L);
```

