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
import com.netflix.spectator.api.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of DataPoint instances for a comment set of TagValues.
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class TaggedDataPoints {
  public static TaggedDataPoints make(Id id, List<Measurement> measurements,
                                      boolean aggregate) {
    List<TagValue> tags = new ArrayList<TagValue>();
    List<DataPoint> dataPoints = new ArrayList<DataPoint>();
    for (Tag tag : id.tags()) {
        tags.add(new TagValue(tag.key(), tag.value()));
    }
    if (aggregate) {
      DataPoint point = null;
      for (Measurement measurement : measurements) {
         if (point == null) {
           point = DataPoint.make(measurement);
           dataPoints.add(point);
         } else {
           point.aggregate(measurement);
         }
      }
    } else {
      for (Measurement measurement : measurements) {
        dataPoints.add(DataPoint.make(measurement));
      }
    }
    return new TaggedDataPoints(tags, dataPoints);
  }

  public Iterable<TagValue> getTags() { return tags; }
  public Iterable<DataPoint> getValues() { return dataPoints; }

  public TaggedDataPoints(List<TagValue> tags, List<DataPoint> dataPoints) {
    this.tags = tags;
    this.dataPoints = dataPoints;
  }

  @Override
  public String toString() {
      return String.format("{TAGS={%s} DATA={%s}", tags, dataPoints);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TaggedDataPoints)) return false;
    TaggedDataPoints other = (TaggedDataPoints)obj;
    return tags.equals(other.tags) && dataPoints.equals(other.dataPoints);
  }

  private List<TagValue> tags;
  private List<DataPoint> dataPoints;
}
