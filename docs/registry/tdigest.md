# T-Digest Registry

!!! warning
    Deprecated, use PercentileTimer or PercentileDistributionSummary instead.

The TDigest registry is used for collecting dimensional percentile data and reporting to
backends. This is currently an experiment and not ready for general use. Current instructions
assume it is being used [internally at Netflix](Netflix-Integration).

## Getting Started

To use it simply add a dependency:

```
com.netflix.spectator:spectator-reg-tdigest:0.37.0
```

Add the `TDigestModule` to the set of modules used with guice:

```java
Injector injector = Guice.createInjector(
  new SpectatorModule(),
  new TDigestModule(),
  ...);
```

Then just inject the `TDigestRegistry`:

```java
public class Foo {
  private final Registry registry;

  public Foo(TDigestRegistry digestRegistry) {
    registry = digestRegistry;
  }

  public void doSomething() {
    registry.timer("foo.doSomething").record(() -> {
      ... something ...
    });
  }
}
```

Timers and distribution summaries accessed via the `TDigestRegistry` will be recorded to both
the main registry and the digest registry.

## Recommendations

* Use digests sparingly. The digest structure will take more memory locally and have additional
overhead compared to typical implementations. 

## Testing

For unit tests use the `TDigestTestModule` to get an injectable `TDigestRegistry`. Then follow
the [normal testing instructions](Testing). Example:

```java
@RunWith(JUnit4.class)
public class FooTest {

  @Test
  public void doSomething() throws Exception {
    Injector injector = Guice.createInjector(TDigestTestModule.create());
    injector.getInstance(Foo.class).doSomething();

    final TDigestRegistry r = injector.getInstance(TDigestRegistry.class);
    Assert.assertEquals(1, r.timer("foo").count());
    Assert.assertEquals(42, r.timer("foo").totalTime());
  }

  private static class Foo {
    private final Timer t;

    @Inject
    Foo(TDigestRegistry registry) {
      t = registry.timer("foo");
    }

    void doSomething() {
      t.record(42, TimeUnit.NANOSECONDS);
    }
  }
}
```

## Known Issues

* Only reports to kinesis for now. This will be changed after the configuration format is
  figured out.
* There is still some debate about whether to control the access via the code and injecting
  the specific registry or using configuration. 
* There is work in progress on having a fixed limit to protect both the local client and
  backends. If the limit is reached rollup policies would get triggered to gracefully degrade.
* Can we support high level digest along with basic timer with more dimensionality. Still
  investigating pending the outcome of previous bullets.
* Not yet supported via sidecar endpoints.
