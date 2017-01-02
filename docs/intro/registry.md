# Registry

There are a few basic concepts you need to learn to use Spectator.
The [registry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/Registry.html)
is the main class for managing a set of meters. A meter is a class for collecting a set of
measurements about your application.

## Choosing an Implementation

The core spectator library, `spectator-api`, comes with the following registry implementations:
 
 <table>
   <thead>
     <th>Class</th>
     <th>Dependency</th>
     <th>Description</th>
   </thead>
   <tbody>
     <tr>
       <td>
       [DefaultRegistry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/DefaultRegistry.html)
       </td>
       <td>spectator-api</td>
       <td>
       Updates local counters, frequently used with [unit tests](testing.md).
       </td>
     </tr>
     <tr>
       <td>
       [NoopRegistry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/NoopRegistry.html)
       </td>
       <td>spectator-api</td>
       <td>
       Does nothing, tries to make operations as cheap as possible. This implementation is
       typically used to help understand the overhead being created due to instrumentation.
       It can also be useful in testing to help ensure that no side effects were introduced
       where the instrumentation is now needed in order for the application for function
       properly.
       </td>
     </tr>
     <tr>
       <td>
       [ServoRegistry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-reg-servo/com/netflix/spectator/servo/ServoRegistry.html)
       </td>
       <td>[spectator-reg-servo](../registry/servo.md)</td>
       <td>
       Map to [servo library](https://github.com/Netflix/servo). This is the implementation
       typically used at Netflix to report data into [Atlas](https://github.com/Netflix/atlas).
       </td>
     </tr>
     <tr>
       <td>
       [MetricsRegistry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-reg-metrics3/com/netflix/spectator/metrics3/MetricsRegistry.html)
       </td>
       <td>[spectator-reg-metrics3](../registry/metrics3.md)</td>
       <td>
       Map to [metrics3 library](http://metrics.dropwizard.io/3.1.0/). This implementation
       is typically used for reporting to local files, JMX, or other backends like Graphite.
       Note that it uses a hierarchical naming scheme rather than the dimensional naming
       used by Spectator, so the names will get flattened when mapped to this registry.
       </td>
     </tr>
   </tbody>
 </table>

It is recommended for libraries to write code against the
[Registry](http://netflix.github.io/spectator/en/latest/javadoc/spectator-api/com/netflix/spectator/api/Registry.html)
interface and allow the implementation to get injected by the user of the library. The
simplest way is to accept the registry via the constructor, for example:

```java
public class HttpServer {
  public HttpServer(Registry registry) {
    // use registry to collect measurements
  }
}
```

The user of the class can then provide the implementation:

```java
Registry registry = new DefaultRegistry();
HttpServer server = new HttpServer(registry);
```

More complete examples can be found on the [testing page](testing.md) or in the
[spectator-examples repo](https://github.com/brharrington/spectator-examples).

## Working With Ids

Spectator is primarily intended for collecting data for dimensional time series
backends like [Atlas](https://github.com/Netflix/atlas). The ids used for looking
up a meter in the registry consist of a name and set of tags. Ids will be consumed
many times by users after the data has been reported so they should be chosen with
some care and thought about how they will get used. See the [conventions page](conventions.md)
for some general guidelines.

Ids are created via the registry, for example:

```java
Id id = registry.createId("server.requestCount");
```

The ids are immutable so they can be freely passed around and used in a concurrent
context. Tags can be added when an id is created:

```java
Id id = registry.createId("server.requestCount", "status", "2xx", "method", "GET");
```

Or by using `withTag` and `withTags` on an existing id:

```java
public class HttpServer {
  private final Id baseId;

  public HttpServer(Registry registry) {
    baseId = registry.createId("server.requestCount");
  }

  private void handleRequestComplete(HttpRequest req, HttpResponse res) {
    // Remember Id is immutable, withTags will return a copy with the
    // the additional metadata
    Id reqId = baseId.withTags(
      "status", res.getStatus(),
      "method", req.getMethod().name());
    registry.counter(reqId).increment();
  }

  private void handleRequestError(HttpRequest req, Throwable t) {
    // Can also be added individually using `withTag`. However, it is better
    // for performance to batch modifications using `withTags`.
    Id reqId = baseId
      .withTag("error",  t.getClass().getSimpleName())
      .withTag("method", req.getMethod().name());
    registry.counter(reqId).increment();
  }
}
```

## Collecting Measurements

Once you have an id, the registry can be used to get an instance of a meter to
record a measurement. Meters can roughly be categorized in two groups:

### Active

Active meters are ones that are called directly when some event occurs. There are
three basic types supported:

* [Counters](counter.md): measures how often something is occuring. This will be
  reported to backend systems as a rate per second. For example, number of requests
  processed by web server.
* [Timers](timer.md): measures how long something took. For example, latency of
  requests processed by a web server.
* [Distribution Summaries](dist-summary.md): measures the size of something. For
  example, entity sizes for requests processed by a web server.

### Passive

Passive meters are ones where the registry just has a reference to get the value
when needed. For example, the number of current connections on a web server or
the number threads that are currently in use. These will be [gauges](gauge.md).

## Global Registry

There are some use-cases where injecting the registry is not possible or is too
cumbersome. The main example from the core spectator libraries is the
[log4j appender](../ext/log4j2.md). The global registry is useful there because
logging is often initialized before any other systems and Spectator itself uses
logging via the slf4j api which is quite likely being bound to log4j when that
the appender is being used. By using the global registry the logging initialization
can proceed before the spectator initialization in the application. Though any
measurements taken before a registry instance has been added will be lost.

The global registry is accessed using:

```java
Registry registry = Spectator.globalRegistry();
```

By default it will not record anything. For a specific registry instance you can
choose to configure it to work with the global registry by calling `add`:

```java
public void init() {
  Registry registry = // Choose an appropriate implementation
  
  // Add it to the global registry so it will receive
  // any activity on the global registry
  Spectator.globalRegistry().add(registry);
}
```

Any measurements taken while no registries are added to the global instance will
be lost. If multiple registries are added, all will recieve updates made to the global
registry.
