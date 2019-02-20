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
package com.netflix.spectator.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AsciiSetTest {

  @Test
  public void empty() {
    AsciiSet s = AsciiSet.fromPattern("");
    Assertions.assertEquals("", s.toString());
  }

  @Test
  public void nonAsciiPattern() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> AsciiSet.fromPattern("\u26A0"));
  }

  @Test
  public void patternEndOfAscii() {
    AsciiSet s = AsciiSet.fromPattern("\u007F");
    Assertions.assertEquals("\u007F", s.toString());
  }

  @Test
  public void rangeToString() {
    AsciiSet s1 = AsciiSet.fromPattern("A-C");
    AsciiSet s2 = AsciiSet.fromPattern("ABC");
    Assertions.assertEquals("A-C", s2.toString());
    Assertions.assertEquals(s1, s2);
  }

  @Test
  public void rangeShortToString() {
    AsciiSet s1 = AsciiSet.fromPattern("A-B");
    AsciiSet s2 = AsciiSet.fromPattern("AB");
    Assertions.assertEquals("AB", s1.toString());
    Assertions.assertEquals(s1, s2);
  }

  @Test
  public void rangeContains() {
    AsciiSet s = AsciiSet.fromPattern("A-C");
    Assertions.assertTrue(s.contains('A'));
    Assertions.assertFalse(s.contains('D'));
    Assertions.assertFalse(s.contains('-'));
  }

  @Test
  public void rangeContainsAll() {
    AsciiSet s = AsciiSet.fromPattern("A-C");
    Assertions.assertTrue(s.containsAll("BCAAABCBCBB"));
    Assertions.assertFalse(s.containsAll("BCAAABCBCBBD"));
  }

  @Test
  public void dash() {
    AsciiSet s = AsciiSet.fromPattern("-");
    Assertions.assertTrue(s.contains('-'));
    Assertions.assertFalse(s.contains('A'));
  }

  @Test
  public void dashStart() {
    AsciiSet s = AsciiSet.fromPattern("-A-C");
    Assertions.assertTrue(s.contains('-'));
    Assertions.assertTrue(s.contains('B'));
    Assertions.assertFalse(s.contains('D'));
  }

  @Test
  public void dashEnd() {
    AsciiSet s = AsciiSet.fromPattern("A-C-");
    Assertions.assertTrue(s.contains('-'));
    Assertions.assertTrue(s.contains('B'));
    Assertions.assertFalse(s.contains('D'));
  }

  @Test
  public void multiRangeContains() {
    AsciiSet s = AsciiSet.fromPattern("0-2A-C");
    Assertions.assertTrue(s.containsAll("012ABC"));
    Assertions.assertFalse(s.containsAll("3"));
    Assertions.assertFalse(s.contains('-'));
  }

  @Test
  public void tab() {
    AsciiSet s = AsciiSet.fromPattern("\t");
    Assertions.assertTrue(s.contains('\t'));
    Assertions.assertFalse(s.contains(' '));
  }

  @Test
  public void badReplacement() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      AsciiSet s = AsciiSet.fromPattern("0-2A-C");
      s.replaceNonMembers("012ABC", '*');
    });
  }

  @Test
  public void replaceAllOk() {
    AsciiSet s = AsciiSet.fromPattern("*0-2A-C");
    Assertions.assertEquals("012ABC", s.replaceNonMembers("012ABC", '*'));
  }

  @Test
  public void replace() {
    AsciiSet s = AsciiSet.fromPattern("*0-2A-C");
    Assertions.assertEquals("012*ABC*", s.replaceNonMembers("0123ABCD", '*'));
  }

  @Test
  public void replaceMultiCharUnicode() {
    // http://www.fileformat.info/info/unicode/char/1f701/index.htm
    AsciiSet s = AsciiSet.fromPattern("_");
    String str = new String(Character.toChars(0x1F701));
    Assertions.assertEquals(2, str.length());
    Assertions.assertEquals("__", s.replaceNonMembers(str, '_'));
  }

  @Test
  public void replaceBoundaries() {
    AsciiSet s = AsciiSet.fromPattern("_");
    String str = "\u0000\u0080\uFFFF";
    Assertions.assertEquals(3, str.length());
    Assertions.assertEquals("___", s.replaceNonMembers(str, '_'));
  }

  @Test
  public void equalsContractTest() {
    EqualsVerifier
        .forClass(AsciiSet.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }
}
