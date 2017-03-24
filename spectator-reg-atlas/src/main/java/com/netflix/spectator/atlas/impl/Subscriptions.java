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

import java.util.List;

/**
 * Model object for subscriptions payload coming from LWC service.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class Subscriptions {

  private List<Subscription> expressions;

  /** Create a new instance. */
  public Subscriptions() {
    // Will get filled in with set methods
  }

  /** Returns the subscriptions with validated expressions. */
  public List<Subscription> validated() {
    // Get the data expression to force parsing and ensure the string
    // from the payload is valid.
    expressions.forEach(Subscription::dataExpr);
    return expressions;
  }

  /** Return the available subscriptions. */
  public List<Subscription> getExpressions() {
    return expressions;
  }

  /** Set the available subscriptions. */
  public void setExpressions(List<Subscription> expressions) {
    this.expressions = expressions;
  }

  /** Set the available subscriptions. */
  public Subscriptions withExpressions(List<Subscription> expressions) {
    this.expressions = expressions;
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
