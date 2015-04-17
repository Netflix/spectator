package com.netflix.spectator.spark;

/**
 * Maps a value based on the name. This is typically used to perform simple unit conversions so
 * that data can be made to follow common conventions with other sources (e.g. always use base
 * units and do conversions as part of presentation).
 */
public interface ValueFunction {
  /** Convert the value for a given metric name. */
  double convert(String name, double v);
}
