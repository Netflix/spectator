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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class TagFilterTest {
  Id idA = Id.create("idA");
  Id idB = Id.create("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAXZ = idA.withTag("tagA", "X").withTag("tagZ", "Z");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Measurement measureAXY = new Measurement(idAXY, 11, 11.11);
  Measurement measureAXZ = new Measurement(idAXZ, 13, 13.13);
  Measurement measureBXY = new Measurement(idBXY, 50, 50.50);

  @Test
  public void testFilteredName() {
    Predicate<Measurement> filter = new TagMeasurementFilter("idA", null, null);
    Assertions.assertTrue(filter.test(measureAXY));
    Assertions.assertFalse(filter.test(measureBXY));
  }

  @Test
  public void collectFilteredTagName() {
    Predicate<Measurement> filter = new TagMeasurementFilter(null, "tagZ", null);
    Assertions.assertTrue(filter.test(measureAXZ));
    Assertions.assertFalse(filter.test(measureAXY));
  }

  @Test
  public void collectFilteredTagValue() {
    Predicate<Measurement> filter = new TagMeasurementFilter(null, null, "Z");
    Assertions.assertTrue(filter.test(measureAXZ));
    Assertions.assertFalse(filter.test(measureAXY));
  }
}
