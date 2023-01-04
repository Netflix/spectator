/*
 * Copyright 2014-2023 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.nflx.tagging;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Helper utility to extract common tags from the environment.
 */
public final class NetflixTagging {

  private NetflixTagging() {
  }

  /**
   * Set of common tag keys that should be used for metrics by default. Other contexts
   * like logs could use a larger set that are inappropriate for use on metrics.
   */
  private static final Set DEFAULT_ATLAS_TAG_KEYS;
  static {
    Set<String> tagKeys = new HashSet<>();
    tagKeys.add("mantisJobId");
    tagKeys.add("mantisJobName");
    tagKeys.add("mantisUser");
    tagKeys.add("mantisWorkerIndex");
    tagKeys.add("mantisWorkerNumber");
    tagKeys.add("mantisWorkerStageNumber");
    tagKeys.add("nf.account");
    tagKeys.add("nf.app");
    tagKeys.add("nf.asg");
    tagKeys.add("nf.cluster");
    tagKeys.add("nf.container");
    tagKeys.add("nf.node");
    tagKeys.add("nf.process");
    tagKeys.add("nf.region");
    tagKeys.add("nf.shard1");
    tagKeys.add("nf.shard2");
    tagKeys.add("nf.stack");
    tagKeys.add("nf.vmtype");
    tagKeys.add("nf.zone");

    DEFAULT_ATLAS_TAG_KEYS = Collections.unmodifiableSet(tagKeys);
  }

  /** Replace characters not allowed by Atlas with underscore. */
  private static String fixTagString(String str) {
    return str.replaceAll("[^-._A-Za-z0-9~^]", "_");
  }

