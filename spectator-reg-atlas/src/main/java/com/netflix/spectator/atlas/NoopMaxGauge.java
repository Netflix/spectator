package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;

import java.util.Collections;

/**
 * <p><b>Experimental:</b> This type may be removed in a future release.</p>
 *
 * NOOP implementation of max gauge.
 */
enum NoopMaxGauge implements MaxGauge {

  /** Singleton instance. */
  INSTANCE;

  private static final Id ID = new NoopRegistry().createId("NoopMaxGauge");

  @Override public double value() {
    return Double.NaN;
  }

  @Override public Id id() {
    return ID;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override public boolean hasExpired() {
    return true;
  }
}
