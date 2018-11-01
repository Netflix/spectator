package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Clock;

/**
 * Wraps a Micrometer Clock to make it conform to the Spectator API.
 */
class MicrometerClock implements Clock {

  private final io.micrometer.core.instrument.Clock impl;

  /** Create a new instance. */
  MicrometerClock(io.micrometer.core.instrument.Clock impl) {
    this.impl = impl;
  }

  @Override public long wallTime() {
    return impl.wallTime();
  }

  @Override public long monotonicTime() {
    return impl.monotonicTime();
  }
}
