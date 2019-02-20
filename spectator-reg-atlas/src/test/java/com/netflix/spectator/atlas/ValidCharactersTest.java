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
package com.netflix.spectator.atlas;

import com.netflix.spectator.impl.AsciiSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Suite for removed ValidCharacters class. Test case was kept and changed to use AsciiSet
// to help verify the new validation.
public class ValidCharactersTest {

  private final AsciiSet set = AsciiSet.fromPattern("-._A-Za-z0-9");

  private String toValidCharset(String str) {
    return set.replaceNonMembers(str, '_');
  }

  @Test
  public void nullValue() throws Exception {
    Assertions.assertThrows(NullPointerException.class, () -> {
      String input = null;
      toValidCharset(input);
    });
  }

  @Test
  public void empty() throws Exception {
    String input = "";
    String actual = toValidCharset(input);
    Assertions.assertEquals("", actual);
  }

  @Test
  public void allValid() throws Exception {
    String input = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";
    String actual = toValidCharset(input);
    Assertions.assertEquals(input, actual);
  }

  @Test
  public void invalidConvertsToUnderscore() throws Exception {
    String input = "a,b%c^d&e|f{g}h:i;";
    String actual = toValidCharset(input);
    Assertions.assertEquals("a_b_c_d_e_f_g_h_i_", actual);
  }
}
