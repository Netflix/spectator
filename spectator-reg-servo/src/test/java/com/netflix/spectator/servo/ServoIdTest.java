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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;


public class ServoIdTest {

  @Test
  public void equalsContractTest() {
    TagList ts1 = BasicTagList.of("k1", "v1");
    TagList ts2 = BasicTagList.of("k1", "v1", "k2", "v2");
    MonitorConfig c1 = new MonitorConfig.Builder("foo").withTags(ts1).build();
    MonitorConfig c2 = new MonitorConfig.Builder("foo").withTags(ts2).build();
    EqualsVerifier
        .forClass(ServoId.class)
        .withPrefabValues(MonitorConfig.class, c1, c2)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }
}
