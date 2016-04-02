
A counter is used to measure the rate at which some event is occurring.
Consider a simple queue, counters would be used to measure things like the
rate at which items are being inserted and removed. 

Counters are created using the registry which will be setup as part of
application initialization. For example:

```java
public class Queue {

  private final Counter insertCounter;
  private final Counter removeCounter;
  private final QueueImpl impl;

  @Inject
  public Queue(Registry registry) {
    insertCounter = registry.counter("queue.insert");
    removeCounter = registry.counter("queue.remove");
    impl = new QueueImpl();
  }
```

Then call increment when an event occurs:

```java
  public void insert(Object obj) {
    insertCounter.increment();
    impl.insert(obj);
  }

  public Object remove() {
    if (impl.nonEmpty()) {
      removeCounter.increment();
      return impl.remove();
    } else {
      return null;
    }
  }
```

Optionally an amount can be passed in when calling increment. This is useful
when a collection of events happens together. 

```java
  public void insertAll(Collection<Object> objs) {
    insertCounter.increment(objs.size());
    impl.insertAll(objs);
  }
}
```

