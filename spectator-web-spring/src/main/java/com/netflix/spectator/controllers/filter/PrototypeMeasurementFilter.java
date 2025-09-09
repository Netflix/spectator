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
import com.netflix.spectator.api.Tag;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;


/**
 * A general filter specified using a prototype "JSON" document.
 *
 * The json document generally follows the structure of the response,
 * however in addition to containing a "metrics" section of what is desired,
 * it also contains an "excludes" section saying what is not desired.
 *
 * Each of the section contains regular expression rather than literals
 * where the regular expressions are matched against the actual names (or values).
 * Thus the excludes section can be used to restrict more general matching
 * expressions.
 *
 * Each measurement is evaluated against all the entries in the filter until one
 * is found that would cause it to be accepted and not excluded.
 */
public class PrototypeMeasurementFilter implements Predicate<Measurement> {
  /**
   * Filters based on Spectator Id tag names and/or values.
   */
  public static class TagFilterPattern {
    /**
     * Construct from regex patterns.
     */
    public TagFilterPattern(Pattern key, Pattern value) {
     this.key = key;
     this.value = value;
    }

    /**
     * Construct from the prototype specification.
     */
    public TagFilterPattern(PrototypeMeasurementFilterSpecification.TagFilterSpecification spec) {
      if (spec == null) return;

      String keySpec = spec.getKey();
      String valueSpec = spec.getValue();
      if (keySpec != null && !keySpec.isEmpty() && !".*".equals(keySpec)) {
        key = Pattern.compile(keySpec);
      }
      if (valueSpec != null && !valueSpec.isEmpty() && !".*".equals(valueSpec)) {
        value = Pattern.compile(valueSpec);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(key.pattern(), value.pattern());
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TagFilterPattern)) return false;
      TagFilterPattern other = (TagFilterPattern) obj;
      return key.pattern().equals(other.key.pattern()) && value.pattern().equals(other.value.pattern());
    }

    /**
     * Implements the MeasurementFilter interface.
     */
    public boolean test(Tag tag) {
      if ((key != null) && !key.matcher(tag.key()).matches()) return false;

      return ((value == null) || value.matcher(tag.value()).matches());
    }

    @Override
    public String toString() {
      return String.format("%s=%s", key.pattern(), value.pattern());
    }

    /**
     * Pattern for matching the Spectator tag key.
     */
    private Pattern key;

