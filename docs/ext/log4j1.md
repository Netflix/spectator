# Log4j1 Appender

Custom appender for [log4j1](http://logging.apache.org/log4j/1.2/) to track the number of
log messages reported. 

!!! note
    Log4j 1.x has reached [end of life][eol] and is no longer supported by Apache. This extension
    is provided for some users that have difficulty moving to a supported version of log4j.
    
[eol]: https://blogs.apache.org/foundation/entry/apache_logging_services_project_announces

## Getting Started

To use it simply add a dependency:

```
com.netflix.spectator:spectator-ext-log4j1:0.85.0
```

Then in your log4j configuration specify the `com.netflix.spectator.log4j.SpectatorAppender`.
In a properties file it would look something like:

```
log4j.rootLogger=ALL, A1
log4j.appender.A1=com.netflix.spectator.log4j.SpectatorAppender
```

## Metrics

### log4j.numMessages

Counters showing the number of messages that have been passed to the appender.

**Unit:** messages/second

**Dimensions:**

* `loglevel`: standard log level of the events.

### log4j.numStackTraces

Counter for the number of messages with stack traces written to the logs.

**Unit:** messages/second

**Dimensions:**

* `loglevel`: standard log level of the events.
* `exception`: simple class name for the exception that was thrown.
* `file`: file name for where the exception was thrown.
