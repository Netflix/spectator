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
package com.netflix.spectator.impl.matcher;

import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPatternMatcherTest {

  protected abstract void testRE(String regex, String value);

  private void intercept(Class<? extends Throwable> cls, String message, Runnable task) {
    try {
      task.run();
      Assertions.fail("expected " + cls.getName() + " but no exception was thrown");
    } catch (Throwable t) {
      if (!cls.isAssignableFrom(t.getClass())) {
        String error = "expected " + cls.getName() + " but received " + t.getClass().getName();
        throw new AssertionError(error, t);
      }
      if (message != null) {
        // Ignore context of the message
        String actual = t.getMessage().split("\n")[0];
        Assertions.assertEquals(message, actual);
      }
    }
  }

  private void testBadExpression(String regex, String message) {
    //Pattern.compile(regex);
    intercept(IllegalArgumentException.class, message, () -> PatternMatcher.compile(regex));
  }

  private void testUnsupported(String regex, String message) {
    intercept(UnsupportedOperationException.class, message, () -> PatternMatcher.compile(regex));
  }

  @Test
  public void prefix() {
    Assertions.assertEquals("abc", PatternMatcher.compile("^abc").prefix());
    Assertions.assertNull(PatternMatcher.compile("abc").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^(abc)").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^(abc|abcdef)").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^[a][b][c]").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^[a][b][c]+").prefix());
  }

  @Test
  public void startAnchor() {
    testRE("^abc", "abcdef");
    testRE("^abc", "123456");
    testRE("^abc^def", "def");
  }

  @Test
  public void isStartAnchored() {
    Assertions.assertTrue(PatternMatcher.compile("^abc").isStartAnchored());
    Assertions.assertTrue(PatternMatcher.compile("^[a-z]").isStartAnchored());
    Assertions.assertTrue(PatternMatcher.compile("(^a|^b)").isStartAnchored());
    Assertions.assertFalse(PatternMatcher.compile("(^a|b)").isStartAnchored());
    Assertions.assertFalse(PatternMatcher.compile("abc").isStartAnchored());
  }

  @Test
  public void endAnchor() {
    testRE("def$", "abcdef");
    testRE("def$", "123456");
  }

  @Test
  public void isEndAnchored() {
    Assertions.assertTrue(PatternMatcher.compile("abc$").isEndAnchored());
    Assertions.assertTrue(PatternMatcher.compile("[a-z]$").isEndAnchored());
    Assertions.assertTrue(PatternMatcher.compile("(a|b)$").isEndAnchored());
    Assertions.assertFalse(PatternMatcher.compile("(a$|b)").isEndAnchored());
    Assertions.assertFalse(PatternMatcher.compile("abc").isEndAnchored());
  }

  @Test
  public void alwaysMatches() {
    Assertions.assertTrue(PatternMatcher.compile(".*").alwaysMatches());
    Assertions.assertFalse(PatternMatcher.compile(".?").alwaysMatches());
    Assertions.assertFalse(PatternMatcher.compile("$.").alwaysMatches());
  }

  @Test
  public void neverMatches() {
    Assertions.assertFalse(PatternMatcher.compile(".*").neverMatches());
    Assertions.assertFalse(PatternMatcher.compile(".?").neverMatches());
    Assertions.assertTrue(PatternMatcher.compile("$.").neverMatches());
  }

  @Test
  public void exact() {
    testRE("^abc$", "abc");
    testRE("^abc$", "abcd");
    testRE("^abc$", "abd");
    testRE("^abc$", "123");
  }

  @Test
  public void indexOf() {
    testRE(".*abc", "abc");
    testRE(".*abc", "    abc");
    testRE(".*abc.*", "    abc    ");
  }

  @Test
  public void substr() {
    testRE("abc", "12345 abc 67890");
  }

  @Test
  public void glob() {
    testRE("^*.abc", "12345 abc 67890");
    testRE("^*abc", "12345 abc 67890");
  }

  @Test
  public void danglingModifier() {
    testBadExpression("?:?abc", "dangling modifier");
    testBadExpression("*abc", "dangling modifier");
    testBadExpression("+abc", "dangling modifier");
  }

  @Test
  public void unclosedCharClass() {
    // unclosed character class, closing brace as first character will be treated as part
    // of the set
    testBadExpression("[]", "unclosed character class");
    testBadExpression("[\\]", "unclosed character class");
    testBadExpression("[^]", "unclosed character class");
    testBadExpression("[^\\]", "unclosed character class");
    testBadExpression("[", "unclosed character class");
  }

  @Test
  public void oneOrMore() {
    testRE("a+", "abc");
    testRE("^.+$", "abc");
    testRE("^(.+)$", "abc");
  }

  @Test
  public void numbers() {
    testRE("^1*", "1000");
    testRE("^1*", "3110");
    testRE("^1*", "3691");
    testRE("^1*", "3692");
  }

  @Test
  public void unicodeChars() {
    testRE("abc_\\u003b", "abc_\u003b");
  }

  @Test
  public void predefinedCharClasses() {
    for (char c = 0; c < 128; ++c) {
      String v = "" + c;
      testRE("\\d", v);
      testRE("\\D", v);
      testRE("\\s", v);
      testRE("\\S", v);
      testRE("\\w", v);
      testRE("\\W", v);
    }
  }

  @Test
  public void posixCharClasses() {
    String[] names = {
        "Lower",
        "Upper",
        "ASCII",
        "Digit",
        "Alpha",
        "Alnum",
        "Punct",
        "Graph",
        "Print",
        "Blank",
        "Cntrl",
        "XDigit",
        "Space"
    };
    for (char c = 0; c < 128; ++c) {
      String v = Character.toString(c);
      for (String name : names) {
        testRE("\\p{" + name + "}", v);
        testRE("\\P{" + name + "}", v);
      }
    }
  }

  @Test
  public void posixAbbreviations() {
    testBadExpression("\\pabc", "unknown character property name: a");
    testBadExpression("\\pLbc", "unknown character property name: L");
  }

  @Test
  public void escapeInCharClass() {
    testRE("abc_[\\w]+_ghi", "abc_def_ghi");
    testRE("abc_[\\w-\\s]+_ghi", "abc_de f-_ghi");
    testRE("abc_[\\]]+_ghi", "abc_]_ghi");
    testRE("abc_[]]+_ghi", "abc_]_ghi");
    testRE("abc_[de\\sf]+_ghi", "abc_ \t\ndef_ghi");
    testRE("abc_[\\p{Space}9\\w]+_ghi", "abc_ \t\n9def_ghi");
    testRE("abc_[\\t- ]+_ghi", "abc_ \t\n_ghi");
  }

  @Test
  public void nestedCharClass() {
    testRE("^[a-f&&]*$", "abcdef&");
    testRE("^[a-f&]*$", "abcdef&");
    testRE("^[a-f[A-F]]*$", "abcdefABCDEF");
    testRE("^[a-f[A-F][G-I&&[^H]]]*$", "abcdefABCDEFGI");
    testRE("^[a-f[A-F][G-I&&[H]]]*$", "abcdefABCDEFH");
    testRE("^[a-f[A-F][G-I&&[H]]]*$", "abcdef&");
    testRE("^[a-f[A-F]]*$", "abcdef[]ABCDEF");
    testRE("^[a-f[A-F[0-2]]]*$", "abcdefABCDEF012");
  }

  @Test
  public void quotation() {
    testRE("(\\Q])(?\\${*\\E)", "])(?\\${*");
    testRE("\\Q])(?\\${*\\Ef(o)o", "])(?\\${*foo");
    testBadExpression("\\Q])(?\\${*", "unclosed quotation");
  }

  @Test
  public void unsupportedFeatures() {
    testUnsupported("\\h", "horizontal whitespace class");
    testUnsupported("\\H", "horizontal whitespace class");
    testUnsupported("\\v", "vertical whitespace class");
    testUnsupported("\\V", "vertical whitespace class");
    testUnsupported("\\1", "back references");
    testUnsupported("\\99", "back references");
    testUnsupported("\\k<foo>", "back references");
  }

  @Test
  public void endsWith() {
    testRE("^abc_(def|ghi)$", "abc_ghi");
    testRE("^abc_(def|ghi)$", "abc_foo_ghi");
    testRE("^abcd.*def$", "abcdef");
  }

  @Test
  public void chained() {
    testRE("(a*)(b?)(b+)b{3}", "aaabbbbbbb");
  }

  @Test
  public void greedyStartAndEnd() {
    testRE("^([^!.]+).att.com!(.+)$", "gryphon.att.com!eby");
  }

  @Test
  public void or() {
    testRE("(ab)c|abc", "abc");
    testRE("(a|b)*c|(a|ab)*c", "abc");
    testRE("^a(bc+|b[eh])g|.h$", "abh");
    testRE(".*abc.*|.*def.*|.*ghi.*", "ghi");
    testRE("[a-z]|\\p{Lower}", "ghi");
    testRE("^(\\d|\\d\\d|\\d\\d\\d)$", "123");
    testRE("^(\\d|\\d\\d|\\d\\d\\d)$", "1");
  }

  @Test
  public void orDedup() {
    testRE(
        "781|910|1109|1222|1223|1193|1224|1700|1436|1080|1278|1287|1698|1699|1580|1481|770|1191|1416|1605",
        "1710");
  }

  @Test
  public void namedCapturingGroup() {
    testRE("(?<foo>abc)", "abc");
  }

  @Test
  public void unclosedNamedCapturingGroup() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("(?<foo)"));
  }

  @Test
  public void unbalancedOpeningParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("((abc)"));
  }

  @Test
  public void unbalancedClosingParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("(abc))"));
  }

  @Test
  public void unknownQuantifier() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile(".+*"));
  }

  @Test
  public void controlEscape() {
    Assertions.assertThrows(UnsupportedOperationException.class,
        () -> PatternMatcher.compile("\\cM"));
  }

  @Test
  public void inlineFlags() {
    Assertions.assertThrows(UnsupportedOperationException.class,
        () -> PatternMatcher.compile("(?u)abc"));
  }

  private List<String[]> loadTestFile(String name) throws Exception {
    List<String[]> pairs = new ArrayList<>();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream in = classLoader.getResourceAsStream(name)) {
      new BufferedReader(new InputStreamReader(in))
          .lines()
          .filter(line -> line.startsWith("BE") || line.startsWith("E"))
          .forEach(line -> {
            String[] parts = line.split("\\t+");
            pairs.add(new String[] {
                parts[1].replace("\\n", "\n"),
                parts[2].replace("\\n", "\n")
            });
          });
    }
    return pairs;
  }

  @Test
  public void basic() throws Exception {
    // Check against basic compatibility tests for re2j
    loadTestFile("basic.dat").forEach(pair -> testRE(pair[0], pair[1]));
  }

  @Test
  public void jdk() throws Exception {
    // Check against basic compatibility tests for jdk based on description in Pattern
    // javadocs:
    // https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
    loadTestFile("jdk.dat").forEach(pair -> testRE(pair[0], pair[1]));
  }
}
