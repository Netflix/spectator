/**
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpectatorAppenderTest {

  private Registry registry;
  private SpectatorAppender appender;

  private LogEvent newEvent(Level level, Throwable t) {
    return newEvent(level, t, true);
  }

  private LogEvent newEvent(Level level, Throwable t, boolean includeSource) {
    final String cname = SpectatorAppenderTest.class.getName();
    final StackTraceElement e = (t == null || !includeSource) ? null : t.getStackTrace()[0];
    return new Log4jLogEvent(cname, null, cname, level, null, t, null, null, null, e, 0);
  }

  @Before
  public void before() {
    registry = new DefaultRegistry();
    appender = new SpectatorAppender(registry, "foo", null, null, false);
    appender.start();
  }

  @Test
  public void numMessagesERROR() {
    Counter c = registry.counter("log4j.numMessages", "appender", "foo", "loglevel", "2_ERROR");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.ERROR, null));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void numMessagesDEBUG() {
    Counter c = registry.counter("log4j.numMessages", "appender", "foo", "loglevel", "5_DEBUG");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, null));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void numStackTraces() {
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "SpectatorAppenderTest.java");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo")));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void numStackTracesNoSource() {
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "unknown");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo"), false));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void ignoreExceptions() {
    appender = new SpectatorAppender(registry, "foo", null, null, true);
    appender.start();
    Counter c = registry.counter("log4j.numStackTraces",
        "appender", "foo",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "SpectatorAppenderTest.java");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, new IllegalArgumentException("foo")));
    Assert.assertEquals(0, c.count());
  }

}
