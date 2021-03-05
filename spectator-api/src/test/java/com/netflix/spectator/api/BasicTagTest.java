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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the BasicTag class.
 */
public class BasicTagTest {

  @Test
  public void equalsContractTest() {
    // NOTE: EqualsVerifier doesn't work with cached hash code
    final BasicTag tag1 = new BasicTag("k1", "v1");
    BasicTag tag2 = new BasicTag("k2", "v2");
    Assertions.assertEquals(tag1, tag1);
    Assertions.assertEquals(tag2, tag2);
    Assertions.assertNotEquals(tag1, tag2);
    Assertions.assertNotEquals(tag1, null);
    Assertions.assertNotEquals(tag1, new Object());
    Assertions.assertNotEquals(tag1, new BasicTag("k1", "v2"));
    Assertions.assertNotEquals(tag1, new BasicTag("k2", "v1"));
    Assertions.assertNotEquals(tag1, new Tag() {
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

    Assertions.assertEquals(tag.hashCode(), new BasicTag(tag.key(), tag.value()).hashCode());
  }

  @Test
  public void testToString() {
    BasicTag tag = new BasicTag("k1", "v1");

    Assertions.assertEquals("k1=v1", tag.toString());
  }

  @Test
  public void testAccessors() {
    BasicTag tag = new BasicTag("k", "v");
    Assertions.assertEquals(tag.key(), "k");
    Assertions.assertEquals(tag.value(), "v");
  }

  @Test
  public void testNullKey() {
    Assertions.assertThrows(NullPointerException.class, () -> new BasicTag(null, "v"));
  }

  @Test
  public void testNullValue() {
    NullPointerException e = Assertions.assertThrows(
        NullPointerException.class, () -> new BasicTag("k", null));
    Assertions.assertEquals("parameter 'value' cannot be null (key=k)", e.getMessage());
  }
}
