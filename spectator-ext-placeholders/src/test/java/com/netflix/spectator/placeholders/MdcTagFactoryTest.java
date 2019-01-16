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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for the MdcTagFactory class.
 */
public class MdcTagFactoryTest {

  @Test
  public void testNullName() {
    Assertions.assertThrows(NullPointerException.class, () -> new MdcTagFactory(null));
  }

  @Test
  public void testNameFromKey() throws Exception {
    String expected = "factoryName";
    TagFactory factory = new MdcTagFactory(expected);

    Assertions.assertEquals(expected, factory.name());
  }

  @Test
  public void testNoValueInMdc() {
    TagFactory factory = new MdcTagFactory("unused");

    Assertions.assertNull(factory.createTag());
  }

  @Test
  public void testValueInMdc() {
    String mdcName = "key";
    String expectedValue = "value";
    Tag expectedTag = new BasicTag(mdcName, expectedValue);
    TagFactory factory = new MdcTagFactory("key");

    try (MDC.MDCCloseable closeable = MDC.putCloseable(mdcName, expectedValue)) {
      Tag actualTag = factory.createTag();

      Assertions.assertEquals(expectedTag, actualTag);
    }

    // Make sure that the factory returns null after the MDC has been cleaned up.
    Assertions.assertNull(factory.createTag());
  }
}
