/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.atlas;

/**
 * Model object for an individual subscription coming from LWC.
 */
class Subscription {

  private String id;
  private String expression;
  private long frequency;

  /** Create a new instance. */
  Subscription() {
    // Will get filled in with set methods
  }

  /** Id for a subscription.  */
  public String getId() {
    return id;
  }

  /** Set the subscription id. */
  public void setId(String id) {
    this.id = id;
  }

  /** Expression for the subscription. */
  public String getExpression() {
    return expression;
  }

  /** Set the expression for the subscription. */
  public void setExpression(String expression) {
    this.expression = expression;
  }

  /** Requested frequency to send data for the subscription. */
  public long getFrequency() {
    return frequency;
  }

  /** Set the requested frequency to send data for the subscription. */
  public void setFrequency(long frequency) {
    this.frequency = frequency;
  }

  @Override public String toString() {
    return "Subscription(" + id + ",[" + expression + "]," + frequency + ")";
  }
}
