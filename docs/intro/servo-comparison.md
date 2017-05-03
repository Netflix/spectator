# Servo Comparison

[Servo](https://github.com/Netflix/servo) is an alternative client monitoring
library that is also developed by Netflix. Originally spectator was an
experiment for a simpler API that wrapped servo. It was done as a separate
project to avoid breaking backwards compatibility for servo.

From a user perspective, both will be supported for a long time, but most
of our efforts for future improvement will go to spectator. For new code it
is recommended to use the spectator API. If running
[at Netflix](Netflix Integration) the correct bindings will be in place
for both servo and spectator.

## Differences

This section provides a quick summary of the differences.

### Simpler API

Servo gives the user a lot of control, but this makes it hard to use
correctly. For example, to create a counter the user needs to understand
the tradeoffs and choose between:

* [BasicCounter](#basiccounter)
* [DynamicCounter](#dynamiccounter)
* [ContextualCounter](#contextualcounter)
* [StepCounter](#stepcounter)

Further each of these can impact how data is reported to observers. The
spectator API focuses on the constructs a user needs to instrument the
code. In spectator the user would always use the registry to create a
[Counter](counter.md). The implementation details are left up to the
registry.

The [registration](#registration) is simpler as well to avoid common pitfalls
when using servo like overwriting a registered object.

### More Focused

The goal of spectator is instrumenting code to send to a dimensional
time-series system like [Atlas](https://github.com/Netflix/atlas/wiki).
Servo has goals of staying compatible with a number of legacy libraries
and naming formats, exposing data to JMX, etc. Examples of how this
influences decisions:

* No support for non-numeric data. Servo supported this for exposing to JMX.
  Exposing the numeric data registered in spectator to JMX can be done
  using a registry that supports it, but there is no goal to be a general
  interface for exposing arbitrary data in JMX.
* No support for customizeable time units when reporting timer data. Base
  units should always be used for reporting and conversions can be performed
  in the presentation layer if needed. It also avoids a lot of the confusion
  around the timer unit for the data and issues like creating aggregates that
  are meaningless because of mixed units.

It is better to have a simple way to get correct and easy to reason about
data to the backend than many options. If you want more knobs, then you can use
Servo.

### DI Friendly

When servo was originally written dependency injection was not heavily used
at Netflix. Further servo needed to stay compatible with a number of use-cases
that were heavily static.

While spectator does have a static registry that can be used, the recommended
way is to create a registry and inject it either manually or via a framework
into the classes that need it. This also makes it much easier to
[test in isolation](Testing).

## Migration

If you want to migrate from the servo API to the spectator API, then this
section provides some guides on how servo constructs can be ported over. The
sub-sections are the class names of monitor types supported by servo.

For users at Netflix, note we are not actively pushing teams to migrate
or do any additional work. Servo is still supported and if it works for your
use-cases feel free to continue using it. 

### Registration

First read through the [servo docs on registration](https://github.com/Netflix/servo/wiki/Getting-Started#registration-of-metrics).
In servo if you have a class like:

```java
public class Foo {

  private AtomicInteger gauge;
  private Counter counter;

  public Foo(String id) {
    gauge = new AtomicInteger();
    counter = new BasicCounter(MonitorConfig.builder("counter").build());
    Monitors.registerObject(id, this);
  }

  @Monitor(name = "gauge", type = DataSourceType.GAUGE)
  private int gauge() {
    return gauge.get();
  }

  public void doSomething() {
    ...
  }
}
```

The state of the class is in the member variables of an instance of `Foo`.
If multiple instances of class `Foo` are created with the same value for `id`,
then the last one will overwrite the others for the registration. So the
values getting reported will only be from the last instance registered. Also
the registry has a reference to the instance of `Foo` so it will never go
away.

For counters and timers one way to get around this is to use
[DynamicCounter](#dynamiccounter) and [DynamicTimer](#dynamictimer)
respectively. Those classes will automatically handle the registration and
expire if there is no activity. They also get used for cases where the set
of dimensions is not known up front.

Gauges need to sample the state of something so they need to have a reference
to an object that contains the state. So the user would need to ensure that
only a single copy was registered leading to patterns like:

```java
class Foo {

  private static class FooStats {

    private AtomicInteger gauge;
    private Counter counter;

    public FooStats(String id) {
      gauge = new AtomicInteger();
      counter = new BasicCounter(MonitorConfig.builder("counter").build());
      Monitors.registerObject(id, this);
    }

    @Monitor(name = "gauge", type = DataSourceType.GAUGE)
    private int gauge() {
      return gauge.get();
    }
  }

  private static ConcurrentHashMap<String, FooStats> STATS =
    new ConcurrentHashMap<>();

  private final FooStats stats;

  public Foo(String id) {
    stats = STATS.computeIfAbsent(id, (i) -> new FooStats(i));
  }

  public void doSomething() {
    ...
    stats.update();
  }
}
```

This ensures that there is a single copy for a given id. In spectator this
example would look like: 

```java
public class Foo {

  private AtomicInteger gauge;
  private Counter counter;

  public Foo(Registry registry, String id) {
    Id gaugeId = registry.createId("gauge").withTag("id", id);
    gauge = registry.gauge(gaugeId, new AtomicInteger());
    counter = registry.counter("counter", "id", id);
  }

  public void doSomething() {
    ...
  }
}
```

Everything using the same registry will get the same counter
instance if the same id is used. For the gauge the registry will
keep a weak reference and will sum the values if multiple instances are
present. Since it is a weak reference, nothing will prevent an instance of
`Foo` from getting garbage collected.

### Annotations

Annotations are not supported, use the appropriate meter type:

| DataSourceType | Spectator Alternative       |
|----------------|-----------------------------|
| COUNTER        | [Counter Usage](counter.md) |
| GAUGE          | [Gauge Usage](gauge.md)     |
| INFORMATIONAL  | Not supported               |

### BasicCounter

See the general overview of [registration differences](#registration) and
summary of [counter usage](counter.md).

Servo:

```java
public class Foo {
  private final Counter c =
    new BasicCounter(MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }

  public void doSomething() {
    c.increment();
  }
}
```

Spectator:

```java
public class Foo {
  private final Counter c;

  @Inject
  public Foo(Registry registry, String id) {
    c = registry.counter("name", "id", id);
  }

  public void doSomething() {
    c.increment();
  }
}
```

### BasicGauge

See the general overview of [registration differences](#registration) and
summary of [gauge usage](gauge.md).

Servo:

```java
public class Foo {
  private final BasicGauge g = new BasicGauge(
    MonitorConfig.builder("name").build(),
    this::getCurrentValue);

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }
}
```

Spectator:

```java
public class Foo {
  @Inject
  public Foo(Registry registry, String id) {
    Id gaugeId = registry.createId("name").withTag("id", id);
    registry.gauge(gaugeId, this, Foo::getCurrentValue);
  }
}
```

### BasicTimer

See the general overview of [registration differences](#registration) and
summary of [timer usage](timer.md). Note in spectator the reported unit
for timers is always seconds and cannot be changed. Seconds is the base unit
and other units should only be used as a presentation detail. Servo allows
the unit to be customized and defaults to milliseconds.

Servo:

```java
public class Foo {
  private final Timer t = new BasicTimer(
    MonitorConfig.builder("name").build(), TimeUnit.SECONDS);

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }

  public void doSomething() {
    Stopwatch s = t.start();
    try {
      ...
    } finally {
      s.stop();
    }
  }
}
```

Spectator:

```java
public class Foo {
  private final Timer t;

  @Inject
  public Foo(Registry registry, String id) {
    t = registry.timer("name", "id", id);
  }

  public void doSomething() {
    t.record(() -> {
      ...
    });
  }
}
```

### BasicDistributionSummary

See the general overview of [registration differences](#registration) and
summary of [distribution summary usage](dist-summary.md).

Servo:

```java
public class Foo {
  private final BasicDistributionSummary s = new BasicDistributionSummary(
    MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }

  public void doSomething() {
    ...
    s.record(getValue());
  }
}
```

Spectator:

```java
public class Foo {
  private final DistributionSummary s;

  @Inject
  public Foo(Registry registry, String id) {
    s = registry.distributionSummary("name", "id", id);
  }

  public void doSomething() {
    ...
    s.record(getValue());
  }
}
```

### BasicInformational

Not supported, see [overview of differences](#differences).

### BasicStopwatch

There isn't an explicit stopwatch class in spectator. Just use a timing
call directly. 

Servo:

```java
  public void doSomething() {
    Stopwatch s = timer.start();
    try {
      ...
    } finally {
      s.stop();
    }
  }
```

Spectator:

```java
  public void doSomething() {
    final long s = System.nanoTime();
    try {
      ...
    } finally {
      timer.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
    }
  }
```

### BucketTimer

See the general overview of [registration differences](#registration) and
summary of [sandbox documentation](Sandbox). Note in spectator BucketTimer is
provided in the sandbox extension library and may change in future as we
gain more experience using it.

Servo:

```java
public class Foo {
  private final Timer t = new BucketTimer(
    MonitorConfig.builder("name").build(),
    new BucketConfig.Builder()
      .withTimeUnit(TimeUnit.MILLISECONDS)
      .withBuckets(new long[] { 500, 2500, 5000, 10000 })
      .build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }

  public void doSomething() {
    Stopwatch s = t.start();
    try {
      ...
    } finally {
      s.stop();
    }
  }
}
```

Spectator:

```java
public class Foo {
  private final Timer t;

  @Inject
  public Foo(Registry registry, String id) {
    Id timerId = registry.createId("name", "id", id);
    BucketFunction f = BucketFunctions.latency(10, TimeUnit.SECONDS);
    t = BucketTimer.get(registry, timerId, f);
  }

  public void doSomething() {
    t.record(() -> {
      ...
    });
  }
}
```

### ContextualCounter

Not supported. A fixed tag list for the context is too rigid and this class
was never used much at Netflix. Future work being looked at in
[issue-180](https://github.com/Netflix/spectator/issues/180).

### ContextualTimer

Not supported. A fixed tag list for the context is too rigid and this class
was never used much at Netflix. Future work being looked at in
[issue-180](https://github.com/Netflix/spectator/issues/180).

### DoubleGauge

See the general overview of [registration differences](#registration) and
summary of [gauge usage](gauge.md).

Servo:

```java
public class Foo {
  private final DoubleGauge g = new DoubleGauge(
    MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }
}
```

Spectator:

```java
import com.google.common.util.concurrent.AtomicDouble;

public class Foo {
  private final AtomicDouble v;

  @Inject
  public Foo(Registry registry, String id) {
    Id gaugeId = registry.createId("name").withTag("id", id);
    v = registry.gauge(gaugeId, new AtomicDouble());
  }
}
```

### DurationTimer

See the general overview of [registration differences](#registration) and
summary of [timer usage](timer.md#longtasktimer).

Servo:

```java
public class Foo {
  private final DurationTimer t = new DurationTimer(
    MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }
}
```

Spectator:

```java
public class Foo {
  private final LongTaskTimer t;

  @Inject
  public Foo(Registry registry, String id) {
    t = registry.longTaskTimer("name", "id", id);
  }
}
```

### DynamicCounter

See the general overview of [registration differences](#registration) and
summary of [counter usage](counter.md).

Servo:

```java
public class Foo {

  private final String id;

  public Foo(String id) {
    this.id = id;
  }

  public void doSomething(Context ctxt) {
    DynamicCounter.increment("staticId", "id", id);
    DynamicCounter.increment("dynamicId", "id", id, "foo", ctxt.getFoo());
  }
}
```

Spectator:

```java
public class Foo {
  private final Registry registry;
  private final String id;
  private final Counter staticCounter;
  private final Id dynamicId;

  @Inject
  public Foo(Registry registry, String id) {
    this.registry = registry;
    this.id = id;
    staticCounter = registry.counter("staticId", "id", id);
    dynamicId = registry.createId("dynamicId", "id", id);
  }

  public void doSomething(Context ctxt) {
    // Keeping the reference to the counter avoids additional allocations
    // to create the id object and the lookup cost
    staticCounter.increment();

    // If the id is dynamic it must be looked up
    registry.counter("dynamicId", "id", id, "foo", ctxt.getFoo()).increment();

    // This will update the same counter as the line above, but the base part
    // of the id is precomputed to make it cheaper to construct the id.
    registry.counter(dynamicId.withTag("foo", ctxt.getFoo())).increment();
  }
}
```

### DynamicTimer

See the general overview of [registration differences](#registration) and
summary of [timer usage](timer.md).

Servo:

```java
public class Foo {

  private final String id;
  private final MonitorConfig staticId;

  public Foo(String id) {
    this.id = id;
    staticId = MonitorConfig.builder("staticId").withTag("id", id).build();
  }

  public void doSomething(Context ctxt) {
    final long d = ctxt.getDurationMillis();
    DynamicTimer.record(staticId, TimeUnit.SECONDS, d, TimeUnit.MILLISECONDS);

    MonitorConfig dynamicId = MonitorConfig.builder("dynamicId")
      .withTag("id", id)
      .withTag("foo", ctxt.getFoo())
      .build();
    DynamicTimer.record(dynamicId, TimeUnit.SECONDS, d, TimeUnit.MILLISECONDS);
  }
}
```

Spectator:

```java
public class Foo {
  private final Registry registry;
  private final String id;
  private final Timer staticTimer;
  private final Id dynamicId;

  @Inject
  public Foo(Registry registry, String id) {
    this.registry = registry;
    this.id = id;
    staticTimer = registry.timer("staticId", "id", id);
    dynamicId = registry.createId("dynamicId", "id", id);
  }

  public void doSomething(Context ctxt) {
    final long d = ctxt.getDurationMillis();

    // Keeping the reference to the timer avoids additional allocations
    // to create the id object and the lookup cost
    staticTimer.record(d, TimeUnit.MILLISECONDS);

    // If the id is dynamic it must be looked up
    registry.timer("dynamicId", "id", id, "foo", ctxt.getFoo())
      .record(d, TimeUnit.MILLISECONDS);

    // This will update the same timer as the line above, but the base part
    // of the id is precomputed to make it cheaper to construct the id.
    registry.timer(dynamicId.withTag("foo", ctxt.getFoo()))
      .record(d, TimeUnit.MILLISECONDS);
  }
}
```

### LongGauge

See the general overview of [registration differences](#registration) and
summary of [gauge usage](gauge.md).

Servo:

```java
public class Foo {
  private final LongGauge g = new LongGauge(
    MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }
}
```

Spectator:

```java
public class Foo {
  private final AtomicLong v;

  @Inject
  public Foo(Registry registry, String id) {
    Id gaugeId = registry.createId("name").withTag("id", id);
    v = registry.gauge(gaugeId, new AtomicLong());
  }
}
```

### MonitorConfig

See the documentation on [naming](conventions.md).

Servo:

```java
MonitorConfig id = MonitorConfig.builder("name")
  .withTag("country", "US")
  .withTag("device",  "xbox")
  .build();
```

Spectator:

```java
Id id = registry.createId("name")
  .withTag("country", "US")
  .withTag("device",  "xbox");

// or

Id id = registry.createId("name", "country", "US", "device", "xbox");
```

### MonitoredCache

Not supported because spectator does not have a direct dependency on guava.
If there is enough demand an extension can be created.

### NumberGauge

See the general overview of [registration differences](#registration) and
summary of [gauge usage](gauge.md).

Servo:

```java
public class Foo {
  private final NumberGauge g = new NumberGauge(
    MonitorConfig.builder("name").build(), new AtomicLong());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }
}
```

Spectator:

```java
public class Foo {
  private final AtomicLong v;

  @Inject
  public Foo(Registry registry, String id) {
    Id gaugeId = registry.createId("name").withTag("id", id);
    v = registry.gauge(gaugeId, new AtomicLong());
  }
}
```

### StatsTimer

Not supported, see [overview of differences](#differences).

### StepCounter

See the general overview of [registration differences](#registration) and
summary of [counter usage](counter.md).

Servo:

```java
public class Foo {
  private final Counter c =
    new StepCounter(MonitorConfig.builder("name").build());

  public Foo(String id) {
    Monitors.registerObject(id, this);
  }

  public void doSomething() {
    c.increment();
  }
}
```

Spectator:

```java
public class Foo {
  private final Counter c;

  @Inject
  public Foo(Registry registry, String id) {
    c = registry.counter("name", "id", id);
  }

  public void doSomething() {
    c.increment();
  }
}
```
