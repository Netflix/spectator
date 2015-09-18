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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the BasicTagFactory.
 *
 * Created by pstout on 9/17/15.
 */
@RunWith(JUnit4.class)
public class BasicTagFactoryTest {

  @Test(expected = NullPointerException.class)
  public void testNullName() {
    new BasicTagFactory(null);
  }

  @Test
  public void testName() throws Exception {
    String expected = "factoryName";
    TagFactory factory = new BasicTagFactory(expected);

    Assert.assertEquals(expected, factory.name());
  }

  @Test
  public void testFactoryNameUsedAsKey() throws Exception {
    String expected = "factoryName";
    TagFactory factory = new BasicTagFactory(expected);
    Tag tag = factory.createTag("ignored");

    Assert.assertEquals(expected, tag.key());
  }

  @Test
  public void testCreateTagNullValue() throws Exception {
    TagFactory factory = new BasicTagFactory("ignored");
    Tag tag = factory.createTag(null);

    Assert.assertEquals(TagList.EMPTY, tag);
  }

  @Test
  public void testCreateTagNonNullValue() throws Exception {
    TagFactory factory = new BasicTagFactory("ignored");
    String expected = "value";
    Tag tag = factory.createTag(expected);

    Assert.assertEquals(expected, tag.value());
  }
}
