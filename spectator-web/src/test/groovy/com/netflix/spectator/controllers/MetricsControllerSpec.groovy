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

package com.netflix.spectator.controllers

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.DefaultCounter
import com.netflix.spectator.api.DefaultTimer
import com.netflix.spectator.api.DefaultId
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Meter
import com.netflix.spectator.api.Measurement

import com.netflix.spectator.controllers.model.DataPoint
import com.netflix.spectator.controllers.filter.MeasurementFilter;
import com.netflix.spectator.controllers.filter.TagMeasurementFilter;
import com.netflix.spectator.controllers.model.MetricValuesMap
import com.netflix.spectator.controllers.model.MetricValues
import com.netflix.spectator.controllers.model.TagValue
import com.netflix.spectator.controllers.model.TaggedDataPoints
import spock.lang.Specification


class MetricsControllerSpec extends Specification {
  static class TestMeter implements Meter {
    Id myId
    Measurement[] myMeasurements
    boolean expired = false

    TestMeter(name, measures) {
      myId = new DefaultId(name)
      myMeasurements = measures
    }
    Id id() { return myId }
    Iterable<Measurement> measure() {
      return Arrays.asList(myMeasurements)
    }
    boolean hasExpired() { return expired }
  }

  MetricsController controller = new MetricsController()
  Id idA = new DefaultId("idA")
  Id idB = new DefaultId("idB")
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y")
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X")
  Id idAXZ = idA.withTag("tagA", "X").withTag("tagZ", "Z")
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y")

  Measurement measureAXY = new Measurement(idAXY, 11, 11.11)
  Measurement measureAXY2 = new Measurement(idAXY, 20, 20.20)
  Measurement measureAYX = new Measurement(idAYX, 12, 12.12)
  Measurement measureAXZ = new Measurement(idAXZ, 13, 13.13)
  Measurement measureBXY = new Measurement(idBXY, 50, 50.50)
  Measurement measureBXY2 = new Measurement(idBXY, 5, 5.5)

  MeasurementFilter allowAll = new MeasurementFilter() {
    public boolean keep(Meter meter, Measurement measurement) {
      return true;
    }
  };

  Meter meterA = new TestMeter("ignoreA", [measureAXY, measureAYX, measureAXZ])
  Meter meterA2 = new TestMeter("ignoreA", [measureAXY2])
  Meter meterB = new TestMeter("ignoreB", [measureBXY])
  Meter meterB2 = new TestMeter("ignoreB", [measureBXY2])

  long millis = 12345L
  Clock clock = new Clock() {
    long  wallTime() { return millis; }
    long monotonicTime() { return millis; }
  }

  HashMap<Id, List<Measurement>> collection

  void setup() {
    collection = new HashMap<Id, List<Measurement>>();
  }

  List<Measurement> toMeasurementList(array) {
    List<Measurement> list = Arrays.toList(array)
    return list
  }

  void "collectDisjointValues"() {
    when:
      List<Id> added = MetricsController.collectValues(
              collection, meterA, allowAll)

    then:
      collection ==  [(idAXY) : [measureAXY],
                      (idAYX) : [measureAYX],
                      (idAXZ) : [measureAXZ]]
  }

  void "collectRepeatedValues"() {
    when:
      MetricsController.collectValues(collection, meterA, allowAll)
      MetricsController.collectValues(collection, meterA2, allowAll)

    then:
      collection == [(idAXY) : [measureAXY, measureAXY2],
                     (idAYX) : [measureAYX],
                     (idAXZ) : [measureAXZ]]
  }

  void "collectSimilarMetrics"() {
    when:
      MetricsController.collectValues(collection, meterA, allowAll)
      MetricsController.collectValues(collection, meterB, allowAll)

    then:
      collection == [(idAXY) : [measureAXY],
                     (idBXY) : [measureBXY],
                     (idAYX) : [measureAYX],
                     (idAXZ) : [measureAXZ]]
  }

  void "collectFilteredName"() {
    given:
      MeasurementFilter filter = new TagMeasurementFilter("idA", null, null)

    when:
      MetricsController.collectValues(collection, meterA, filter)
      MetricsController.collectValues(collection, meterB, filter)

    then:
      collection == [(idAXY) : [measureAXY],
                     (idAYX) : [measureAYX],
                     (idAXZ) : [measureAXZ]]
  }

