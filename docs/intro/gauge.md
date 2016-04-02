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
a function to get or compute a numeric value based on the object. The
reference to the object is passed in separately and the spectator registry
will keep a weak reference to the object. If the object is garbage collected,
then it will automatically drop the registration.

When multiple gauges are registered with the same id the reported value will
be the sum of the matches.

### Using Lambda

Specify a lambda that takes the object as parameter.

```java
public class Queue {

  @Inject
  public Queue(Registry registry) {
    registry.gauge("queue.size", this, Queue::size);
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
    registry.gauge("queue.size", this, obj -> size());
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

### Using Number

A gauge can also be created based on an implementation of Number. For example:

```java
AtomicInteger size = new AtomicInteger();
registry.gauge("queue.size", size);
```

The call will return the Number so the registration can be inline on the
assignment:

```java
AtomicInteger size = registry.gauge("queue.size", new AtomicInteger());
```

### Collection Sizes

For classes that implement `Collection` or `Map` there are helpers:

```java
Queue queue = new LinkedBlockingQueue();
registry.collectionSize("queue.size", queue);

Map<String, String> cache = new ConcurrentMap<>();
registry.mapSize("cache.size", cache);
```
