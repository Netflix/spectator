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

import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Tag;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;

/**
 * Appender that tracks the number of messages that pass through. If the {@code ignoreExceptions}
 * option is set to false, a more detailed counter for the number of stack traces with the
 * exception types and file will also get tracked.
 */
@Plugin(name = "Spectator", category = "Core", elementType = "appender", printObject = true)
public final class SpectatorAppender extends AbstractAppender {

  private static final long serialVersionUID = 42L;

  private final transient ExtendedRegistry registry;
  private final transient Id numMessages;
  private final transient Id numStackTraces;

  /** Create a new instance of the appender. */
  SpectatorAppender(
      ExtendedRegistry registry,
      String name,
      Filter filter,
      Layout<? extends Serializable> layout,
      boolean ignoreExceptions) {
    super(name, filter, layout, ignoreExceptions);
    this.registry = registry;
    numMessages = registry.createId("log4j.numMessages").withTag("appender", name);
    numStackTraces = registry.createId("log4j.numStackTraces").withTag("appender", name);
  }

  /** Create a new instance of the appender using the global spectator registry. */
  @PluginFactory
  public static SpectatorAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
      @PluginElement("Layout") Layout<? extends Serializable> layout,
      @PluginElement("Filters") Filter filter) {

    if (name == null) {
      LOGGER.error("no name provided for SpectatorAppender");
      return null;
    }

    return new SpectatorAppender(Spectator.registry(), name, filter, layout, ignoreExceptions);
  }

  @Override public void append(LogEvent event) {
    final Tag level = LevelTag.get(event.getLevel());
    registry.counter(numMessages.withTag(level)).increment();
    if (!ignoreExceptions() && event.getThrown() != null) {
      final String file = (event.getSource() == null) ? "unknown" : event.getSource().getFileName();
      Id stackTraceId = numStackTraces
          .withTag(level)
          .withTag("exception", event.getThrown().getClass().getSimpleName())
          .withTag("file", file);
      registry.counter(stackTraceId).increment();
    }
  }
}
