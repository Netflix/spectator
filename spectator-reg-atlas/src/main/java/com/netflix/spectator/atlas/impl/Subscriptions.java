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
public class Subscriptions {

  private List<Subscription> expressions;

  /** Create a new instance. */
  public Subscriptions() {
    // Will get filled in with set methods
  }

  /** Returns the subscriptions with validated expressions. */
  public List<Subscription> validated() {
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
}
