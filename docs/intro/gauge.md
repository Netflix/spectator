# Gauges

A gauge is a handle to get the current value. Typical examples for gauges
would be the size of a queue or number of threads in the running state.
Since gauges are sampled, there is no information about what might have
occurred between samples.

Consider monitoring the behavior of a queue of tasks. If the data is being
collected once a minute, then a gauge for the size will show the size when
it was sampled. The size may have been much higher or lower at some point
during interval, but that is not known.

## Registration

A gauge is registered by passing in an id, a reference to the object, and
a function to get or compute a numeric value based on the object. Note that
a gauge should only be registered once, not on each update. Consider this
example of a web server tracking the number of connections:

```java
class HttpServer {
  // Tracks the number of current connections to the server
  private AtomicInteger numConnections;

  public HttpServer(Registry registry) {
    numConnections = registry.monitorNumber("server.numConnections", new AtomicInteger(0));
  }

  public void onConnectionCreated() {
    numConnections.incrementAndGet();
    ...
  }

  public void onConnectionClosed() {
    numConnections.decrementAndGet();
    ...
  }

  ...
}
```

Gauges are [passive](registry.md#passive), i.e., they are not directly updated
when some activity occurs. They provide a way to register a function that allows
the registry to retrieve the current value when it is needed.

The reference to the object is passed in separately and the spectator registry
will keep a weak reference to the object. If the object is garbage collected,
then it will automatically drop the registration. In the example above, the registry
will have a weak reference to `numConnections` and the server instance will
have a strong reference to `numConnections`. If the server instance goes away,
then the gauge will as well.

When multiple gauges are registered with the same id the reported value will
be the sum of the matches. For example, if multiple instances of the `HttpServer`
class were created on different ports, then the value `server.numConnections`
would be the total number of connections across all server instances. If a different
behavior is desired, then ensure your usage does not perform multiple registrations.

There are several different ways to register a gauge:

### Using Number

A gauge can also be created based on an implementation of Number. Note the number
implementation should be thread safe. For example:

```java
AtomicInteger size = new AtomicInteger();
registry.monitorNumber("queue.size", size);
```

The call will return the Number so the registration can be inline on the
assignment:

```java
AtomicInteger size = registry.monitorNumber("queue.size", new AtomicInteger());
```

Updates to the value are preformed by updating the number instance directly.

### Using Lambda

Specify a lambda that takes the object as parameter.

```java
public class Queue {

  @Inject
  public Queue(Registry registry) {
    registry.monitorValue("queue.size", this, Queue::size);
  }

  ...
}
```

!!! warning
    Be careful to avoid creating a reference to the object in the
    lambda. It will prevent garbage collection and can lead to a memory leak
    in the application. For example, by calling size without using the passed
    in object there will be a reference to `this`:

    ```
    registry.monitorValue("queue.size", this, obj -> size());
    ```

### Using Reflection

Use reflection to call a method on an object. The method must have an empty
parameter list and return a number. This approach can be used to access
methods that are not public.

```java
public class Queue {

  @Inject
  public Queue(Registry registry) {
    registry.methodValue("queue.size", this, "size");
  }

  ...
}
```

### Collection Sizes

For classes that implement `Collection` or `Map` there are helpers:

```java
Queue queue = new LinkedBlockingQueue();
registry.collectionSize("queue.size", queue);

Map<String, String> cache = new ConcurrentMap<>();
registry.mapSize("cache.size", cache);
```
