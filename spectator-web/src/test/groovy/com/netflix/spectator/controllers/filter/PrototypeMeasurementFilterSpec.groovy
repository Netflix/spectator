package com.netflix.spectator.controllers.filter

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Measurement
import com.netflix.spectator.api.Tag

import com.netflix.spectator.controllers.model.TestId
import com.netflix.spectator.controllers.model.TestMeter

import spock.lang.Specification

import java.util.regex.Pattern

/**
 * Created by ewiseblatt on 9/6/16.
 */
class PrototypeMeasurementFilterSpec extends Specification {
    long millis = 12345L
    Clock clock = new Clock() {
        long wallTime() { return millis; }

        long monotonicTime() { return millis; }
    }

    PrototypeMeasurementFilterSpecification spec
    ValueFilterSpecification valueSpecAxBy;
    ValueFilterSpecification valueSpecAyBx
    ValueFilterSpecification valueSpecAzBy;

    MeterFilterSpecification meterSpecA;
    MeterFilterSpecification meterSpecB;
    MeterFilterSpecification meterSpecC;
    MeterFilterSpecification meterSpecD;

    void setup() {
        ArrayList<TagFilterSpecification> tagsAxBy = [
                new TagFilterSpecification("tagA", "X"),
                new TagFilterSpecification("tagB", "Y")
        ]

        ArrayList<TagFilterSpecification> tagsAyBx = [
                new TagFilterSpecification("tagA", "Y"),
                new TagFilterSpecification("tagB", "X")
        ]

        ArrayList<TagFilterSpecification> tagsAzBy = [
                new TagFilterSpecification("tagA", "Z"),
                new TagFilterSpecification("tagB", "Y")
        ]

        valueSpecAxBy = new ValueFilterSpecification();
        valueSpecAxBy.tags.addAll(tagsAxBy)

        valueSpecAyBx = new ValueFilterSpecification();
        valueSpecAyBx.tags.addAll(tagsAyBx)

        valueSpecAzBy = new ValueFilterSpecification();
        valueSpecAzBy.tags.addAll(tagsAzBy)


        meterSpecA = new MeterFilterSpecification([valueSpecAxBy]);
        meterSpecB = new MeterFilterSpecification([valueSpecAyBx]);
        meterSpecC = new MeterFilterSpecification([valueSpecAzBy]);
        meterSpecD = new MeterFilterSpecification([valueSpecAxBy, valueSpecAyBx]);
    }