  void "collectFilteredTagName"() {
    given:
      MeasurementFilter filter = new TagMeasurementFilter(null, "tagZ", null)

    when:
      MetricsController.collectValues(collection, meterA, filter)

    then:
      collection == [(idAXZ) : [measureAXZ]]
  }

  void "collectFilteredTagValue"() {
    given:
      MeasurementFilter filter = new TagMeasurementFilter(null, null, "X")

    when:
      MetricsController.collectValues(collection, meterA, filter)

    then:
      collection == [(idAXY) : [measureAXY],
                     (idAYX) : [measureAYX],
                     (idAXZ) : [measureAXZ]]
  }

  void "collectNotFound"() {
    given:
      MeasurementFilter filter = new TagMeasurementFilter(null, "tagZ", "X")

    when:
      MetricsController.collectValues(collection, meterA, filter)

    then:
      collection == new HashMap<Id, List<Measurement>>()
  }

  void "meterToKind"() {
    given:
      String kind

    when:
      kind = MetricsController.meterToKind(new DefaultCounter(clock, idAXY))
    then:
      kind.equals("Counter")

    when:
      kind = MetricsController.meterToKind(new DefaultTimer(clock, idAXY))
    then:
      kind.equals("Timer")

    when:
      kind = MetricsController.meterToKind(meterA)
    then:
      kind.equals("MetricsControllerSpec\$TestMeter")
  }

  void "encodeSimpleRegistry"() {
    given:
      MetricValuesMap got
      MetricValuesMap expect
      DefaultRegistry registry = new DefaultRegistry(clock)
      Meter counterA = new DefaultCounter(clock, idAXY)
      Meter counterB = new DefaultCounter(clock, idBXY)
      counterA.increment(4)
      counterB.increment(10)

      registry.register(counterA)
      registry.register(counterB)

      List<TaggedDataPoints> expectedTaggedDataPointsA = [
          new TaggedDataPoints(
              [new TagValue("tagA", "X"),
               new TagValue("tagB", "Y")],
              [new DataPoint(millis, 4)])
      ]
      List<TaggedDataPoints> expectedTaggedDataPointsB = [
          new TaggedDataPoints(
              [new TagValue("tagA", "X"),
               new TagValue("tagB", "Y")],
              [new DataPoint(millis, 10)])
      ]


    when:
      got = controller.encodeRegistry(registry, allowAll)

    then:
      got == MetricValuesMap.make(
             ["idA" : new MetricValues("Counter", expectedTaggedDataPointsA),
              "idB" : new MetricValues("Counter", expectedTaggedDataPointsB)])
  }


  void "encodeCombinedRegistry"() {
    given:
      MetricValuesMap got
      MetricValuesMap expect
      DefaultRegistry registry = new DefaultRegistry(clock)
      registry.register(meterB)
      registry.register(meterB2)

      List<TaggedDataPoints> expectedTaggedDataPoints = [
          new TaggedDataPoints(
              [new TagValue("tagA", "X"),
               new TagValue("tagB", "Y")],
              [new DataPoint(50, 50.5 + 5.5)])
      ]

    when:
      got = controller.encodeRegistry(registry, allowAll)

    then:
      got == MetricValuesMap.make(
             ["idB" : new MetricValues("Counter", expectedTaggedDataPoints)])
  }


  void "encodeCompositeRegistry"() {
    given:
      MetricValuesMap got
      MetricValuesMap expect
      DefaultRegistry registry = new DefaultRegistry(clock)
      registry.register(meterA)
      registry.register(meterA2)

      List<TaggedDataPoints> expected_tagged_data_points = [
        new TaggedDataPoints(
              [new TagValue("tagA", "Y"),
               new TagValue("tagB", "X")],
              [new DataPoint(12, 12.12)]),
        new TaggedDataPoints(
              [new TagValue("tagA", "X"),
               new TagValue("tagB", "Y")],
               // This should be 20, but AggrMeter keeps first time,
               // which happens to be the 11th, not the most recent time.
              [new DataPoint(11, 11.11 + 20.20)]),
        new TaggedDataPoints(
              [new TagValue("tagA", "X"),
               new TagValue("tagZ", "Z")],
              [new DataPoint(13, 13.13)])
      ]

    when:
      got = controller.encodeRegistry(registry, allowAll)

    then:
      got.equals(MetricValuesMap.make(
                 ["idA" : new MetricValues("Counter", expected_tagged_data_points)]))
  }
}
