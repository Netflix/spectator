/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spectator.controllers.model;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class TestMeter implements Meter {
  private Id myId;
  private List<Measurement> myMeasurements;
  private boolean expired = false;

  public TestMeter(Id id) {
    myId = id;
    myMeasurements = new ArrayList<Measurement>();
  }

  public TestMeter(String name, Measurement measurement) {
    this(name, Arrays.asList(measurement));
  }

  public TestMeter(String name, List<Measurement> measures) {
    myId = new TestId(name);
    myMeasurements = measures;
  }
  public Id id() { return myId; }
  public Iterable<Measurement> measure() {
    return myMeasurements;
  }
  public boolean hasExpired() {
    return expired;
  }
};

