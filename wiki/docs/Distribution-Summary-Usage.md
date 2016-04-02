
A distribution summary is used to track the distribution of events. It is
similar to a timer, but more general in that the size does not have to be
a period of time. For example, a distribution summary could be used to measure
the payload sizes of requests hitting a server.

It is recommended to always use base units when recording the data. So if
measuring the payload size use bytes, not kilobytes or some other unit.

Distribution summaries are created using the registry which will be setup as
part of application initialization. For example:

```java
public class Server {

  private final DistributionSummary requestSize;

  @Inject
  public Server(Registry registry) {
    requestSize = registry.distributionSummary("server.requestSize");
  }
```

Then call record when an event occurs:

```java
  public Response handle(Request request) {
    requestSize.record(request.sizeInBytes());
  }
}
```

