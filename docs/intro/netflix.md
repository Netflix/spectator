# Netflix Integration

When running at Netflix, use the `atlas-client` library to enable transferring the
instrumented data to [Atlas](http://github.com/Netflix/atlas/wiki/). See the appropriate
section for the type of project you are working on:

* [Libraries](#libraries)
* [Applications](#applications), specifically standalone apps using guice or governator directly.
* [Base Server](#base-server)

## Libraries

For libraries, the only dependency that should be needed is:

```
com.netflix.spectator:spectator-api:0.51.0
```

The bindings to integrate internally should be included with the application. In your code,
just inject a registry, e.g.:

```java
public class Foo {
  @Inject
  public Foo(Registry registry) {
    ...
  }
  ...
}
```

See the [testing docs](testing.md) for more information about creating a binding to use with tests.

## Applications

Application should include a dependency on the `atlas-client` plugin:

```
netflix:atlas-client:latest.release
```

Note this is an internal only library with configs specific to the Netflix environments. It
is assumed you are using Nebula so that internal maven repositories are available for your
build. When configuring with governator specify the `AtlasModule`:

```java
Injector injector = LifecycleInjector.builder()
    .withModules(new AtlasModule())
    .build()
    .createInjector();
```

The registry binding will then be available so it can be injected as shown in the
[libraries section](#libraries). The insight libraries do not use any governator or guice
specific features. So it is possible to use guice or other dependency injection frameworks
directly with the following caveats:

1. However, some of the libraries do use the
[@PostConstruct](http://docs.oracle.com/javaee/7/api/javax/annotation/PostConstruct.html) and
[@PreDestroy](http://docs.oracle.com/javaee/7/api/javax/annotation/PreDestroy.html) annotations
for managing lifecycle. Governator adds lifecycle management and many other features on top of
guice and is the recommended way. For more minimalist support of just the lifecycle annotations
on top of guice see [iep-guice](https://github.com/Netflix/iep/tree/master/iep-guice#description).
2. The bindings and configuration necessary to run correctly with the internal setup are only
supported as guice modules. If trying to use some other dependency injection framework, then
you will be responsible for either finding a way to leverage the guice module in that framework
or recreating those bindings and maintaining them as things change. It is not a paved road path.

## Base Server

If using `base-server`, then you will get the Spectator and Atlas bindings automatically.

## Auto Plugin

!!! warning
    **Deprecated**: Use of AutoBindSingleton is generally discouraged. It is recommended to
    use one of the other methods.

If you are only interested in getting the GC logging, there is a library with an auto-bind
singleton that can be used:

```
com.netflix.spectator:spectator-nflx:0.51.0
```

Assuming you are using karyon/base-server or governator with `com.netflix` in the list of base
packages then the plugin should get automatically loaded.
