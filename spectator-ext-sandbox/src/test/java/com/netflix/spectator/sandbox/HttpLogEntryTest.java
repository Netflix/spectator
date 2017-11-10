/*
 * Copyright 2014-2017 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;

@RunWith(JUnit4.class)
public class HttpLogEntryTest {

  @Test(expected = NullPointerException.class)
  public void npeIfMethodIsNotSet() {
    HttpLogEntry entry = new HttpLogEntry()
        .withClientName("test")
        .withRequestUri(URI.create("http://test.com/foo"));
    try {
      HttpLogEntry.logClientRequest(entry);
    } catch (NullPointerException e) {
      Assert.assertEquals("parameter 'method' cannot be null", e.getMessage());
      throw e;
    }
  }
}
