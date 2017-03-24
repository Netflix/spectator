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

/**
 * Model object for an individual subscription coming from LWC.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class Subscription {

  private String id;
  private String expression;
  private long frequency;

  private DataExpr expr;

  /** Create a new instance. */
  public Subscription() {
    // Will get filled in with set methods
  }

  /** Return the data expression for this subscription. */
  public DataExpr dataExpr() {
    if (expr == null) {
      expr = Parser.parseDataExpr(expression);
    }
    return expr;
  }

  /** Id for a subscription.  */
  public String getId() {
    return id;
  }

  /** Set the subscription id. */
  public void setId(String id) {
    this.id = id;
  }

  /** Set the subscription id. */
  public Subscription withId(String id) {
    this.id = id;
    return this;
  }

  /** Expression for the subscription. */
  public String getExpression() {
    return expression;
  }

  /** Set the expression for the subscription. */
  public void setExpression(String expression) {
    this.expression = expression;
  }

  /** Set the expression for the subscription. */
  public Subscription withExpression(String expression) {
    this.expression = expression;
    return this;
  }

  /** Requested frequency to send data for the subscription. */
  public long getFrequency() {
    return frequency;
  }

  /** Set the requested frequency to send data for the subscription. */
  public void setFrequency(long frequency) {
    this.frequency = frequency;
  }

  /** Set the requested frequency to send data for the subscription. */
  public Subscription withFrequency(long frequency) {
    this.frequency = frequency;
    return this;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Subscription that = (Subscription) o;
    return frequency == that.frequency
        && id.equals(that.id)
        && expression.equals(that.expression)
        && expr.equals(that.expr);
  }

  @Override public int hashCode() {
    int result = id.hashCode();
    result = 31 * result + expression.hashCode();
    result = 31 * result + (int) (frequency ^ (frequency >>> 32));
    result = 31 * result + expr.hashCode();
    return result;
  }

  @Override public String toString() {
    return "Subscription(" + id + ",[" + expression + "]," + frequency + ")";
  }
}
