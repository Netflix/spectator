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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Model object for subscriptions payload coming from LWC service.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class Subscriptions {

  private static final Logger LOGGER = LoggerFactory.getLogger(Subscriptions.class);

  private List<Subscription> expressions = Collections.emptyList();

  /** Create a new instance. */
  public Subscriptions() {
    // Will get filled in with set methods
  }

  /**
   * Merge the subscriptions from this update into a map from subscriptions to
   * expiration times.
   *
   * @param subs
   *     Existing subscriptions. The map value is the expiration time in millis since
   *     the epoch.
   * @param currentTime
   *     Current time to use for checking if entries are expired.
   * @param expirationTime
   *     Expiration time used for new and updated entries.
   */
  public void update(Map<Subscription, Long> subs, long currentTime, long expirationTime) {
    // Update expiration time for existing subs and log new ones
    for (Subscription sub : expressions) {
      if (!subs.containsKey(sub)) {
        LOGGER.info("new subscription: {}", sub);
      }
      subs.put(sub, expirationTime);
    }

    // Remove any expired entries
    Iterator<Map.Entry<Subscription, Long>> it = subs.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Subscription, Long> entry = it.next();
      if (entry.getValue() < currentTime) {
        LOGGER.info("expired: {}", entry.getKey());
        it.remove();
      }
    }
  }

  /** Return the available subscriptions. */
  public List<Subscription> getExpressions() {
    return expressions;
  }

  /** Set the available subscriptions. */
  public void setExpressions(List<Subscription> expressions) {
    this.expressions = Preconditions.checkNotNull(expressions, "expressions");
  }

  /** Set the available subscriptions. */
  public Subscriptions withExpressions(List<Subscription> expressions) {
    setExpressions(expressions);
    return this;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Subscriptions that = (Subscriptions) o;
    return expressions.equals(that.expressions);
  }

  @Override public int hashCode() {
    return expressions.hashCode();
  }

  @Override public String toString() {
    return "Subscriptions(" + expressions + ")";
  }
}