    /**
     * Pattern for matching the Spectator tag value.
     */
    private Pattern value;
  }


  /**
   * Filters on measurement values.
   *
   * A value includes a set of tags.
   * This filter does not currently include the actual measurement value, only sets of tags.
   */
  public static class ValueFilterPattern {
    /**
     * Constructs a filter from a specification.
     */
    public ValueFilterPattern(PrototypeMeasurementFilterSpecification.ValueFilterSpecification spec) {
      if (spec == null) return;

      for (PrototypeMeasurementFilterSpecification.TagFilterSpecification tag : spec.getTags()) {
          this.tags.add(new TagFilterPattern(tag));
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ValueFilterPattern)) return false;
      ValueFilterPattern other = (ValueFilterPattern) obj;
      return tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
      return tags.hashCode();
    }

    /**
     * Determins if a particular TagFilter is satisfied among the value's tag set.
     */
    static boolean patternInList(TagFilterPattern tagPattern,
                                 Iterable<Tag> sourceTags) {
      for (Tag candidateTag : sourceTags) {
          if (tagPattern.test(candidateTag)) {
            return true;
          }
      }
      return false;
    }

    /**
     * Implements the MeasurementFilter interface.
     */
    public boolean test(Iterable<Tag> sourceTags) {
      for (TagFilterPattern tagPattern : this.tags) {
        if (!patternInList(tagPattern, sourceTags)) {
          return false;
        }
      }
      return true;
    }


    /**
     * The list of tag filters that must be satisfied for the value to be satisfied.
     */
    public List<TagFilterPattern> getTags() {
      return tags;
    }
    private final List<TagFilterPattern> tags = new ArrayList<>();
  }


  /**
   * Filters a meter.
   */
  public static class MeterFilterPattern {
    /**
     * Constructs from a specification.
     *
     * The nameRegex specifies the name of the Spectator meter itself.
     */
    public MeterFilterPattern(
            String nameRegex,
            PrototypeMeasurementFilterSpecification.MeterFilterSpecification spec) {
      namePattern = Pattern.compile(nameRegex);
      if (spec == null) return;

      if (spec.getValues().isEmpty()) {
        values.add(new ValueFilterPattern(PrototypeMeasurementFilterSpecification.ValueFilterSpecification.ALL));
      }
      for (PrototypeMeasurementFilterSpecification.ValueFilterSpecification value : spec.getValues()) {
          values.add(new ValueFilterPattern(value));
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MeterFilterPattern)) return false;
      MeterFilterPattern other = (MeterFilterPattern) obj;
      return namePattern.equals(other.namePattern) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
      return Objects.hash(namePattern, values);
    }

    /**
     * Filters the name of the meter.
     */
    private final Pattern namePattern;

    /**
     * A list of value filters acts as a disjunction.
     * Any of the values can be satisifed to satisfy the Meter.
     */
    public List<ValueFilterPattern> getValues() {
      return values;
    }
    private final List<ValueFilterPattern> values = new ArrayList<>();
  }


  /**
   * A collection of Include patterns and Exclude patterns for filtering.
   */
  public static class IncludeExcludePatterns {
    /**
     * The value patterns that must be satisifed to include.
     */
    public List<ValueFilterPattern> getInclude() {
      return include;
    }
    private final List<ValueFilterPattern> include = new ArrayList<>();

    /**
     * The value patterns that cannot be satisifed to include.
     * This is meant to refine the include list from being too generous.
     */
    public List<ValueFilterPattern> getExclude() {
      return exclude;
    }
    private final List<ValueFilterPattern> exclude = new ArrayList<>();


    /**
     * Default constructor.
     */
    public IncludeExcludePatterns() {
      // empty.
    }

    /**
     * Constructor.
     */
    public IncludeExcludePatterns(List<ValueFilterPattern> include,
                                  List<ValueFilterPattern> exclude) {
      this.include.addAll(include);
      this.exclude.addAll(exclude);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IncludeExcludePatterns)) {
        return false;
      }
      IncludeExcludePatterns other = (IncludeExcludePatterns) obj;
      return include.equals(other.include) && exclude.equals(other.exclude);
    }

    @Override
    public int hashCode() {
      return Objects.hash(include, exclude);
    }

    /**
     * Implements the MeasurementFilter interface.
     */
    public boolean test(Measurement measurement) {
      boolean ok = include.isEmpty();
      for (ValueFilterPattern pattern : include) {
        if (pattern.test(measurement.id().tags())) {
          ok = true;
          break;
        }
      }

      if (ok) {
        for (ValueFilterPattern pattern : exclude) {
          if (pattern.test(measurement.id().tags())) {
           return false;
          }
        }
      }
      return ok;
    }
  }

  /**
   * Constructor.
   */
  public PrototypeMeasurementFilter(PrototypeMeasurementFilterSpecification specification) {
    for (Map.Entry<String, PrototypeMeasurementFilterSpecification.MeterFilterSpecification> entry
             : specification.getInclude().entrySet()) {
      includePatterns.add(new MeterFilterPattern(entry.getKey(), entry.getValue()));
    }
    for (Map.Entry<String, PrototypeMeasurementFilterSpecification.MeterFilterSpecification> entry
             : specification.getExclude().entrySet()) {
      excludePatterns.add(new MeterFilterPattern(entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Implements the MeasurementFilter interface.
   */
  @Override public boolean test(Measurement measurement) {
    IncludeExcludePatterns patterns = metricToPatterns(measurement.id().name());
    return patterns != null && patterns.test(measurement);
  }

  /**
   * Find the IncludeExcludePatterns for filtering a given metric.
   *
   * The result is the union of all the individual pattern entries
   * where their specified metric name patterns matches the actual metric name.
   */
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

  /**
   * Factory method building a filter from a specification file.
   */
  public static PrototypeMeasurementFilter loadFromPath(String path) throws IOException {
    PrototypeMeasurementFilterSpecification spec =
        PrototypeMeasurementFilterSpecification.loadFromPath(path);
    return new PrototypeMeasurementFilter(spec);
  }

  /**
   * All the meter filter patterns that can be satisfied.
   */
  private final List<MeterFilterPattern> includePatterns = new ArrayList<>();

  /**
   * All the meter filter patterns that cannot be satisfied.
   */
  private final List<MeterFilterPattern> excludePatterns = new ArrayList<>();

  /**
   * A cache of previously computed includeExcludePatterns.
   * Since the patterns are static and meters heavily reused,
   * we'll cache previous results for the next time we apply the filter.
   */
  private final Map<String, IncludeExcludePatterns> metricNameToPatterns
      = new HashMap<>();
}

