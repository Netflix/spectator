# Placeholders

The placeholders extension allows for identifiers to be created with dimensions that
will get filled in based on the context when an activity occurs. The primary use-cases
are to support:

1. Optional dimensions that can be conditionally enabled.
2. Pulling dimensions from another context such as a thread local store. This can make
   it is easier to share the across various parts of the code.
   
## Dependencies

To use the placeholders support add a dependency on:

```
com.netflix.spectator:spectator-ext-placeholders:0.54.0
```

## Usage

Placeholder support is available for activity based types including
[counters](../intro/counter.md), [timers](../intro/timer.md), and
[distribution summaries](../intro/dist-summary.md). To get started create a
`PlaceholderFactory` from the registry:

```java
PlaceholderFactory factory = PlaceholderFactory.from(registry);
```

Then use the factory to create an identifier using a `TagFactory` to dynamically fetch
the value for a given dimension when some activity occurs. Suppose we want to use a
dynamic configuration library such as [Archaius](https://github.com/Netflix/archaius/tree/2.x)
to conditionally enable a dimension with high cardinality:

```java
public class Server {
  
  private final Context context;
  private final Counter rps;
  
  public Server(Context context, PropertyFactory props, Registry registry) {
    this.context = context;
    
    // Property that can be dynamically updated to indicate whether or not
    // detailed dimensions should be added to metrics.
    Property<Boolean> enabled = props
      .getProperty("server.detailedMetricsEnabled")
      .asBoolean(false);
      
    // Factory for creating instances of the counter using placeholders
    PlaceholderFactory factory = PlaceholderFactory.from(registry);
    
    // Create the underlying id with 4 possible dimensions:
    // *  method and status - low cardinality and always added if available
    //    in the context.
    // *  geo and device - high cardinality and only available if the property
    //    to enable detailed metrics is set to true.
    PlaceholderId rpsId = factory.createId("server.requests")
      .withTagFactory(TagFactory.from("method", context::getMethod))
      .withTagFactory(TagFactory.from("status", context::getStatus))
      .withTagFactory(new DetailedDimension("geo", enabled, context::getGeo))
      .withTagFactory(new DetailedDimension("device", enabled, context::getDevice));
    rps = factory.counter(rpsId);
  }
  
  public Response handle(Request request) {
    fillInContext(request);
    Response response = process(request);
    fillInContext(response);
    
    // Update the counter, the placeholders will be resolved when the activity, in
    // this case the increment is called.
    rps.increment();
    return response;
  }
 
  // Tag factory that can be controlled with an enabled property.
  private static class DetailedDimension implements TagFactory {
    
    private final String name;
    private final Supplier<String> valueFunc;
    
    DetailedDimension(String name, Property<Boolean> enabled, Supplier<String> valueFunc) {
      this.name = name;
      this.enabled = enabled;
      this.valueFunc = valueFunc;
    }
    
    @Override public String name() {
      return name;
    }
    
    @Override public Tag createTag() {
      return enabled.get()
          ? new BasicTag(name, valueFunc.get())
          : null;
    }
  }
}
```