    void "Pattern from spec"() {
        given:
        List<PrototypeMeasurementFilter.TagFilterPattern> tagPatterns = [
                new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile("tagA"), Pattern.compile("X")),
                new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile("tagB"), Pattern.compile("Y"))]
        PrototypeMeasurementFilter.MeterFilterPattern meterPattern
        PrototypeMeasurementFilter.ValueFilterPattern valuePattern

        when:
        meterPattern = new PrototypeMeasurementFilter.MeterFilterPattern("meterA", meterSpecA)
        then:
        meterPattern != null
        meterPattern.values.size() == 1

        when:
        valuePattern = meterPattern.values.get(0)
        then:
        valuePattern.tags == tagPatterns
    }

    void "testMetricToPatterns"() {
        given:
        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification()
        spec.include.put("meterA", meterSpecA);
        spec.include.put(".+B", meterSpecB);
        spec.include.put(".+C.*", meterSpecC);
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec)
        PrototypeMeasurementFilter.IncludeExcludePatterns patterns

        PrototypeMeasurementFilter.MeterFilterPattern meterPatternA = new PrototypeMeasurementFilter.MeterFilterPattern("meterA", meterSpecA)
        PrototypeMeasurementFilter.MeterFilterPattern meterPatternB = new PrototypeMeasurementFilter.MeterFilterPattern(".+B", meterSpecB)
        PrototypeMeasurementFilter.MeterFilterPattern meterPatternC = new PrototypeMeasurementFilter.MeterFilterPattern(".+C.*", meterSpecC)

        when:
        patterns = filter.metricToPatterns("meterA")
        then:
        patterns.equals(
                new PrototypeMeasurementFilter.IncludeExcludePatterns(meterPatternA.values, []))

        when:
        patterns = filter.metricToPatterns("meterB")
        then:
        patterns.equals(
                new PrototypeMeasurementFilter.IncludeExcludePatterns(meterPatternB.values, []))
        when:
        patterns = filter.metricToPatterns("meterBefore")
        then:
        patterns.equals(new PrototypeMeasurementFilter.IncludeExcludePatterns())

        when:
        patterns = filter.metricToPatterns("meterCthing")
        then:
        patterns.equals(
                new PrototypeMeasurementFilter.IncludeExcludePatterns(meterPatternC.values, []))
    }

    void "testMetricToPatterns with multiple meters"() {
        given:
        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification()
        spec.include.put("meterA", meterSpecA);
        spec.include.put("meter.+", meterSpecB);
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec)
        PrototypeMeasurementFilter.IncludeExcludePatterns patterns

        PrototypeMeasurementFilter.MeterFilterPattern meterPatternA = new PrototypeMeasurementFilter.MeterFilterPattern("ignored", meterSpecA)
        PrototypeMeasurementFilter.MeterFilterPattern meterPatternB = new PrototypeMeasurementFilter.MeterFilterPattern("ignored", meterSpecB)

        when:
        patterns = filter.metricToPatterns("meterB")
        then:
        patterns.equals(
                new PrototypeMeasurementFilter.IncludeExcludePatterns(meterPatternB.values, []))

        when:
        patterns = filter.metricToPatterns("meterA")
        then:
        patterns.include as Set == (meterPatternA.values + meterPatternB.values) as Set
    }

    void "keep any tag"() {
        given:
        PrototypeMeasurementFilter.TagFilterPattern pattern = new PrototypeMeasurementFilter.TagFilterPattern(
                new TagFilterSpecification("", ""))
        Tag tagA = new Tag() {
            String key() { return "some_name_value"; }

            String value() { return "some_value_string"; }
        }

        expect:
        pattern.keep(tagA);
    }

    void "keepTag ok"() {
        given:
        PrototypeMeasurementFilter.TagFilterPattern pattern = new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile(".+_name_.+"), Pattern.compile(".+_value_.+"))
        Tag tagA = new Tag() {
            String key() { return "some_name_value"; }

            String value() { return "some_value_string"; }
        }
        expect:
        pattern.keep(tagA)
    }

    void "keepTag not ok"() {
        given:
        PrototypeMeasurementFilter.TagFilterPattern pattern = new PrototypeMeasurementFilter.TagFilterPattern(Pattern.compile(".+_name_.+"), Pattern.compile(".+_value_.+"))
        Tag tagOnlyNameOk = new Tag() {
            String key() { return "some_name_value"; }

            String value() { return "some_string"; }
        }
        Tag tagOnlyValueOk = new Tag() {
            String key() { return "some_value"; }

            String value() { return "some_value_string"; }
        }
        Tag tagNeitherOk = new Tag() {
            String key() { return "some_value"; }

            String value() { return "some_string"; }
        }

        expect:
        !pattern.keep(tagOnlyNameOk)
        !pattern.keep(tagOnlyValueOk)
        !pattern.keep(tagNeitherOk)
    }

    void "value tags ok"() {
        given:
        PrototypeMeasurementFilter.ValueFilterPattern pattern = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy)
        List<Tag> tagsAxBy = [new Tag() {
            public String key() { return "tagA"; }

            public String value() { return "X"; }
        },
                              new Tag() {
                                  public String key() { return "tagB"; }

                                  public String value() { return "Y"; }
                              }]
        List<Tag> tagsByAx = [tagsAxBy[1], tagsAxBy[0]]

        expect:
        pattern.keep(tagsAxBy);
        pattern.keep(tagsByAx);
    }

    void "extra value tags ok"() {
        given:
        PrototypeMeasurementFilter.ValueFilterPattern pattern = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy)
        List<Tag> tagsAxBy = [new Tag() {
            public String key() { return "tagX"; }

            public String value() { return "X"; }
        },
                              new Tag() {
                                  public String key() { return "tagA"; }

                                  public String value() { return "X"; }
                              },
                              new Tag() {
                                  public String key() { return "tagC"; }

                                  public String value() { return "C"; }
                              },
                              new Tag() {
                                  public String key() { return "tagB"; }

                                  public String value() { return "Y"; }
                              }]
        List<Tag> tagsByAx = [tagsAxBy[3], tagsAxBy[2], tagsAxBy[1], tagsAxBy[0]]

        expect:
        pattern.keep(tagsAxBy);
        pattern.keep(tagsByAx);
    }

    void "value tags missing"() {
        given:
        PrototypeMeasurementFilter.ValueFilterPattern pattern = new PrototypeMeasurementFilter.ValueFilterPattern(valueSpecAxBy)
        List<Tag> tagsAx = [new Tag() {
            String key() { return "tagA"; }

            String value() { return "X"; }
        }]
        List<Tag> tagsAxZy = [new Tag() {
            String key() { return "tagA"; }

            String value() { return "X"; }
        },
                              new Tag() {
                                  String key() { return "tagZ"; }

                                  String value() { return "Y"; }
                              }]
        List<Tag> tagsAyBy = [new Tag() {
            String key() { return "tagA"; }

            String value() { return "Y"; }
        },
                              new Tag() {
                                  String key() { return "tagB"; }

                                  String value() { return "Y"; }
                              }]

        expect:
        !pattern.keep(tagsAx);
        !pattern.keep(tagsAxZy);
        !pattern.keep(tagsAyBy);
    }

    void "meter ok"() {
        given:
        PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpec = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification([valueSpecAyBx, valueSpecAzBy])

        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification()
        spec.include.put("counter.+", meterSpec)

        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec)

        TestId idAYX = new TestId("counterA", []).withTag("tagA", "Y").withTag("tagB", "X")
        TestId idBZY = new TestId("counterB", []).withTag("tagA", "Z").withTag("tagB", "Y")

        TestMeter meterA = new TestMeter(idAYX)
        TestMeter meterB = new TestMeter(idBZY)

        expect:
        filter.keep(meterA, new Measurement(idAYX, 1, 1))
        filter.keep(meterB, new Measurement(idBZY, 2, 2))
    }

    void "meters excluded"() {
        given:
        PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpec = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification([valueSpecAyBx, valueSpecAzBy])

        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification()
        spec.include.put(
                "counter.+",
                new PrototypeMeasurementFilterSpecification.MeterFilterSpecification())
        spec.exclude.put(
                "counterC",
                new PrototypeMeasurementFilterSpecification.MeterFilterSpecification())
        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec)

        TestId idAYX = new TestId("counterA", []).withTag("tagA", "Y").withTag("tagB", "X")
        TestId idCYX = new TestId("counterC", []).withTag("tagA", "Y").withTag("tagB", "X")

        TestMeter meterA = new TestMeter(idAYX)
        TestMeter meterC = new TestMeter(idCYX)

        expect:
        filter.keep(meterA, new Measurement(idAYX, 1, 1))
        !filter.keep(meterC, new Measurement(idCYX, 2, 2))
    }

    void "meter not ok because not included"() {
        given:
        PrototypeMeasurementFilterSpecification.MeterFilterSpecification meterSpec = new PrototypeMeasurementFilterSpecification.MeterFilterSpecification([valueSpecAyBx, valueSpecAzBy])

        PrototypeMeasurementFilterSpecification spec = new PrototypeMeasurementFilterSpecification()
        spec.include.put("counter.+", meterSpec)

        PrototypeMeasurementFilter filter = new PrototypeMeasurementFilter(spec)

        TestId idAXX = new TestId("counterA", []).withTag("tagA", "X").withTag("tagB", "X")
        TestId idBZX = new TestId("counterB", []).withTag("tagA", "Z").withTag("tagB", "X")

        TestMeter meterA = new TestMeter(idAXX)
        TestMeter meterB = new TestMeter(idBZX)

        expect:
        !filter.keep(meterA, new Measurement(idAXX, 1, 1))
        !filter.keep(meterB, new Measurement(idBZX, 2, 2))
    }

    void "loadFromJson"() {
        given:
        String path = getClass().getResource("/test_measurement_filter.json").getFile()
        PrototypeMeasurementFilterSpecification spec = PrototypeMeasurementFilterSpecification.loadFromPath(path);
        PrototypeMeasurementFilterSpecification specA = new PrototypeMeasurementFilterSpecification();
        specA.include.put("meterA", meterSpecA);
        specA.include.put("meterD", meterSpecD);
        specA.include.put("empty", new MeterFilterSpecification([]));

        ArrayList<TagFilterSpecification> tagsX = [
                new TagFilterSpecification("tagA", "X"),
                new TagFilterSpecification("tagX", ".*")
        ]
        ValueFilterSpecification valueSpecX = new ValueFilterSpecification()
        valueSpecX.tags.addAll(tagsX)
        specA.exclude.put(".+", new MeterFilterSpecification([valueSpecX]))

        expect:
        spec.equals(specA)
    }
}


class MeterFilterSpecification extends PrototypeMeasurementFilterSpecification.MeterFilterSpecification {
  MeterFilterSpecification(List<ValueFilterSpecification> values) {
    super(values)
  }
}

class ValueFilterSpecification extends PrototypeMeasurementFilterSpecification.ValueFilterSpecification {
}

class TagFilterSpecification extends PrototypeMeasurementFilterSpecification.TagFilterSpecification {
  TagFilterSpecification(String key, String value) {
    super(key, value)
  }
}