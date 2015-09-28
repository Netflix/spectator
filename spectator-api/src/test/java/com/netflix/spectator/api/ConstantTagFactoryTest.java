/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.api;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the ConstantTagFactory.
 *
 * Created by pstout on 9/17/15.
 */
@RunWith(JUnit4.class)
public class ConstantTagFactoryTest {
  @Test(expected = NullPointerException.class)
  public void testNullTag() {
    new ConstantTagFactory(null);
  }

  @Test(expected = NullPointerException.class)
  public void testNullKey() {
    new ConstantTagFactory(null, "value");
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    new ConstantTagFactory("key", null);
  }

  @Test
  public void testNameFromTag() throws Exception {
    String expected = "factoryName";
    Tag tag = new TagList(expected, "unused");
    TagFactory factory = new ConstantTagFactory(tag);

    Assert.assertEquals(expected, factory.name());
  }

  @Test
  public void testNameFromKey() throws Exception {
    String expected = "factoryName";
    TagFactory factory = new ConstantTagFactory(expected, "unused");

    Assert.assertEquals(expected, factory.name());
  }

  @Test
  public void testCreateTagUsingTagConstructor() throws Exception {
    Tag expected = new TagList("key", "value");
    TagFactory factory = new ConstantTagFactory(expected);
    Tag actual = factory.createTag(null);

    Assert.assertSame(expected, actual);
  }

  @Test
  public void testCreateTagUsingKeyValueConstructor() throws Exception {
    String expectedKey = "key";
    String expectedValue = "value";
    TagFactory factory = new ConstantTagFactory(expectedKey, expectedValue);
    Tag actual = factory.createTag(null);

    Assert.assertEquals(expectedKey, actual.key());
    Assert.assertEquals(expectedValue, actual.value());
  }

  @Test
  public void equalsContractTest() {
    EqualsVerifier
            .forClass(ConstantTagFactory.class)
            .suppress(Warning.NULL_FIELDS)
            .verify();
  }
}
