/*
 * Copyright 2014-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.log4j;

import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class SpectatorAppenderTest {

  private Registry registry;
  private SpectatorAppender appender;

  private LogEvent newEvent(Level level, Throwable t) {
    return newEvent(level, t, true);
  }

  private LogEvent newEvent(Level level, Throwable t, boolean includeSource) {
    final String cname = SpectatorAppenderTest.class.getName();
    final StackTraceElement e = (t == null || !includeSource) ? null : t.getStackTrace()[0];
    return new Log4jLogEvent.Builder()
        .setLoggerName(cname)
        .setLoggerFqcn(cname)
        .setLevel(level)
        .setThrown(t)
        .setSource(e)
        .setTimeMillis(0L)
        .build();
  }

  @BeforeEach
  public void before() {
    registry = new DefaultRegistry();
    appender = new SpectatorAppender(
        registry, "foo", null, null, false, Property.EMPTY_ARRAY);
    appender.start();
  }

  @Test
  public void numMessagesERROR() {
    Counter c = registry.counter("log4j.numMessages", "appender", "foo", "loglevel", "2_ERROR");
    Assertions.assertEquals(0, c.count());
    appender.append(newEvent(Level.ERROR, null));
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void numMessagesDEBUG() {
    Counter c = registry.counter("log4j.numMessages", "appender", "foo", "loglevel", "5_DEBUG");
    Assertions.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, null));
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void numStackTraces() {
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "SpectatorAppenderTest.java");
    Assertions.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo")));
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void numStackTracesNoSource() {
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "unknown");
    Assertions.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo"), false));
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void ignoreExceptions() {
    appender = new SpectatorAppender(
        registry, "foo", null, null, true, Property.EMPTY_ARRAY);
    appender.start();
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "SpectatorAppenderTest.java");
    Assertions.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo")));
    Assertions.assertEquals(0, c.count());
  }

  @Test
  public void appendIsNotReentrant() {
    AtomicInteger lookups = new AtomicInteger();
    AtomicReference<SpectatorAppender> appenderRef = new AtomicReference<>();
    ReentrantRegistry reentrant = new ReentrantRegistry(() -> {
      lookups.incrementAndGet();
      appenderRef.get().append(newEvent(Level.ERROR, null));
    });
    appenderRef.set(new SpectatorAppender(
        reentrant, "foo", null, null, false, Property.EMPTY_ARRAY));
    appenderRef.get().start();

    appenderRef.get().append(newEvent(Level.ERROR, null));
    appenderRef.get().append(newEvent(Level.ERROR, null));

    Assertions.assertEquals(2, lookups.get(), "each outer append should reach one lookup");
    Assertions.assertEquals(2, reentrant.count(
        "log4j.numMessages", "appender", "foo", "loglevel", "2_ERROR"));
  }

  /** Registry that runs an action before each meter lookup. */
  private static final class ReentrantRegistry extends AbstractRegistry {
    private final Registry delegate = new DefaultRegistry();
    private final Runnable onLookup;

    ReentrantRegistry(Runnable onLookup) {
      super(Clock.SYSTEM);
      this.onLookup = onLookup;
    }

    double count(String name, String... tags) {
      return delegate.counter(name, tags).count();
    }

    @Override protected <T extends Meter> T getOrCreate(
        Id id, Class<T> cls, T dflt, Function<Id, T> factory) {
      onLookup.run();
      return super.getOrCreate(id, cls, dflt, factory);
    }

    @Override protected Counter newCounter(Id id) {
      return delegate.counter(id);
    }

    @Override protected DistributionSummary newDistributionSummary(Id id) {
      return delegate.distributionSummary(id);
    }

    @Override protected Timer newTimer(Id id) {
      return delegate.timer(id);
    }

    @Override protected Gauge newGauge(Id id) {
      return delegate.gauge(id);
    }

    @Override protected Gauge newMaxGauge(Id id) {
      return delegate.maxGauge(id);
    }
  }

  @Test
  public void nullFilename() {
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "2_ERROR",
        "exception", "RuntimeException",
        "file", "unknown");
    Assertions.assertEquals(0, c.count());

    String cname = SpectatorAppender.class.getName();
    Throwable t = new RuntimeException("test");
    t.fillInStackTrace();
    StackTraceElement source = new StackTraceElement(cname, "foo", null, 0);
    LogEvent event = new Log4jLogEvent.Builder()
        .setLoggerName(cname)
        .setLoggerFqcn(cname)
        .setLevel(Level.ERROR)
        .setThrown(t)
        .setSource(source)
        .setTimeMillis(0L)
        .build();
    appender.append(event);
    Assertions.assertEquals(1, c.count());
  }
}
