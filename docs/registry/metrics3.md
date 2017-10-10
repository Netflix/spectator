# Metrics3 Registry

Registry that uses [metrics3](http://metrics.dropwizard.io/3.1.0/) as the
underlying implementation. To use the metrics registry, add a dependency on the
`spectator-reg-metrics3` library. For gradle:

```
com.netflix.spectator:spectator-reg-metrics3:0.58.0
```

Then when initializing the application, use the `MetricsRegistry`. For more
information see the [metrics3 example](https://github.com/brharrington/spectator-examples/tree/master/metrics3).
