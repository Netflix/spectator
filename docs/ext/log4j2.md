# Log4j2 Appender

Custom appender for [log4j2](http://logging.apache.org/log4j/2.x/) to track the number of
log messages reported. 

## Getting Started

To use it simply add a dependency:

```
com.netflix.spectator:spectator-ext-log4j2:0.56.0
```

Then in your application initialization:

```java
Registry registry = ...
SpectatorAppender.addToRootLogger(
    registry,             // Registry to use
    "spectator",          // Name for the appender
    false);               // Should stack traces be ignored?
```

This will add the appender to the root logger and register a listener so it will get
re-added if the configuration changes. You can also use the appender by specifying it
in the log4j2 configuration, but this will cause some of the loggers in Spectator to get
created before log4j is properly initialized and result in some lost log messages. With
that caveat in mind, if you need the additional flexibility of using the configuration then
specify the `Spectator` appender:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration monitorInterval="5" status="warn">
  <Appenders>
    <Spectator name="root"/>
  </Appenders>
  <Loggers>
    <Root level="debug">
      <AppenderRef ref="root"/>
    </Root>
  </Loggers>
</Configuration>
```

## Metrics

### log4j.numMessages

Counters showing the number of messages that have been passed to the appender.

**Unit:** messages/second

**Dimensions:**

* `appender`: name of the spectator appender.
* `loglevel`: standard log level of the events.

### log4j.numStackTraces

Counter for the number of messages with stack traces written to the logs. This will only be
collected if the `ignoreExceptions` flag is set to false for the appender.

**Unit:** messages/second

**Dimensions:**

* `appender`: name of the spectator appender.
* `loglevel`: standard log level of the events.
* `exception`: simple class name for the exception that was thrown.
* `file`: file name for where the exception was thrown.
