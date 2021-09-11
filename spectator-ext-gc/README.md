## Description

Subscribe to the notification emitter for the Java garbage collector and report
[metrics] to the global registry. Basic usage:

```java
GcLogger gcLogger = new GcLogger();
gcLogger.start(null);
```

[metrics]: https://netflix.github.io/spectator/en/latest/ext/jvm-gc/

## Gradle

```
compile "com.netflix.spectator:spectator-ext-gc:${version}"
```