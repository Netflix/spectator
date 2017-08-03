/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.netflix.spectator.api.Spectator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Properties;

@RunWith(JUnit4.class)
public class SpectatorAppenderTest {

  private Registry registry;
  private SpectatorAppender appender;

  private LoggingEvent newEvent(Level level, Throwable t) {
    final String cname = SpectatorAppenderTest.class.getName();
    final Logger logger = Logger.getLogger(cname);
    return new LoggingEvent(logger.getClass().getName(), logger, 0L, level, "foo", t);
  }

  @Before
  public void before() {
    registry = new DefaultRegistry();
    appender = new SpectatorAppender(registry);
  }

  @Test
  public void numMessagesERROR() {
    Counter c = registry.counter("log4j.numMessages", "loglevel", "2_ERROR");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.ERROR, null));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void numMessagesDEBUG() {
    Counter c = registry.counter("log4j.numMessages", "loglevel", "5_DEBUG");
    Assert.assertEquals(0, c.count());
    appender.append(newEvent(Level.DEBUG, null));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void numStackTraces() {
    Counter c = registry.counter("log4j.numStackTraces",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "?"); // Location is unknown because it is not called via logger
    Assert.assertEquals(0, c.count());
    Exception e = new IllegalArgumentException("foo");
    e.fillInStackTrace();
    appender.append(newEvent(Level.DEBUG, e));
    Assert.assertEquals(1, c.count());
  }

  @Test
  public void properties() {
    Spectator.globalRegistry().add(registry);

    Properties props = new Properties();
    props.setProperty("log4j.rootLogger", "ALL, A1");
    props.setProperty("log4j.appender.A1", "com.netflix.spectator.log4j.SpectatorAppender");
    PropertyConfigurator.configure(props);

    Counter c = registry.counter("log4j.numStackTraces",
        "loglevel", "5_DEBUG",
        "exception", "IllegalArgumentException",
        "file", "SpectatorAppenderTest.java");
    Assert.assertEquals(0, c.count());
    Exception e = new IllegalArgumentException("foo");
    e.fillInStackTrace();
    Logger.getLogger(getClass()).debug("foo", e);

    Assert.assertEquals(1, c.count());
  }
}
