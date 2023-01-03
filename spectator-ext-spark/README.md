## Description

Implementation of `org.apache.spark.metrics.sink.Sink` for reporting Spark system metrics
to a local sidecar using [spectator-reg-sidecar](../spectator-reg-sidecar/README.md).

## Gradle

```
compile "com.netflix.spectator:spectator-ext-spark:${version}:shadow"
```