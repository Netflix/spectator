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
import com.netflix.spectator.api.Tag;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class PrototypeMeasurementFilter implements MeasurementFilter {
  public static class TagFilterPattern {
    public TagFilterPattern(Pattern key, Pattern value) {
     this.key = key;
     this.value = value;
    }

    public TagFilterPattern(PrototypeMeasurementFilterSpecification.TagFilterSpecification spec) {
      if (spec == null) return;

      if (spec.key != null && !spec.key.isEmpty() && !spec.key.equals(".*")) {
        key = Pattern.compile(spec.key);
      }
      if (spec.value != null && !spec.value.isEmpty() && !spec.value.equals(".*")) {
        value = Pattern.compile(spec.value);
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof TagFilterPattern)) return false;
      TagFilterPattern other = (TagFilterPattern)obj;
      return key.pattern().equals(other.key.pattern()) && value.pattern().equals(other.value.pattern());
    }

    public boolean keep(Tag tag) {
      if ((key != null) && !key.matcher(tag.key()).matches()) return false;
      if ((value != null) && !value.matcher(tag.value()).matches()) return false;
      return true;
    }

    public String toString() {
      return String.format("%s=%s", key.pattern(), value.pattern());
    }

    public Pattern key;
    public Pattern value;
  };

  public static class ValueFilterPattern {
    public ValueFilterPattern(PrototypeMeasurementFilterSpecification.ValueFilterSpecification spec) {
      if (spec == null) return;

      for (PrototypeMeasurementFilterSpecification.TagFilterSpecification tag : spec.tags) {
          this.tags.add(new TagFilterPattern(tag));
     }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof ValueFilterPattern)) return false;
      ValueFilterPattern other = (ValueFilterPattern) obj;
      return tags.equals(other.tags);
    }

    static boolean patternInList(TagFilterPattern tagPattern,
                                 Iterable<Tag> sourceTags) {
      for (Tag candidateTag : sourceTags) {
          if (tagPattern.keep(candidateTag)) {
            return true;
          }
      }
      return false;
    }

    public boolean keep(Iterable<Tag> sourceTags) {
      for (TagFilterPattern tagPattern : this.tags) {
        if (!patternInList(tagPattern, sourceTags)) {
          return false;
        }
      }
      return true;
    }

    public final List<TagFilterPattern> tags = new ArrayList<TagFilterPattern>();
  };

  public static class MeterFilterPattern {
    public MeterFilterPattern(
            String nameRegex,
            PrototypeMeasurementFilterSpecification.MeterFilterSpecification spec) {
      namePattern = Pattern.compile(nameRegex);
      if (spec == null) return;

      if (spec.values.isEmpty()) {
        values.add(new ValueFilterPattern(PrototypeMeasurementFilterSpecification.ValueFilterSpecification.ALL));
      }
      for (PrototypeMeasurementFilterSpecification.ValueFilterSpecification value : spec.values) {
          values.add(new ValueFilterPattern(value));
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof MeterFilterPattern)) return false;
      MeterFilterPattern other = (MeterFilterPattern) obj;
      return namePattern.equals(other.namePattern) && values.equals(other.values);
    }

     public final Pattern namePattern;
    public final List<ValueFilterPattern> values = new ArrayList<ValueFilterPattern>();
  };


  public static class IncludeExcludePatterns {
    public List<ValueFilterPattern> include = new ArrayList<ValueFilterPattern>();
    public List<ValueFilterPattern> exclude = new ArrayList<ValueFilterPattern>();

    public IncludeExcludePatterns() {}
    public IncludeExcludePatterns(List<ValueFilterPattern> include,
                                  List<ValueFilterPattern> exclude) {
      this.include.addAll(include);
      this.exclude.addAll(exclude);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof IncludeExcludePatterns)) {
        return false;
      }
      IncludeExcludePatterns other = (IncludeExcludePatterns)obj;
      return include.equals(other.include) && exclude.equals(other.exclude);
    }

    public boolean keep(Meter meter, Measurement measurement) {
      boolean ok = include.isEmpty();
      for (ValueFilterPattern pattern : include) {
        if (pattern.keep(measurement.id().tags())) {
          ok = true;
          break;
        }
      }

      if (ok) {
        for (ValueFilterPattern pattern : exclude) {
          if (pattern.keep(measurement.id().tags())) {
           return false;
          }
        }
      }
      return ok;
    }
  };

  public PrototypeMeasurementFilter(PrototypeMeasurementFilterSpecification specification) {
    for (Map.Entry<String, PrototypeMeasurementFilterSpecification.MeterFilterSpecification> entry
             : specification.include.entrySet()) {
      includePatterns.add(new MeterFilterPattern(entry.getKey(), entry.getValue()));
    }
    for (Map.Entry<String, PrototypeMeasurementFilterSpecification.MeterFilterSpecification> entry
             : specification.exclude.entrySet()) {
      excludePatterns.add(new MeterFilterPattern(entry.getKey(), entry.getValue()));
    }
  }

  public boolean keep(Meter meter, Measurement measurement) {
    IncludeExcludePatterns patterns = metricToPatterns(measurement.id().name());
    return patterns != null && patterns.keep(meter, measurement);
  }

  public IncludeExcludePatterns metricToPatterns(String metric) {
    IncludeExcludePatterns foundPatterns = metricNameToPatterns.get(metric);
    if (foundPatterns != null) {
        return foundPatterns;
    }

    // Since the keys in the prototype can be regular expressions,
    // need to look at all of them and can potentially match multiple,
    // each having a different set of rules.
    foundPatterns = new IncludeExcludePatterns();
    for (MeterFilterPattern meterPattern : includePatterns) {
      if (meterPattern.namePattern.matcher(metric).matches()) {
        foundPatterns.include.addAll(meterPattern.values);
      }
    }
    for (MeterFilterPattern meterPattern : excludePatterns) {
      if (meterPattern.namePattern.matcher(metric).matches()) {
        foundPatterns.exclude.addAll(meterPattern.values);
      }
    }
    metricNameToPatterns.put(metric, foundPatterns);
    return foundPatterns;
  }

  public static PrototypeMeasurementFilter loadFromPath(String path) throws IOException {
    PrototypeMeasurementFilterSpecification spec =
        PrototypeMeasurementFilterSpecification.loadFromPath(path);
    return new PrototypeMeasurementFilter(spec);
  }

  final private List<MeterFilterPattern> includePatterns = new ArrayList<MeterFilterPattern>();
  final private List<MeterFilterPattern> excludePatterns = new ArrayList<MeterFilterPattern>();

  final private Map<String, IncludeExcludePatterns> metricNameToPatterns = new HashMap<String, IncludeExcludePatterns>();
};

