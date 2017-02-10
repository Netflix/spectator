# Servo Registry

Registry that uses [servo](https://github.com/Netflix/servo) as the underlying
implementation. To use the servo registry, add a dependency on the
`spectator-reg-servo` library. For gradle:

```
com.netflix.spectator:spectator-reg-servo:0.51.0
```

Then when initializing the application, use the `ServoRegistry`. If using guice
then that would look like:

```java
Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
    }

    @Provides
    @Singleton
    private Registry providesRegistry() {
      return new ServoRegistry();
    }
  });
```

For more information see the [servo example](https://github.com/brharrington/spectator-examples/tree/master/servo). Note if running internally at Netflix see the
[Netflix Integration](../intro/netflix.md) docs instead.
