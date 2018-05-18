/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.ipc;

import java.util.Objects;

/**
 * This is an alternate implementation that uses String for everything rather than wrapping
 * with a CharBuffer to reduce allocations. It is used for the benchmark to verify that the
 * CharBuffer approach actually provides a benefit.
 */
public class StringServerGroup {

  /**
   * Create a new instance of a server group object by parsing the group name.
   */
  public static StringServerGroup parse(String asg) {
    int d1 = asg.indexOf('-');
    int d2 = asg.indexOf('-', d1 + 1);
    int dN = asg.lastIndexOf('-');
    if (dN < 0 || !isSequence(asg, dN)) {
      dN = asg.length();
    }
    return new StringServerGroup(asg, d1, d2, dN);
  }

  /**
   * Check if the last portion of the server group name is a version sequence (v\d+).
   */
  private static boolean isSequence(String asg, int dN) {
    int length = asg.length();
    if (length - dN < 3 || asg.charAt(dN + 1) != 'v') {
      return false;
    }
    for (int i = dN + 2; i < length; ++i) {
      if (!Character.isDigit(asg.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * The substring method will create a copy of the substring in JDK 8 and probably newer
   * versions. To reduce the number of allocations we use a char buffer to return a view
   * with just that subset.
   */
  private static String substr(String str, int s, int e) {
    return (s >= e) ? null : str.substring(s, e);
  }

  private final String asg;
  private final int d1;
  private final int d2;
  private final int dN;

  StringServerGroup(String asg, int d1, int d2, int dN) {
    this.asg = asg;
    this.d1 = d1;
    this.d2 = d2;
    this.dN = dN;
  }

  public String app() {
    if (d1 < 0) {
      // No stack or detail is present
      return asg.length() > 0 ? asg : null;
    } else if (d1 == 0) {
      // Application portion is empty
      return null;
    } else {
      // Application is present along with stack, detail, or sequence
      return substr(asg, 0, d1);
    }
  }

  public String cluster() {
    if (d1 == 0) {
      // Application portion is empty
      return null;
    } else {
      return (dN > 0 && dN == asg.length()) ? asg() : substr(asg, 0, dN);
    }
  }

  public String asg() {
    return (d1 != 0 && dN > 0) ? asg : null;
  }

  public String stack() {
    if (d1 <= 0) {
      // No stack, detail or sequence is present
      return null;
    } else if (d2 < 0) {
      // Stack, but no detail is present
      return substr(asg, d1 + 1, dN);
    } else {
      // Stack and at least one of detail or sequence is present
      return substr(asg, d1 + 1, d2);
    }
  }

  public String detail() {
    return (d1 != 0 && d2 > 0) ? substr(asg, d2 + 1, dN) : null;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StringServerGroup that = (StringServerGroup) o;
    return d1 == that.d1 &&
        d2 == that.d2 &&
        dN == that.dN &&
        Objects.equals(asg, that.asg);
  }

  @Override public int hashCode() {
    return Objects.hash(asg, d1, d2, dN);
  }

  @Override public String toString() {
    return "StringServerGroup(" + asg + ", " + d1 + ", " + d2 + ", " + dN + ")";
  }
}
