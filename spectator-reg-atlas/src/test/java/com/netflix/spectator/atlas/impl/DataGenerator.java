/*
 * Copyright 2014-2021 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

final class DataGenerator {

  private DataGenerator() {
  }

  private static final String[] COMMON_KEYS = {
      "id",
      "name",
      "nf.app",
      "nf.asg",
      "nf.cluster",
      "nf.region",
      "nf.stack",
      "nf.zone",
      "statistic"
  };

  private static String randomKey(Random r) {
    return COMMON_KEYS[r.nextInt(COMMON_KEYS.length)];
  }

  private static String randomString(Random r) {
    char c = (char) ('a' + r.nextInt(26));
    return "" + c;
  }

  private static Set<String> randomStringSet(Random r) {
    Set<String> strings = new HashSet<>();
    int n = r.nextInt(10) + 1;
    for (int i = 0; i < n; ++i) {
      strings.add(randomString(r));
    }
    return strings;
  }

  static Query randomQuery(Random r, int depth) {
    if (depth > 0) {
      Query q;
      switch (r.nextInt(12)) {
        case 0:
          q = randomQuery(r, depth - 1).and(randomQuery(r, depth - 1));
          break;
        case 1:
          q = randomQuery(r, depth - 1).or(randomQuery(r, depth - 1));
          break;
        case 2:
          q = randomQuery(r, depth - 1).not();
          break;
        case 3:
          q = new Query.Equal(randomKey(r), randomString(r));
          break;
        case 4:
          q = new Query.In(randomKey(r), randomStringSet(r));
          break;
        case 5:
          q = new Query.Regex(randomKey(r), randomString(r));
          break;
        case 6:
          q = new Query.GreaterThan(randomKey(r), randomString(r));
          break;
        case 7:
          q = new Query.GreaterThanEqual(randomKey(r), randomString(r));
          break;
        case 8:
          q = new Query.LessThan(randomKey(r), randomString(r));
          break;
        case 9:
          q = new Query.LessThanEqual(randomKey(r), randomString(r));
          break;
        case 10:
          q = new Query.Has(randomKey(r));
          break;
        default:
          q = r.nextBoolean() ? Query.TRUE : Query.FALSE;
      }
      return q;
    } else {
      return new Query.Has(randomString(r));
    }
  }
}
