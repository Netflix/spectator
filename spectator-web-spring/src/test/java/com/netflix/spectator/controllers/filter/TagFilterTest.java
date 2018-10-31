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

import com.netflix.spectator.controllers.model.TestId;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.function.Predicate;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class TagFilterTest {
  Id idA = new TestId("idA");
  Id idB = new TestId("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X");
  Id idAXZ = idA.withTag("tagA", "X").withTag("tagZ", "Z");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Measurement measureAXY = new Measurement(idAXY, 11, 11.11);
  Measurement measureAYX = new Measurement(idAYX, 12, 12.12);
  Measurement measureAXZ = new Measurement(idAXZ, 13, 13.13);
  Measurement measureBXY = new Measurement(idBXY, 50, 50.50);

  @Test
  public void testFilteredName() {
    Predicate<Measurement> filter = new TagMeasurementFilter("idA", null, null);
    Assert.assertTrue(filter.test(measureAXY));
    Assert.assertFalse(filter.test(measureBXY));
  }

  @Test
  public void collectFilteredTagName() {
    Predicate<Measurement> filter = new TagMeasurementFilter(null, "tagZ", null);
    Assert.assertTrue(filter.test(measureAXZ));
    Assert.assertFalse(filter.test(measureAXY));
  }

  @Test
  public void collectFilteredTagValue() {
    Predicate<Measurement> filter = new TagMeasurementFilter(null, null, "Z");
    Assert.assertTrue(filter.test(measureAXZ));
    Assert.assertFalse(filter.test(measureAXY));
  }
}
