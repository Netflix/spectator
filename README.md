
# Spectator

Simple library for instrumenting code to record dimensional time series.

## Requirements

* Java 7 or higher.

## Documentation

* [Wiki](https://github.com/Netflix/spectator/wiki)
* [Javadoc](http://netflix.github.io/spectator/javadoc/HEAD/spectator-api/)

## Dependencies

To instrument your code you need to depend on the api library. This provides the minimal interfaces
for you to code against and build test cases. The only dependency is slf4j.

```
com.netflix.spectator:spectator-api:0.14.2
```

If running at Netflix with the standard platform, see the
[Netflix Integration](https://github.com/Netflix/spectator/wiki/Netflix-Integration) page on the
wiki.

## Instrumenting Code

Suppose we have a server and we want to keep track of:

* Number of requests received with dimensions for breaking down by status code, country, and
  the exception type if the request fails in an unexpected way.
* Latency for handling requests.
* Summary of the response sizes.
* Current number of active connections on the server.

Here is some sample code that does that:

```java
// The Spectator class provides a static lookup for the default registry
Server s = new Server(Spectator.registry());

public class Server {
  private final ExtendedRegistry registry;
  private final Id requestCountId;
  private final Timer requestLatency;
  private final DistributionSummary responseSizes;

  // We can pass in the registry to make unit testing easier. Outside of tests it should typically
  // be bound to Spectator.registry() to make sure data goes to the right place.
  public Server(ExtendedRegistry registry) {
    this.registry = registry;

    // Create a base id for the request count. The id will get refined with additional dimensions
    // when we receive a request.
    requestCountId = registry.createId("server.requestCount");

    // Create a timer for tracking the latency. The reference can be held onto to avoid
    // additional lookup cost in critical paths.
    requestLatency = registry.timer("server.requestLatency");

    // Create a distribution summary meter for tracking the response sizes.
    responseSizes = registry.distributionSummary("server.responseSizes");

    // Gauge type that can be sampled. In this case it will invoke the specified method via
    // reflection to get the value. The registry will keep a weak reference to the object passed
    // in so that registration will not prevent garbage collection of the server object.
    registry.methodValue("server.numConnections", this, "getNumConnections");
  }

  public Response handle(Request req) {
    final long s = System.nanoTime();
    try {
      Response res = doSomething(req);

      // Update the counter id with dimensions based on the request. The counter will then
      // be looked up in the registry which should be fairly cheap, such as lookup of id object
      // in a ConcurrentHashMap. However, it is more expensive than having a local variable set
      // to the counter.
      final Id cntId = requestCountId
        .withTag("country", req.country())
        .withTag("status", res.status());
      registry.counter(cntId).increment();

      responseSizes.record(res.body().size());

      return res;
    } catch (Exception e) {
      final Id cntId = requestCountId
        .withTag("country", req.country())
        .withTag("status", "exception")
        .withTag("error", e.getClass().getSimpleName());
      registry.counter(cntId).increment();
      throw e;
    } finally {
      // Update the latency timer. This should typically be done in a finally block.
      requestLatency.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
    }
  }

  public int getNumConnections() {
    // however we determine the current number of connections on the server
  }
}
```
