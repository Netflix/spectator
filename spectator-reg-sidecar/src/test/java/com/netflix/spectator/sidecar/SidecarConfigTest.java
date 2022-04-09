/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.sidecar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.function.Function;


public class SidecarConfigTest {

  @Test
  public void outputLocationDefault() {
    SidecarConfig config = s -> null;
    Assertions.assertEquals("udp://127.0.0.1:1234", config.outputLocation());
  }

  @Test
  public void outputLocationSet() {
    SidecarConfig config = s -> "sidecar.output-location".equals(s) ? "none" : null;
    Assertions.assertEquals("none", config.outputLocation());
  }

  @Test
  public void commonTagsEmpty() {
    SidecarConfig config = s -> null;
    Assertions.assertEquals(Collections.emptyMap(), config.commonTags());
  }

  @Test
  public void commonTagsProcess() {
    Function<String, String> getenv = s -> "NETFLIX_PROCESS_NAME".equals(s) ? "test" : null;
    Assertions.assertEquals(
        Collections.singletonMap("nf.process", "test"),
        CommonTags.commonTags(getenv));
  }

  @Test
  public void commonTagsContainer() {
    Function<String, String> getenv = s -> "TITUS_CONTAINER_NAME".equals(s) ? "test" : null;
    Assertions.assertEquals(
        Collections.singletonMap("nf.container", "test"),
        CommonTags.commonTags(getenv));
  }
}
