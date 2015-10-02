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
 * Unit tests for the BasicTag class.
 * 
 * Created by pstout on 10/1/15.
 */
@RunWith(JUnit4.class)
public class BasicTagTest {

  @Test
  public void equalsContractTest() {
    // NOTE: EqualsVerifier doesn't work with cached hash code
    final BasicTag tag1 = new BasicTag("k1", "v1");
    BasicTag tag2 = new BasicTag("k2", "v2");
    Assert.assertEquals(tag1, tag1);
    Assert.assertEquals(tag2, tag2);
    Assert.assertNotEquals(tag1, tag2);
    Assert.assertNotEquals(tag1, null);
    Assert.assertNotEquals(tag1, new Object());
    Assert.assertNotEquals(tag1, new BasicTag("k1", "v2"));
    Assert.assertNotEquals(tag1, new BasicTag("k2", "v1"));
    Assert.assertNotEquals(tag1, new Tag() {
      @Override
      public String key() {
        return tag1.key();
      }

      @Override
      public String value() {
        return tag1.value();
      }
    });
  }

  @Test
  public void testHashCode() {
    BasicTag tag = new BasicTag("k1", "v1");

    Assert.assertEquals(tag.hashCode(), new BasicTag(tag.key(), tag.value()).hashCode());
  }

  @Test
  public void testToString() {
    BasicTag tag = new BasicTag("k1", "v1");

    Assert.assertEquals("k1=v1", tag.toString());
  }

  @Test
  public void testAccessors() {
    BasicTag tag = new BasicTag("k", "v");
    Assert.assertEquals(tag.key(), "k");
    Assert.assertEquals(tag.value(), "v");
  }

  @Test(expected = NullPointerException.class)
  public void testNullKey() {
    new BasicTag(null, "v");
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    new BasicTag("k", null);
  }
}
