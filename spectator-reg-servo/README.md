## Description

**DEPRECATED:** servo delegates to Spectator internally now thus using this library
can lead to a cycle. Servo is deprecated and usage should be phased out.

Registry implementation that wraps the [Servo] library.

[Atlas]: https://github.com/Netflix/servo/

## Gradle

```
compile "com.netflix.spectator:spectator-reg-servo:${version}"
```