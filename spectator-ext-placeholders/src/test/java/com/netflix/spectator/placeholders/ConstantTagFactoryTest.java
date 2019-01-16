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
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConstantTagFactoryTest {
  @Test
  public void testNullTag() {
    Assertions.assertThrows(NullPointerException.class, () -> new ConstantTagFactory(null));
  }

  @Test
  public void testNullKey() {
    Assertions.assertThrows(NullPointerException.class,
        () -> new ConstantTagFactory(null, "value"));
  }

  @Test
  public void testNullValue() {
    Assertions.assertThrows(NullPointerException.class,
        () -> new ConstantTagFactory("key", null));
  }

  @Test
  public void testNameFromTag() throws Exception {
    String expected = "factoryName";
    Tag tag = new BasicTag(expected, "unused");
    TagFactory factory = new ConstantTagFactory(tag);

    Assertions.assertEquals(expected, factory.name());
  }

  @Test
  public void testNameFromKey() throws Exception {
    String expected = "factoryName";
    TagFactory factory = new ConstantTagFactory(expected, "unused");

    Assertions.assertEquals(expected, factory.name());
  }

  @Test
  public void testCreateTagUsingTagConstructor() throws Exception {
    Tag expected = new BasicTag("key", "value");
    TagFactory factory = new ConstantTagFactory(expected);
    Tag actual = factory.createTag();

    Assertions.assertSame(expected, actual);
  }

  @Test
  public void testCreateTagUsingKeyValueConstructor() throws Exception {
    String expectedKey = "key";
    String expectedValue = "value";
    TagFactory factory = new ConstantTagFactory(expectedKey, expectedValue);
    Tag actual = factory.createTag();

    Assertions.assertEquals(expectedKey, actual.key());
    Assertions.assertEquals(expectedValue, actual.value());
  }

  @Test
  public void equalsContractTest() {
    EqualsVerifier
            .forClass(ConstantTagFactory.class)
            .suppress(Warning.NULL_FIELDS)
            .verify();
  }
}
