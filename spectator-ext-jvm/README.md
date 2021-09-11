## Description

Collect basic JVM [metrics] from the core system MBeans. Basic usage:

```java
Jmx.registerStandardMXBeans(registry);
```

[metrics]: https://netflix.github.io/spectator/en/latest/ext/jvm-memory-pools/

## Gradle

```
compile "com.netflix.spectator:spectator-ext-jvm:${version}"
```