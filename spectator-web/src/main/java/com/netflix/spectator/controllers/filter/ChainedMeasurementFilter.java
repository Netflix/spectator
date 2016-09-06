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

package com.netflix.spectator.controllers.filter;

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.controllers.filter.MeasurementFilter;

import java.util.ArrayList;
import java.util.List;


public class ChainedMeasurementFilter implements MeasurementFilter {
  private List<MeasurementFilter> filters = new ArrayList<MeasurementFilter>();

  public ChainedMeasurementFilter(MeasurementFilter a, MeasurementFilter b) {
    filters.add(a);
    filters.add(b);
  }

  public ChainedMeasurementFilter(ArrayList<MeasurementFilter> list) {
    filters.addAll(list);
  }

  public boolean keep(Meter meter, Measurement measurement) {
    for (MeasurementFilter filter : filters) {
      if (!filter.keep(meter, measurement)) return false;
    }
    return true;
  }
};