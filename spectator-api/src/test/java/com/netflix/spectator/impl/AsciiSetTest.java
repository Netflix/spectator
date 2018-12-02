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
package com.netflix.spectator.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AsciiSetTest {

  @Test
  public void empty() {
    AsciiSet s = AsciiSet.fromPattern("");
    Assert.assertEquals("", s.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void nonAsciiPattern() {
    AsciiSet.fromPattern("\u26A0");
  }

  @Test
  public void patternEndOfAscii() {
    AsciiSet s = AsciiSet.fromPattern("\u007F");
    Assert.assertEquals("\u007F", s.toString());
  }

  @Test
  public void rangeToString() {
    AsciiSet s1 = AsciiSet.fromPattern("A-C");
    AsciiSet s2 = AsciiSet.fromPattern("ABC");
    Assert.assertEquals("A-C", s2.toString());
    Assert.assertEquals(s1, s2);
  }

  @Test
  public void rangeShortToString() {
    AsciiSet s1 = AsciiSet.fromPattern("A-B");
    AsciiSet s2 = AsciiSet.fromPattern("AB");
    Assert.assertEquals("AB", s1.toString());
    Assert.assertEquals(s1, s2);
  }

  @Test
  public void rangeContains() {
    AsciiSet s = AsciiSet.fromPattern("A-C");
    Assert.assertTrue(s.contains('A'));
    Assert.assertFalse(s.contains('D'));
    Assert.assertFalse(s.contains('-'));
  }

  @Test
  public void rangeContainsAll() {
    AsciiSet s = AsciiSet.fromPattern("A-C");
    Assert.assertTrue(s.containsAll("BCAAABCBCBB"));
    Assert.assertFalse(s.containsAll("BCAAABCBCBBD"));
  }

  @Test
  public void dash() {
    AsciiSet s = AsciiSet.fromPattern("-");
    Assert.assertTrue(s.contains('-'));
    Assert.assertFalse(s.contains('A'));
  }

  @Test
  public void dashStart() {
    AsciiSet s = AsciiSet.fromPattern("-A-C");
    Assert.assertTrue(s.contains('-'));
    Assert.assertTrue(s.contains('B'));
    Assert.assertFalse(s.contains('D'));
  }

  @Test
  public void dashEnd() {
    AsciiSet s = AsciiSet.fromPattern("A-C-");
    Assert.assertTrue(s.contains('-'));
    Assert.assertTrue(s.contains('B'));
    Assert.assertFalse(s.contains('D'));
  }

  @Test
  public void multiRangeContains() {
    AsciiSet s = AsciiSet.fromPattern("0-2A-C");
    Assert.assertTrue(s.containsAll("012ABC"));
    Assert.assertFalse(s.containsAll("3"));
    Assert.assertFalse(s.contains('-'));
  }

  @Test
  public void tab() {
    AsciiSet s = AsciiSet.fromPattern("\t");
    Assert.assertTrue(s.contains('\t'));
    Assert.assertFalse(s.contains(' '));
  }

  @Test(expected = IllegalArgumentException.class)
  public void badReplacement() {
    AsciiSet s = AsciiSet.fromPattern("0-2A-C");
    s.replaceNonMembers("012ABC", '*');
  }

  @Test
  public void replaceAllOk() {
    AsciiSet s = AsciiSet.fromPattern("*0-2A-C");
    Assert.assertEquals("012ABC", s.replaceNonMembers("012ABC", '*'));
  }

  @Test
  public void replace() {
    AsciiSet s = AsciiSet.fromPattern("*0-2A-C");
    Assert.assertEquals("012*ABC*", s.replaceNonMembers("0123ABCD", '*'));
  }

  @Test
  public void replaceMultiCharUnicode() {
    // http://www.fileformat.info/info/unicode/char/1f701/index.htm
    AsciiSet s = AsciiSet.fromPattern("_");
    String str = new String(Character.toChars(0x1F701));
    Assert.assertEquals(2, str.length());
    Assert.assertEquals("__", s.replaceNonMembers(str, '_'));
  }

  @Test
  public void replaceBoundaries() {
    AsciiSet s = AsciiSet.fromPattern("_");
    String str = "\u0000\u0080\uFFFF";
    Assert.assertEquals(3, str.length());
    Assert.assertEquals("___", s.replaceNonMembers(str, '_'));
  }

  @Test
  public void equalsContractTest() {
    EqualsVerifier
        .forClass(AsciiSet.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }
}
