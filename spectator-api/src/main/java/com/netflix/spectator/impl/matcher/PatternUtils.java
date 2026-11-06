/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Helper functions for working with patterns.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class PatternUtils {

  /**
   * Maximum number of characters for a negative lookahead that will be inlined. Each
   * character adds a level of nesting to the rewritten pattern, so long lookaheads could
   * overflow the stack when processing the matcher or exceed the nesting limits of other
   * regex engines.
   */
  private static final int MAX_INLINE_LOOKAHEAD_LENGTH = 100;

  private PatternUtils() {
  }

  /**
   * Compile a pattern string and return a matcher that can be used to check if string values
   * match the pattern. Pattern matchers are can be reused many times and are thread safe.
   */
  public static PatternMatcher compile(String pattern) {
    String p = pattern;
    boolean ignoreCase = false;
    if (p.startsWith("(?i)")) {
      ignoreCase = true;
      p = pattern.substring(4);
    }
    Matcher m = new Parser(removeQuotations(p)).parse();
    // Match anywhere in the string, same as the pattern ^.*(p).*$. The matcher is wrapped
    // rather than the pattern string so the wrapper cannot mix with the syntax of the
    // pattern, e.g. for a)|(b or a trailing backslash. For an empty pattern, the optimizer
    // reduces the wrapper to a matcher that always matches.
    Matcher end = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, EndMatcher.INSTANCE);
    Matcher any = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(m, end));
    m = Optimizer.optimize(SeqMatcher.create(StartMatcher.INSTANCE, any));
    return ignoreCase ? m.ignoreCase() : m;
  }

  private static String context(String str, int pos) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < pos; ++i) {
      builder.append(' ');
    }
    builder.append('^');
    return str + "\n" + builder.toString();
  }

  /**
   * Create an IllegalArgumentException with a message including context based
   * on the position.
   */
  static IllegalArgumentException error(String message, String str, int pos) {
    return new IllegalArgumentException(message + "\n" + context(str, pos));
  }

  /**
   * Create an IllegalArgumentException for a regex feature that is not supported by this
   * matcher, with a message including context based on the position. This is an illegal
   * argument rather than an unsupported operation: the pattern is the problem, not the
   * compile operation. The message is prefixed with {@code unsupported:} to distinguish it
   * from a malformed pattern.
   */
  static IllegalArgumentException unsupported(String message, String str, int pos) {
    return new IllegalArgumentException("unsupported: " + message + "\n" + context(str, pos));
  }

  /**
   * Replace quotations, {@code \\Q...\\E}, with the escaped text. Like java, this is done
   * before parsing, so the quoted text can be used anywhere a literal could be used, e.g.
   * {@code [a-\\Qz\\E]} is {@code [a-z]}, and an empty quotation joins the elements on
   * either side, e.g. {@code a*\\Q\\E?} is {@code a*?}. An unclosed quotation extends to
   * the end of the pattern. Other escapes are decoded by the parser.
   */
  static String removeQuotations(String str) {
    if (!str.contains("\\Q")) {
      return str;
    }
    StringBuilder builder = new StringBuilder(str.length());
    int i = 0;
    while (i < str.length()) {
      char c = str.charAt(i);
      if (c != '\\' || i + 1 >= str.length()) {
        builder.append(c);
        ++i;
      } else if (str.charAt(i + 1) != 'Q') {
        // Other escape, e.g. \\\\Q is an escaped backslash followed by 'Q'
        builder.append(c).append(str.charAt(i + 1));
        i += 2;
      } else {
        int end = str.indexOf("\\E", i + 2);
        if (end < 0) {
          end = str.length();
        }
        for (int j = i + 2; j < end; ++j) {
          appendQuoted(builder, str.charAt(j), j == i + 2);
        }
        i = Math.min(end + 2, str.length());
      }
    }
    return builder.toString();
  }

  /**
   * Append a quoted character so it is a literal for the parser. ASCII letters and digits
   * are kept as is, since escaping them would change the meaning, e.g. {@code \\d}. Like
   * java, a digit at the start of the quotation is written as a hex escape so it cannot be
   * joined with an escape or repetition before the quotation, e.g. {@code \\07\\Q7\\E} is
   * {@code \\07} followed by '7' rather than {@code \\077}. Surrogates are kept as is so
   * that a pair stays together.
   */
  private static void appendQuoted(StringBuilder builder, char c, boolean first) {
    if (Constants.DIGIT.contains(c)) {
      if (first) {
        builder.append("\\x3");
      }
    } else if (!Constants.ALPHA.contains(c) && !Character.isSurrogate(c)) {
      builder.append('\\');
    }
    builder.append(c);
  }

  /**
   * Escape a string so it will be interpreted as a literal character sequence when processed
   * as a regular expression.
   */
  @SuppressWarnings("PMD.NcssCount")
  public static String escape(String str) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '\t': builder.append("\\t"); break;
        case '\n': builder.append("\\n"); break;
        case '\r': builder.append("\\r"); break;
        case '\f': builder.append("\\f"); break;
        case '\\': builder.append("\\\\"); break;
        case '^':  builder.append("\\^"); break;
        case '$':  builder.append("\\$"); break;
        case '.':  builder.append("\\."); break;
        case '|':  builder.append("\\|"); break;
        case '?':  builder.append("\\?"); break;
        case '*':  builder.append("\\*"); break;
        case '+':  builder.append("\\+"); break;
        case '[':  builder.append("\\["); break;
        case ']':  builder.append("\\]"); break;
        case '(':  builder.append("\\("); break;
        case ')':  builder.append("\\)"); break;
        case '{':  builder.append("\\{"); break;
        case '}':  builder.append("\\}"); break;
        default:
          if (c <= ' ' || (c > '~' && c <= 0xFF)) {
            // Use the hex escape, RE2 doesn't support the unicode escape. Characters
            // above 0xFF are not special, so they are kept as is.
            builder.append(String.format("\\x%02x", (int) c));
          } else {
            builder.append(c);
          }
          break;
      }
    }
    return builder.toString();
  }

  /** Convert to a matchers that ignores the case. */
  static Matcher ignoreCase(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      // The folded set can be empty even if the set is not, e.g. [^\\x00-`b-\\x7f].
      CharClassMatcher m = matcher.<CharClassMatcher>as().withIgnoreCase();
      return m.set().isEmpty() ? FalseMatcher.INSTANCE : m;
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return new CharSeqMatcher(m.pattern(), true);
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return new IndexOfMatcher(m.pattern(), m.next(), true);
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      return new StartsWithMatcher(m.pattern(), true);
    } else {
      return matcher;
    }
  }

  /**
   * Expand OR clauses in the provided matcher up to the max limit. If expanding would exceed
   * the max, then null will be returned.
   */
  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  static List<Matcher> expandOrClauses(Matcher matcher, int max) {
    if (matcher instanceof OrStartsWithMatcher) {
      // Folded alternation, expand the original form so the branches are visible
      return expandOrClauses(matcher.<OrStartsWithMatcher>as().delegate(), max);
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return map(
          expandOrClauses(m.next(), max),
          n -> new IndexOfMatcher(m.pattern(), n, m.isIgnoreCase()),
          max);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return map(expandOrClauses(m.next(), max), n -> new ZeroOrMoreMatcher(m.repeated(), n), max);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      List<Matcher> rs = expandOrClauses(m.repeated(), max);
      List<Matcher> ns = expandOrClauses(m.next(), max);
      if (rs == null || ns == null || ns.size() * (rs.size() + 1) > max) {
        return null;
      } else if (rs.size() == 1 && ns.size() == 1) {
        return Collections.singletonList(matcher);
      } else {
        List<Matcher> results = new ArrayList<>(ns);
        for (Matcher r : rs) {
          for (Matcher n : ns) {
            results.add(new ZeroOrOneMatcher(r, n));
          }
        }
        return results;
      }
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      PositiveLookaheadMatcher m = matcher.as();
      return map(expandOrClauses(m.matcher(), max), PositiveLookaheadMatcher::new, max);
    } else if (matcher instanceof SeqMatcher) {
      return expandSeq(matcher.as(), max);
    } else if (matcher instanceof OrMatcher) {
      return expandOr(matcher.as(), max);
    } else {
      return Collections.singletonList(matcher);
    }
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> map(List<Matcher> ms, Function<Matcher, Matcher> f, int max) {
    if (ms == null || ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>(ms.size());
    for (Matcher m : ms) {
      results.add(f.apply(m));
    }
    return results;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> expandSeq(SeqMatcher seqMatcher, int max) {
    List<List<Matcher>> results = new ArrayList<>();
    for (Matcher matcher : seqMatcher.matchers()) {
      if (results.isEmpty()) {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null)
          return null;
        for (Matcher m : rs) {
          List<Matcher> tmp = new ArrayList<>();
          tmp.add(m);
          results.add(tmp);
        }
      } else {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null || results.size() * rs.size() > max)
          return null;
        List<List<Matcher>> tmp = new ArrayList<>(results.size() * rs.size());
        for (List<Matcher> ms : results) {
          for (Matcher r : rs) {
            List<Matcher> seq = new ArrayList<>(ms);
            seq.add(r);
            tmp.add(seq);
          }
        }
        results = tmp;
      }
    }

    List<Matcher> tmp = new ArrayList<>(results.size());
    for (List<Matcher> ms : results) {
      tmp.add(SeqMatcher.create(ms));
    }
    return tmp;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> expandOr(OrMatcher matcher, int max) {
    List<Matcher> ms = matcher.matchers();
    if (ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>();
    for (Matcher m : ms) {
      List<Matcher> tmp = expandOrClauses(m, max);
      if (tmp == null || results.size() + tmp.size() > max) {
        return null;
      }
      results.addAll(tmp);
    }
    return results;
  }

  static PatternExpr toPatternExpr(Matcher matcher, int max) {
    List<Matcher> matchers = expandOrClauses(matcher, max);
    if (matchers == null) {
      return null;
    } else {
      List<PatternExpr> exprs = new ArrayList<>(matchers.size());
      for (Matcher m : matchers) {
        PatternExpr expr = expandLookahead(inlineNegativeLookahead(m, false, true));
        if (expr == null) {
          // If any branch cannot be simplified, then neither can the overall expression
          return null;
        }
        exprs.add(expr);
      }
      return PatternExpr.or(exprs);
    }
  }

  /**
   * Splitting out a lookahead into a separate clause is only valid if the lookahead is at a
   * fixed position. For example, with {@code foo-(?!bar)} the clause {@code NOT '.*foo-bar'}
   * would reject {@code foo-bar foo-baz} even though the second {@code foo-} is not followed
   * by {@code bar}. If nothing needs to match after the lookahead and it is for a fixed
   * sequence of characters, then it can instead be rewritten as a pattern that matches
   * anything other than that sequence. For example, {@code (?!ab)} is the same as
   * {@code ($|[^a]|a($|[^b]))} if nothing follows.
   *
   * @param matcher
   *     Matcher to rewrite.
   * @param fixed
   *     True if the start position of the matcher is fixed, i.e. it is start anchored and
   *     preceded only by fixed length matchers.
   * @param restAny
   *     True if whatever follows the matcher will match any suffix.
   */
  private static Matcher inlineNegativeLookahead(Matcher matcher, boolean fixed, boolean restAny) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new IndexOfMatcher(m.pattern(), next, m.isIgnoreCase());
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new ZeroOrMoreMatcher(m.repeated(), next);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new ZeroOrOneMatcher(m.repeated(), next);
    } else if (matcher instanceof SeqMatcher) {
      Matcher[] ms = matcher.<SeqMatcher>as().matchers().toArray(new Matcher[0]);
      boolean changed = false;
      boolean f = fixed;
      for (int i = 0; i < ms.length; ++i) {
        Matcher m = ms[i];
        ms[i] = inlineNegativeLookahead(m, f, restAny && matchesAnySuffix(ms, i + 1));
        changed |= ms[i] != m;
        f = isFixedAfter(f, m);
      }
      return changed ? SeqMatcher.create(ms) : matcher;
    } else if (matcher instanceof NegativeLookaheadMatcher && !fixed && restAny) {
      Matcher m = negateCharSeq(matcher.<NegativeLookaheadMatcher>as().matcher());
      return m == null ? matcher : m;
    } else {
      return matcher;
    }
  }

  /**
   * Create a matcher for anything other than the sequence of characters matched by the
   * lookahead, or null if the lookahead is not a sequence of single character matchers or
   * is longer than {@link #MAX_INLINE_LOOKAHEAD_LENGTH}. It will also be null if an inverted
   * set cannot be written as a negated class. The end of the string is also allowed as the
   * lookahead would not match.
   */
  private static Matcher negateCharSeq(Matcher matcher) {
    List<CharClassMatcher> classes = new ArrayList<>();
    if (!charClasses(matcher, classes)
        || classes.isEmpty()
        || classes.size() > MAX_INLINE_LOOKAHEAD_LENGTH) {
      return null;
    }
    Matcher tail = null;
    for (int i = classes.size() - 1; i >= 0; --i) {
      CharClassMatcher cls = classes.get(i);
      List<Matcher> alternatives = new ArrayList<>(3);
      alternatives.add(EndMatcher.INSTANCE);
      if (cls != null) {
        // AsciiSet cannot include non-ascii characters, but they would not match the
        // lookahead. The inverted class must be written as a negated class, e.g. [^b],
        // so that engines like RE2 will match non-ascii characters as well. The folded
        // set is inverted as well so that if the case is ignored later, [^b] will match
        // neither 'b' nor 'B'.
        AsciiSet inverted = cls.set().invert();
        Matcher m = new CharClassMatcher(inverted, cls.foldedSet().invert(), false);
        if (inverted.isEmpty() || !m.toString().startsWith("[^")) {
          return null;
        }
        alternatives.add(m);
      }
      if (tail != null) {
        Matcher m = (cls == null) ? AnyMatcher.INSTANCE : cls;
        alternatives.add(SeqMatcher.create(m, tail));
      }
      tail = OrMatcher.create(alternatives);
    }
    return tail;
  }

  /**
   * Add a class for the characters at each position of the matcher. A null entry is used
   * for any character. Returns false if the matcher is not a sequence of single character
   * matchers. For ignore case, the sets will include both cases so that the inverted set
   * will be correct. The classes will not ignore case, but keep the folded set so the
   * case can be ignored later.
   */
  private static boolean charClasses(Matcher matcher, List<CharClassMatcher> classes) {
    if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      for (int i = 0; i < pattern.length(); ++i) {
        char c = pattern.charAt(i);
        if (c >= 128) {
          return false;
        }
        AsciiSet set = AsciiSet.fromPattern(Character.toString(c));
        classes.add(new CharClassMatcher(
            m.isIgnoreCase() ? CharClassMatcher.withOtherCase(set) : set));
      }
      return true;
    } else if (matcher instanceof CharClassMatcher) {
      CharClassMatcher m = matcher.as();
      classes.add(m.isIgnoreCase()
          ? new CharClassMatcher(CharClassMatcher.withOtherCase(m.set()))
          : m);
      return true;
    } else if (matcher instanceof AnyMatcher) {
      classes.add(null);
      return true;
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (!charClasses(m, classes)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /** Returns true if the matchers starting at the offset will match any suffix. */
  private static boolean matchesAnySuffix(Matcher[] ms, int offset) {
    for (int i = offset; i < ms.length; ++i) {
      if (!matchesAnySuffix(ms[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAnySuffix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      return matchesAnySuffix(matcher.<SeqMatcher>as().matchers().toArray(new Matcher[0]), 0);
    } else {
      return matcher.alwaysMatches();
    }
  }

  /**
   * Returns true if the position after the matcher is fixed.
   *
   * @param fixed
   *     True if the position before the matcher is fixed.
   * @param matcher
   *     Matcher to check.
   */
  private static boolean isFixedAfter(boolean fixed, Matcher matcher) {
    if (matcher instanceof StartMatcher || matcher instanceof StartsWithMatcher) {
      return true;
    } else {
      return fixed && isFixedLength(matcher);
    }
  }

  private static boolean isFixedLength(Matcher matcher) {
    if (matcher instanceof CharSeqMatcher
        || matcher instanceof CharClassMatcher
        || matcher instanceof AnyMatcher
        || matcher instanceof TrueMatcher
        || matcher instanceof StartMatcher
        || matcher instanceof EndMatcher
        || matcher instanceof PositiveLookaheadMatcher
        || matcher instanceof NegativeLookaheadMatcher) {
      return true;
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      return m.min() == m.max() && isFixedLength(m.repeated());
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (!isFixedLength(m)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  private static PatternExpr expandLookahead(Matcher matcher) {
    List<PatternExpr> exprs = new ArrayList<>();
    // 0 - positive matcher to append
    // 1 - negative matcher to append
    // 2 - remaining matcher to keep processing
    Matcher[] results = new Matcher[3];

    // Keep processing until no lookaheads remain
    removeNextLookahead(matcher, results);
    while (results[2] != null) {
      if (results[0] != null)
        exprs.add(PatternExpr.simple(results[0]));
      if (results[1] != null)
        exprs.add(PatternExpr.not(PatternExpr.simple(results[1])));
      removeNextLookahead(results[2], results);
    }

    // If the results array is all null, then something was incompatible, return null
    // to indicate this regex cannot be simplified
    if (results[0] == null && results[1] == null) {
      return null;
    }

    // Add final expression
    if (results[0] != null)
      exprs.add(PatternExpr.simple(results[0]));
    if (results[1] != null)
      exprs.add(PatternExpr.not(PatternExpr.simple(results[1])));
    return PatternExpr.and(exprs);
  }

  private static void removeNextLookahead(Matcher matcher, Matcher[] results) {
    clearAndRewriteNextLookahead(matcher, results, false, true);

    // The results are complete patterns, so they can be optimized. This should not be done
    // for the partial results when recursing, because some optimizations are only valid at
    // the start of the pattern.
    for (int i = 0; i < results.length; ++i) {
      if (results[i] != null) {
        results[i] = Optimizer.optimize(results[i]);
      }
    }
  }

  private static void clearAndRewriteNextLookahead(
      Matcher matcher, Matcher[] results, boolean fixed, boolean restAny) {
    Arrays.fill(results, null);
    rewriteNextLookahead(matcher, results, fixed, restAny);
  }

  @SuppressWarnings("PMD")
  private static void rewriteNextLookahead(
      Matcher matcher, Matcher[] results, boolean fixed, boolean restAny) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      rewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new IndexOfMatcher(m.pattern(), results[i], m.isIgnoreCase());
        }
      }
    } else if (matcher instanceof SeqMatcher) {
      SeqMatcher m = matcher.as();
      Matcher[] ms = m.matchers().toArray(new Matcher[0]);
      boolean f = fixed;
      for (int i = 0; i < ms.length; ++i) {
        results[0] = null;
        rewriteNextLookahead(ms[i], results, f, restAny && matchesAnySuffix(ms, i + 1));
        f = isFixedAfter(f, ms[i]);
        if (results[2] != null) {
          // Truncated sequence with lookahead match at the end
          List<Matcher> matchers = new ArrayList<>(i + 1);
          matchers.addAll(Arrays.asList(ms).subList(0, i));
          if (results[0] != null) {
            matchers.add(results[0]);
            results[0] = SeqMatcher.create(matchers);
          } else {
            matchers.add(results[1]);
            results[1] = SeqMatcher.create(matchers);
          }

          // Matcher with lookahead removed
          ms[i] = results[2];
          results[2] = SeqMatcher.create(ms);
          break;
        } else if (results[0] == null) {
          // Indicates this entry of the sequence cannot be simplified
          return;
        } else {
          results[0] = m;
        }
      }
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (containsUnsupported(m.repeated())) {
        return;
      }
      clearAndRewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new ZeroOrMoreMatcher(m.repeated(), results[i]);
        }
      }
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      if (containsUnsupported(m.repeated())) {
        return;
      }
      clearAndRewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new ZeroOrOneMatcher(m.repeated(), results[i]);
        }
      }
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      if (containsUnsupported(m)) {
        return;
      } else {
        results[0] = matcher;
      }
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      // X(?=Y)Z is split into XY AND XZ. If the position is not fixed, this is only valid
      // when Z will match anything, e.g. foo-(?=bar).
      PositiveLookaheadMatcher m = matcher.as();
      if (containsUnsupported(m.matcher()) || !(fixed || restAny)) {
        return;
      }
      results[0] = m.matcher();
      results[2] = TrueMatcher.INSTANCE;
    } else if (matcher instanceof NegativeLookaheadMatcher) {
      // X(?!Y)Z is split into XZ AND NOT XY. This is only valid if the position is fixed,
      // see inlineNegativeLookahead for how some other cases are handled.
      NegativeLookaheadMatcher m = matcher.as();
      if (containsUnsupported(m.matcher()) || !fixed) {
        return;
      }
      results[1] = m.matcher();
      results[2] = TrueMatcher.INSTANCE;
    } else if (!containsUnsupported(matcher)) {
      results[0] = matcher;
    }
  }

  /**
   * Returns true if the matcher contains a lookahead or a repetition with a large upper bound
   * anywhere in the tree. Some engines like RE2 have limitations on the number of repetitions,
   * treat those as failures to match to the expression.
   */
  private static boolean containsUnsupported(Matcher matcher) {
    if (matcher instanceof NegativeLookaheadMatcher) {
      return true;
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      return true;
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return containsUnsupported(m.next());
    } else if (matcher instanceof OrMatcher) {
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        if (containsUnsupported(m)) {
          return true;
        }
      }
      return false;
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      return m.max() > 1000 || containsUnsupported(m.repeated());
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (containsUnsupported(m)) {
          return true;
        }
      }
      return false;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return containsUnsupported(m.repeated()) || containsUnsupported(m.next());
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      return containsUnsupported(m.repeated()) || containsUnsupported(m.next());
    } else {
      return false;
    }
  }

  /** Convert a matcher to a SQL pattern or return null if not possible. */
  static String toSqlPattern(Matcher matcher) {
    SqlPatternBuilder builder = new SqlPatternBuilder();
    if (toSqlPattern(builder, matcher) && builder.isValid()) {
      if (!builder.endsWithWildcard() && !builder.isEndAnchored()) {
        builder.appendWildcard('%');
      }
      return builder.toString();
    } else {
      return null;
    }
  }

  /**
   * Builder for SQL LIKE patterns that tracks whether the pattern currently ends with
   * a '%' wildcard. Literals are escaped using backslash as the escape character. LIKE
   * patterns are implicitly anchored, so the builder also tracks whether anything has
   * been appended after an end anchor, which cannot be represented.
   */
  @SuppressWarnings("PMD.AvoidStringBufferField")
  private static final class SqlPatternBuilder {

    private final StringBuilder builder = new StringBuilder();
    private boolean endsWithWildcard = false;
    private boolean endAnchored = false;
    private boolean valid = true;

    /** Append a wildcard, either '%' or '_'. */
    void appendWildcard(char c) {
      checkNotEnded();
      builder.append(c);
      endsWithWildcard = c == '%';
    }

    /** Append an escaped literal string. */
    void appendLiteral(String str) {
      for (int i = 0; i < str.length(); ++i) {
        char c = str.charAt(i);
        switch (c) {
          case '_':  builder.append("\\_");  break;
          case '%':  builder.append("\\%");  break;
          case '\\': builder.append("\\\\"); break;
          default:   builder.append(c);      break;
        }
      }
      if (!str.isEmpty()) {
        checkNotEnded();
        endsWithWildcard = false;
      }
    }

    private void checkNotEnded() {
      if (endAnchored) {
        valid = false;
      }
    }

    /** Mark the end anchor, nothing else can be appended after this point. */
    void markEnd() {
      endAnchored = true;
    }

    boolean isEmpty() {
      return builder.length() == 0;
    }

    boolean endsWithWildcard() {
      return endsWithWildcard;
    }

    /** Returns true if an end anchor has been seen. */
    boolean isEndAnchored() {
      return endAnchored;
    }

    /** Returns false if something was appended after the end anchor. */
    boolean isValid() {
      return valid;
    }

    @Override public String toString() {
      return builder.toString();
    }
  }

  private static boolean toSqlPattern(SqlPatternBuilder builder, Matcher matcher) {
    if (matcher instanceof TrueMatcher) {
      builder.appendWildcard('%');
      return true;
    } else if (matcher instanceof AnyMatcher) {
      builder.appendWildcard('_');
      return true;
    } else if (matcher instanceof StartMatcher) {
      // Start anchor is only supported at the beginning of the pattern
      return builder.isEmpty();
    } else if (matcher instanceof EndMatcher) {
      builder.markEnd();
      return true;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (m.repeated() == AnyMatcher.INSTANCE) {
        builder.appendWildcard('%');
        return toSqlPattern(builder, m.next());
      }
    } else if (matcher instanceof SeqMatcher) {
      SeqMatcher sm = matcher.as();
      for (Matcher m : sm.matchers()) {
        if (!toSqlPattern(builder, m)) {
          return false;
        }
      }
      return true;
    } else if (matcher instanceof CharSeqMatcher) {
      // LIKE is case sensitive, so case insensitive literals cannot be represented
      CharSeqMatcher m = matcher.as();
      if (m.isIgnoreCase()) {
        return false;
      }
      builder.appendLiteral(m.pattern());
      return true;
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      if (m.isIgnoreCase()) {
        return false;
      }
      builder.appendWildcard('%');
      builder.appendLiteral(m.pattern());
      return toSqlPattern(builder, m.next());
    } else if (matcher instanceof StartsWithMatcher) {
      // Start anchor is only supported at the beginning of the pattern
      StartsWithMatcher m = matcher.as();
      if (!builder.isEmpty() || m.isIgnoreCase()) {
        return false;
      }
      builder.appendLiteral(m.pattern());
      return true;
    }

    return false;
  }

  /** Returns the first matcher from a sequence. */
  static Matcher head(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else {
      return matcher;
    }
  }

  /**
   * Returns all but the first matcher from a sequence or True if there is only a single
   * matcher in the sequence.
   */
  static Matcher tail(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else {
      return TrueMatcher.INSTANCE;
    }
  }

  /**
   * Get the prefix matcher. This is similar to {@link #head(Matcher)} except that it can
   * reach into character sequences as well as higher level sequences.
   */
  static Matcher getPrefix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return new ZeroOrMoreMatcher(zm.repeated(), TrueMatcher.INSTANCE);
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      return pattern.isEmpty() ? null : new CharSeqMatcher(pattern.substring(0, 1), m.isIgnoreCase());
    } else {
      return matcher;
    }
  }

  /**
   * Get the suffix matcher. This is similar to {@link #tail(Matcher)} except that it intended
   * to be used with {@link #getPrefix(Matcher)}
   */
  static Matcher getSuffix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return zm.next();
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      return pattern.length() <= 1
          ? TrueMatcher.INSTANCE
          : new CharSeqMatcher(pattern.substring(1), m.isIgnoreCase());
    } else {
      return TrueMatcher.INSTANCE;
    }
  }

  /** Compute the set of trigrams for an input string. */
  public static SortedSet<String> computeTrigrams(String input) {
    if (input.length() < 3) {
      return Collections.emptySortedSet();
    } else {
      SortedSet<String> trigrams = new TreeSet<>();
      int n = input.length() - 3;
      for (int i = 0; i <= n; ++i) {
        trigrams.add(input.substring(i, i + 3));
      }
      return trigrams;
    }
  }
}
