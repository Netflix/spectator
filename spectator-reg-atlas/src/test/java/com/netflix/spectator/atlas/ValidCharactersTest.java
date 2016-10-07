/*
 * Copyright 2014-2016 Netflix, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class ValidCharactersTest {

  @Test(expected = NullPointerException.class)
  public void nullValue() throws Exception {
    String input = null;
    ValidCharacters.toValidCharset(input);
  }

  @Test
  public void empty() throws Exception {
    String input = "";
    String actual = ValidCharacters.toValidCharset(input);
    Assert.assertEquals("", actual);
  }

  @Test
  public void allValid() throws Exception {
    String input = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";
    String actual = ValidCharacters.toValidCharset(input);
    Assert.assertEquals(input, actual);
  }

  @Test
  public void invalidConvertsToUnderscore() throws Exception {
    String input = "a,b%c^d&e|f{g}h:i;";
    String actual = ValidCharacters.toValidCharset(input);
    Assert.assertEquals("a_b_c_d_e_f_g_h_i_", actual);
  }
}
