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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

public class TagFactoryTest {

  @Test
  public void fromSupplier() {
    TagFactory f = TagFactory.from("foo", () -> "bar");
    Assertions.assertEquals("foo", f.name());
    Assertions.assertEquals(new BasicTag("foo", "bar"), f.createTag());
  }

  @Test
  public void fromSupplierNull() {
    TagFactory f = TagFactory.from("foo", () -> null);
    Assertions.assertEquals("foo", f.name());
    Assertions.assertNull(f.createTag());
  }

  @Test
  public void fromSupplierDynamic() {
    AtomicReference<String> value = new AtomicReference<>();
    TagFactory f = TagFactory.from("foo", value::get);
    Assertions.assertEquals("foo", f.name());
    Assertions.assertNull(f.createTag());
    value.set("bar");
    Assertions.assertEquals(new BasicTag("foo", "bar"), f.createTag());
  }
}
