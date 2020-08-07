/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.nflx;

import com.netflix.archaius.api.Config;
import com.netflix.archaius.config.MapConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class NetflixConfigTest {

  @Test
  public void defaultEnvVarsAreSet() {
    Config config = NetflixConfig.loadPropFiles();
    Assertions.assertEquals("test", config.getString("NETFLIX_ENVIRONMENT"));
    Assertions.assertEquals("unknown", config.getString("EC2_OWNER_ID"));
    Assertions.assertEquals("us-east-1", config.getString("EC2_REGION"));
  }

  @Test
  public void defaultOverrides() {
    Config overrides = MapConfig.builder()
        .put("NETFLIX_ENVIRONMENT", "prod")
        .put("substitutions", "${NETFLIX_ENVIRONMENT}-${EC2_OWNER_ID}")
        .build();
    Config config = NetflixConfig.createConfig(overrides);
    Assertions.assertEquals("prod", config.getString("NETFLIX_ENVIRONMENT"));
    Assertions.assertEquals("unknown", config.getString("EC2_OWNER_ID"));
    Assertions.assertEquals("us-east-1", config.getString("EC2_REGION"));
    Assertions.assertEquals("prod-unknown", config.getString("substitutions"));
  }

  @Test
  public void commonTagsCannotBeEmpty() {
    Map<String, String> commonTags = NetflixConfig.commonTags();
    commonTags.forEach((k, v) -> Assertions.assertFalse(v.isEmpty()));
  }
}
