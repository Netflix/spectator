## Description

Implementation of `org.apache.spark.metrics.sink.Sink` for reporting Spark system metrics
to a local sidecar using [spectator-reg-stateless](../spectator-reg-stateless/README.md).

## Gradle

```
compile "com.netflix.spectator:spectator-ext-spark:${version}:shadow"
```