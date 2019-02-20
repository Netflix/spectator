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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * Appender that tracks the number of messages that pass through.
 */
public final class SpectatorAppender extends AppenderSkeleton {

  private final Registry registry;
  private final Id[] numMessages;
  private final Id[] numStackTraces;

  /** Create a new instance of the appender. */
  public SpectatorAppender() {
    this(Spectator.globalRegistry());
  }

  /** Create a new instance of the appender. */
  public SpectatorAppender(Registry registry) {
    super();
    this.registry = registry;

    final LevelTag[] levels = LevelTag.values();
    numMessages = new Id[levels.length];
    numStackTraces = new Id[levels.length];
    for (int i = 0; i < levels.length; ++i) {
      numMessages[i] = registry.createId("log4j.numMessages")
          .withTag(levels[i]);
      numStackTraces[i] = registry.createId("log4j.numStackTraces")
          .withTag(levels[i]);
    }
  }

  @Override protected void append(LoggingEvent event) {
    final LevelTag level = LevelTag.get(event.getLevel());
    registry.counter(numMessages[level.ordinal()]).increment();

    ThrowableInformation info = event.getThrowableInformation();
    if (info != null) {
      LocationInfo loc = event.getLocationInformation();
      final String file = (loc == null) ? "unknown" : loc.getFileName();
      Id stackTraceId = numStackTraces[level.ordinal()]
          .withTag("exception", info.getThrowable().getClass().getSimpleName())
          .withTag("file", file);
      registry.counter(stackTraceId).increment();
    }
  }

  @Override public void close() {
  }

  @Override public boolean requiresLayout() {
    return false;
  }
}
