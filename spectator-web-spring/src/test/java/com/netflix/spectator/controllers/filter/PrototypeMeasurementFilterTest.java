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


import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;

import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class PrototypeMeasurementFilterTest {
    private long millis = 12345L;

    PrototypeMeasurementFilterSpecification.ValueFilterSpecification valueSpecAxBy;
    PrototypeMeasurementFilterSpecification.ValueFilterSpecification valueSpecAyBx;
    PrototypeMeasurementFilterSpecification.ValueFilterSpecification valueSpecAzBy;

    PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpecA;
    PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpecB;
    PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpecC;
    PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpecD;

    @BeforeEach
    public void setup() {
        List<PrototypeMeasurementFilterSpecification.TagFilterSpecification> tagsAxBy = Arrays.asList(
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagA", "X"),
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagB", "Y"));

        List<PrototypeMeasurementFilterSpecification.TagFilterSpecification> tagsAyBx = Arrays.asList(
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagA", "Y"),
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagB", "X"));

        List<PrototypeMeasurementFilterSpecification.TagFilterSpecification> tagsAzBy = Arrays.asList(
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagA", "Z"),
            new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagB", "Y"));

        valueSpecAxBy = new PrototypeMeasurementFilterSpecification.ValueFilterSpecification();
        valueSpecAxBy.getTags().addAll(tagsAxBy);

        valueSpecAyBx = new PrototypeMeasurementFilterSpecification.ValueFilterSpecification();
        valueSpecAyBx.getTags().addAll(tagsAyBx);

        valueSpecAzBy = new PrototypeMeasurementFilterSpecification.ValueFilterSpecification();
        valueSpecAzBy.getTags().addAll(tagsAzBy);

        meterSpecA = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
            Collections.singletonList(valueSpecAxBy));
        meterSpecB = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
            Collections.singletonList(valueSpecAyBx));
        meterSpecC = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
            Collections.singletonList(valueSpecAzBy));
        meterSpecD = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
            Arrays.asList(valueSpecAxBy, valueSpecAyBx));
    }

    @Test
    public void testPatternFromSpec() {
        List<PrototypeMeasurementFilter.TagFilterPattern> tagPatterns = Arrays.asList(
            new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile("tagA"), Pattern.compile("X")),
            new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile("tagB"), Pattern.compile("Y")));
        PrototypeMeasurementFilter.MeterFilterPattern meterPattern
            = new PrototypeMeasurementFilter.MeterFilterPattern("meterA", meterSpecA);

        Assertions.assertEquals(meterPattern.getValues().size(), 1);
        Assertions.assertEquals(meterPattern.getValues().get(0).getTags(), tagPatterns);
    }

    @Test
    public void testMetricToPatterns() {
        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification();
        spec.getInclude().put("meterA", meterSpecA);
        spec.getInclude().put(".+B", meterSpecB);
        spec.getInclude().put(".+C.*", meterSpecC);
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec);

        PrototypeMeasurementFilter.MeterFilterPattern meterPatternA
            = new PrototypeMeasurementFilter.MeterFilterPattern("meterA", meterSpecA);
        PrototypeMeasurementFilter.MeterFilterPattern meterPatternC
            = new PrototypeMeasurementFilter.MeterFilterPattern(".+C.*", meterSpecC);

        final List<PrototypeMeasurementFilter.ValueFilterPattern> emptyList = new ArrayList<>();

        Assertions.assertEquals(
            filter.metricToPatterns("meterA"), 
            new PrototypeMeasurementFilter.IncludeExcludePatterns(
                    meterPatternA.getValues(), emptyList));

        Assertions.assertEquals(
            filter.metricToPatterns("meterBextra"),
            new PrototypeMeasurementFilter.IncludeExcludePatterns(
                    emptyList, emptyList));

        Assertions.assertEquals(
            filter.metricToPatterns("meterCthing"),
            new PrototypeMeasurementFilter.IncludeExcludePatterns(
                    meterPatternC.getValues(), emptyList));
    }

    @Test
    public void testMetricToPatternsWithMultipleMeters() {
        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification();
        spec.getInclude().put("meterA", meterSpecA);
        spec.getInclude().put("meter.+", meterSpecB);
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec);

        PrototypeMeasurementFilter.MeterFilterPattern meterPatternA
            = new PrototypeMeasurementFilter.MeterFilterPattern("ignored", meterSpecA);
        PrototypeMeasurementFilter.MeterFilterPattern meterPatternB
            = new PrototypeMeasurementFilter.MeterFilterPattern("ignored", meterSpecB);
        final List<PrototypeMeasurementFilter.ValueFilterPattern> emptyList = new ArrayList<>();
        Assertions.assertEquals(
            filter.metricToPatterns("meterB"), 
            new PrototypeMeasurementFilter.IncludeExcludePatterns(
                    meterPatternB.getValues(), emptyList));

        List<PrototypeMeasurementFilter.ValueFilterPattern> expect = new ArrayList<>();
        expect.addAll(meterPatternA.getValues());
        expect.addAll(meterPatternB.getValues());

        PrototypeMeasurementFilter.IncludeExcludePatterns patterns
            = filter.metricToPatterns("meterA");

        Assertions.assertEquals(new HashSet<>(expect), new HashSet<>(patterns.getInclude()));
    }


    @Test
    public void keepAnyTag() {
        PrototypeMeasurementFilter.TagFilterPattern pattern
            = new PrototypeMeasurementFilter.TagFilterPattern(
                      new PrototypeMeasurementFilterSpecification.TagFilterSpecification("", ""));
        Tag tagA = new BasicTag("some_name_value", "some_value_string");
        Assertions.assertTrue(pattern.test(tagA));
    }

    @Test
    public void keepTagOk() {
        PrototypeMeasurementFilter.TagFilterPattern pattern
            = new PrototypeMeasurementFilter.TagFilterPattern(
                      Pattern.compile(".+_name_.+"), Pattern.compile(".+_value_.+"));
        Tag tagA = new BasicTag("some_name_value", "some_value_string");
        Assertions.assertTrue(pattern.test(tagA));
    }

    @Test
    public void keepTagNotOk() {
        PrototypeMeasurementFilter.TagFilterPattern pattern
            = new PrototypeMeasurementFilter.TagFilterPattern(
                      Pattern.compile(".+_name_.+"), Pattern.compile(".+_value_.+"));
        Tag tagOnlyNameOk = new BasicTag("some_name_value", "some_string");
        Tag tagOnlyValueOk = new BasicTag("some_value", "some_value_string");
        Tag tagNeitherOk = new BasicTag("some_value", "some_string");

        Assertions.assertFalse(pattern.test(tagOnlyNameOk));
        Assertions.assertFalse(pattern.test(tagOnlyValueOk));
        Assertions.assertFalse(pattern.test(tagNeitherOk));
    }

    @Test
    public void valueTagsOk() {
        PrototypeMeasurementFilter.ValueFilterPattern pattern
            = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy);
        List<Tag> tagsAxBy = Arrays.asList(new BasicTag("tagA", "X"),
                                           new BasicTag("tagB", "Y"));
        List<Tag> tagsByAx = Arrays.asList(tagsAxBy.get(1), tagsAxBy.get(0));
        Assertions.assertTrue(pattern.test(tagsAxBy));
        Assertions.assertTrue(pattern.test(tagsByAx));
    }

    @Test
    public void extraValueTagsOk() {
        PrototypeMeasurementFilter.ValueFilterPattern pattern
            = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy);
        List<Tag> tagsAxBy = Arrays.asList(new BasicTag("tagX", "X"),
                                           new BasicTag("tagA", "X"),
                                           new BasicTag("tagC", "C"),
                                           new BasicTag("tagB", "Y"));
        List<Tag> tagsByAx
            = Arrays.asList(tagsAxBy.get(3), tagsAxBy.get(2),
                            tagsAxBy.get(1), tagsAxBy.get(0));
        Assertions.assertTrue(pattern.test(tagsAxBy));
        Assertions.assertTrue(pattern.test(tagsByAx));
    }

    @Test
    public void valueTagsMissing() {
        PrototypeMeasurementFilter.ValueFilterPattern pattern
            = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy);
        List<Tag> tagsAx = Collections.singletonList(new BasicTag("tagA", "X"));
        List<Tag> tagsAxZy = Arrays.asList(new BasicTag("tagA", "X"),
                                           new BasicTag("tagZ","Y"));
        List<Tag> tagsAyBy = Arrays.asList(new BasicTag("tagA", "Y"),
                                           new BasicTag("tagB", "Y"));

        Assertions.assertFalse(pattern.test(tagsAx));
        Assertions.assertFalse(pattern.test(tagsAxZy));
        Assertions.assertFalse(pattern.test(tagsAyBy));
    }

    @Test
    public void meterOk() {
        PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpec
            = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
                     Arrays.asList(valueSpecAyBx, valueSpecAzBy));

        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification();
        spec.getInclude().put("counter.+", meterSpec);

        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec);

        Id idAYX = new TestId("counterA").withTag("tagA", "Y").withTag("tagB", "X");
        Id idBZY = new TestId("counterB").withTag("tagA", "Z").withTag("tagB", "Y");

        Assertions.assertTrue(filter.test(new Measurement(idAYX, 1, 1)));
        Assertions.assertTrue(filter.test(new Measurement(idBZY, 2, 2)));
    }

    @Test
    public void metersExcluded() {
        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification();
        spec.getInclude().put(
                "counter.+",
                new PrototypeMeasurementFilterSpecification.MeterFilterSpecification());
        spec.getExclude().put(
                "counterC",
                new PrototypeMeasurementFilterSpecification.MeterFilterSpecification());
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec);

        Id idAYX = new TestId("counterA").withTag("tagA", "Y").withTag("tagB", "X");
        Id idCYX = new TestId("counterC").withTag("tagA", "Y").withTag("tagB", "X");

        Assertions.assertTrue(filter.test(new Measurement(idAYX, 1, 1)));
        Assertions.assertFalse(filter.test(new Measurement(idCYX, 2, 2)));
    }

    @Test
    public void meterNotOkBecauseNotIncluded() {
        PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpec
            = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
                     Arrays.asList(valueSpecAyBx, valueSpecAzBy));

        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification();
        spec.getInclude().put("counter.+", meterSpec);

        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec);

        Id idAXX = new TestId("counterA").withTag("tagA", "X").withTag("tagB", "X");
        Id idBZX = new TestId("counterB").withTag("tagA", "Z").withTag("tagB", "X");

        Assertions.assertFalse(filter.test(new Measurement(idAXX, 1, 1)));
        Assertions.assertFalse(filter.test(new Measurement(idBZX, 2, 2)));
    }

    @Test
    public void loadFromJson() throws IOException {
        String path = getClass().getResource("/test_measurement_filter.json").getFile();
        PrototypeMeasurementFilterSpecification spec
            = PrototypeMeasurementFilterSpecification.loadFromPath(path);
        PrototypeMeasurementFilterSpecification specA
            = new PrototypeMeasurementFilterSpecification();
        specA.getInclude().put("meterA", meterSpecA);
        specA.getInclude().put("meterD", meterSpecD);
        specA.getInclude().put(
             "empty",
             new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
                     new ArrayList<>()));

        List<PrototypeMeasurementFilterSpecification.TagFilterSpecification> tagsX = Arrays.asList(
                new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagA", "X"),
                new PrototypeMeasurementFilterSpecification.TagFilterSpecification("tagX", ".*"));
        PrototypeMeasurementFilterSpecification.ValueFilterSpecification valueSpecX
            = new PrototypeMeasurementFilterSpecification.ValueFilterSpecification();
        valueSpecX.getTags().addAll(tagsX);
        specA.getExclude().put(
               ".+",
               new PrototypeMeasurementFilterSpecification.MeterFilterSpecification(
                      Collections.singletonList(valueSpecX)));

        Assertions.assertEquals(spec, specA);
    }
}
