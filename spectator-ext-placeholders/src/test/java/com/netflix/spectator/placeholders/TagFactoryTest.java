/**
 * Copyright 2016 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class TagFactoryTest {

  @Test
  public void fromSupplier() {
    TagFactory f = TagFactory.from("foo", () -> "bar");
    Assert.assertEquals("foo", f.name());
    Assert.assertEquals(new BasicTag("foo", "bar"), f.createTag());
  }

  @Test
  public void fromSupplierNull() {
    TagFactory f = TagFactory.from("foo", () -> null);
    Assert.assertEquals("foo", f.name());
    Assert.assertNull(f.createTag());
  }

  @Test
  public void fromSupplierDynamic() {
    AtomicReference<String> value = new AtomicReference<>();
    TagFactory f = TagFactory.from("foo", value::get);
    Assert.assertEquals("foo", f.name());
    Assert.assertNull(f.createTag());
    value.set("bar");
    Assert.assertEquals(new BasicTag("foo", "bar"), f.createTag());
  }
}