  /** Fix tag strings for map to ensure they are valid for Atlas. */
  private static Map<String, String> fixTagStrings(Map<String, String> tags) {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      result.put(fixTagString(entry.getKey()), fixTagString(entry.getValue()));
    }
    return result;
  }

  /**
   * Extract common infrastructure tags for use with metrics, logs, etc from the
   * Netflix environment variables.
   *
   * @return
   *     Common tags based on the environment.
   */
  public static Map<String, String> commonTags() {
    return commonTags(System::getenv);
  }

  /**
   * Extract common infrastructure tags for use with metrics, logs, etc from the
   * Netflix environment variables.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @return
   *     Common tags based on the environment.
   */
  public static Map<String, String> commonTags(Function<String, String> getenv) {
    // Note, these extract from the environment directly rather than use the helper
    // methods that access the reference config. This is done because the helpers
    // assign default values to many of the entries which can be convenient for testing,
    // but are not appropriate for inclusion with tags.
    Map<String, String> tags = new HashMap<>();

    // Generic infrastructure
    putIfNotEmptyOrNull(getenv, tags, "nf.account",
        "NETFLIX_ACCOUNT_ID",
        "EC2_OWNER_ID");
    putIfNotEmptyOrNull(getenv, tags, "nf.ami", "EC2_AMI_ID");
    putIfNotEmptyOrNull(getenv, tags, "nf.app", "NETFLIX_APP");
    putIfNotEmptyOrNull(getenv, tags, "nf.asg", "NETFLIX_AUTO_SCALE_GROUP");
    putIfNotEmptyOrNull(getenv, tags, "nf.cluster", "NETFLIX_CLUSTER");
    putIfNotEmptyOrNull(getenv, tags, "nf.container", "TITUS_CONTAINER_NAME");
    putIfNotEmptyOrNull(getenv, tags, "nf.node",
        "NETFLIX_INSTANCE_ID",
        "TITUS_TASK_INSTANCE_ID",
        "EC2_INSTANCE_ID");
    putIfNotEmptyOrNull(getenv, tags, "nf.process", "NETFLIX_PROCESS_NAME");
    putIfNotEmptyOrNull(getenv, tags, "nf.region",
        "NETFLIX_REGION",
        "EC2_REGION");
    putIfNotEmptyOrNull(getenv, tags, "nf.shard1", "NETFLIX_SHARD1");
    putIfNotEmptyOrNull(getenv, tags, "nf.shard2", "NETFLIX_SHARD2");
    putIfNotEmptyOrNull(getenv, tags, "nf.stack", "NETFLIX_STACK");
    putIfNotEmptyOrNull(getenv, tags, "nf.vmtype", "EC2_INSTANCE_TYPE");
    putIfNotEmptyOrNull(getenv, tags, "nf.zone", "EC2_AVAILABILITY_ZONE");

    // Build info
    putIfNotEmptyOrNull(getenv, tags, "accountType", "NETFLIX_ACCOUNT_TYPE");
    putIfNotEmptyOrNull(getenv, tags, "buildUrl", "NETFLIX_BUILD_JOB");
    putIfNotEmptyOrNull(getenv, tags, "sourceRepo", "NETFLIX_BUILD_SOURCE_REPO");
    putIfNotEmptyOrNull(getenv, tags, "branch", "NETFLIX_BUILD_BRANCH");
    putIfNotEmptyOrNull(getenv, tags, "commit", "NETFLIX_BUILD_COMMIT");

    // Mantis info
    putIfNotEmptyOrNull(getenv, tags, "mantisJobName", "MANTIS_JOB_NAME");
    putIfNotEmptyOrNull(getenv, tags, "mantisJobId", "MANTIS_JOB_ID");
    putIfNotEmptyOrNull(getenv, tags, "mantisWorkerIndex", "MANTIS_WORKER_INDEX");
    putIfNotEmptyOrNull(getenv, tags, "mantisWorkerNumber", "MANTIS_WORKER_NUMBER");
    putIfNotEmptyOrNull(getenv, tags, "mantisWorkerStageNumber", "MANTIS_WORKER_STAGE_NUMBER");
    putIfNotEmptyOrNull(getenv, tags, "mantisUser", "MANTIS_USER");

    return tags;
  }

  /**
   * Extract common infrastructure tags for use with Atlas metrics from the
   * Netflix environment variables. This may be a subset of those used for other
   * contexts like logs.
   *
   * @return
   *     Common tags based on the environment.
   */
  public static Map<String, String> commonTagsForAtlas() {
    return commonTagsForAtlas(System::getenv);
  }

  /**
   * Extract common infrastructure tags for use with Atlas metrics from the
   * Netflix environment variables. This may be a subset of those used for other
   * contexts like logs.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @return
   *     Common tags based on the environment.
   */
  public static Map<String, String> commonTagsForAtlas(Function<String, String> getenv) {
    return fixTagStrings(commonTagsForAtlas(getenv, defaultAtlasKeyPredicate(getenv)));
  }

  /**
   * Extract common infrastructure tags for use with Atlas metrics from the
   * Netflix environment variables. This may be a subset of those used for other
   * contexts like logs.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @param keyPredicate
   *     Predicate to determine if a common tag key should be included as part of
   *     the Atlas tags.
   * @return
   *     Common tags based on the environment.
   */
  public static Map<String, String> commonTagsForAtlas(
      Function<String, String> getenv, Predicate<String> keyPredicate) {
    Map<String, String> tags = commonTags(getenv);
    tags.keySet().removeIf(keyPredicate.negate());
    return tags;
  }

  /**
   * Returns the recommended predicate to use for filtering out the set of common tags
   * to the set that should be used on Atlas metrics.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @return
   *     Predicate that evaluates to true if a tag key should be included.
   */
  public static Predicate<String> defaultAtlasKeyPredicate(Function<String, String> getenv) {
    Predicate<String> skipPredicate = atlasSkipTagsPredicate(getenv);
    return k -> DEFAULT_ATLAS_TAG_KEYS.contains(k) && skipPredicate.test(k);
  }


  /**
   * Returns the recommended predicate to use for skipping common tags based on the
   * {@code ATLAS_SKIP_COMMON_TAGS} environment variable.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @return
   *     Predicate that evaluates to true if a tag key should be included.
   */
  public static Predicate<String> atlasSkipTagsPredicate(Function<String, String> getenv) {
    Set<String> tagsToSkip = parseAtlasSkipTags(getenv);
    return k -> !tagsToSkip.contains(k);
  }

  private static boolean isEmptyOrNull(String s) {
    if (s == null) {
      return true;
    }

    for (int i = 0; i < s.length(); ++i) {
      if (!Character.isWhitespace(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static void putIfNotEmptyOrNull(
      Function<String, String> getenv, Map<String, String> tags, String key, String... envVars) {
    for (String envVar : envVars) {
      String value = getenv.apply(envVar);
      if (!isEmptyOrNull(value)) {
        tags.put(key, value.trim());
        break;
      }
    }
  }

  private static Set<String> parseAtlasSkipTags(Function<String, String> getenv) {
    return parseAtlasSkipTags(getenv.apply("ATLAS_SKIP_COMMON_TAGS"));
  }

  private static Set<String> parseAtlasSkipTags(String skipTags) {
    if (isEmptyOrNull(skipTags)) {
      return Collections.emptySet();
    } else {
      Set<String> set = new HashSet<>();
      String[] parts = skipTags.split(",");
      for (String s : parts) {
        if (!isEmptyOrNull(s)) {
          set.add(s.trim());
        }
      }
      return set;
    }
  }
}
