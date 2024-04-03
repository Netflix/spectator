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
package com.netflix.spectator.atlas.impl;

/**
 * Model object for an individual subscription coming from LWC.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class Subscription {

  private String id;
  private String exprType;
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

  /** Return true if it is a time series expression. */
  public boolean isTimeSeries() {
    // Null is for legacy endpoints that do not indicate the type.
    return exprType == null || "TIME_SERIES".equals(exprType);
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

  /** Expression type for the subscription. */
  public String getExprType() {
    return exprType;
  }

  /** Set the expression for the subscription. */
  public void setExprType(String exprType) {
    this.exprType = exprType;
  }

  /** Set the expression for the subscription. */
  public Subscription withExprType(String exprType) {
    setExprType(exprType);
    return this;
  }

  /** Expression for the subscription. */
  public String getExpression() {
    return expression;
  }

  /** Set the expression for the subscription. */
  public void setExpression(String expression) {
    this.expression = expression;
    this.expr = null;
  }

  /** Set the expression for the subscription. */
  public Subscription withExpression(String expression) {
    setExpression(expression);
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
        && equalsOrNull(id, that.id)
        && equalsOrNull(exprType, that.exprType)
        && equalsOrNull(expression, that.expression);
  }

  private boolean equalsOrNull(Object a, Object b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  @Override public int hashCode() {
    int result = hashCodeOrZero(id);
    result = 31 * result + hashCodeOrZero(exprType);
    result = 31 * result + hashCodeOrZero(expression);
    result = 31 * result + (int) (frequency ^ (frequency >>> 32));
    return result;
  }

  private int hashCodeOrZero(Object o) {
    return (o == null) ? 0 : o.hashCode();
  }

  @Override public String toString() {
    return "Subscription(" + id + "," + exprType + ",[" + expression + "]," + frequency + ")";
  }
}
