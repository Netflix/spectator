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
 * Helper for parsing Netflix server group names that follow the Frigga conventions. For
 * more information see the IEP documentation for
 * <a href="https://github.com/Netflix/iep/tree/master/iep-nflxenv#server-group-settings">server groups</a>.
 *
 * <p>Frigga is not used for the actual parsing as it is quite inefficient. See the
 * ServerGroupParsing benchmark for a comparison.</p>
 */
public class ServerGroup {

  /**
   * Create a new instance of a server group object by parsing the group name.
   */
  public static ServerGroup parse(String asg) {
    int d1 = asg.indexOf('-');
    int d2 = asg.indexOf('-', d1 + 1);
    int dN = asg.lastIndexOf('-');
    if (dN < 0 || !isSequence(asg, dN)) {
      dN = asg.length();
    }
    return new ServerGroup(asg, d1, d2, dN);
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

  private static String substr(String str, int s, int e) {
    return (s >= e) ? null : str.substring(s, e);
  }

  private final String asg;
  private final int d1;
  private final int d2;
  private final int dN;

  /**
   * Create a new instance of the server group.
   *
   * @param asg
   *     Raw group name received from the user.
   * @param d1
   *     Position of the first dash or -1 if there are no dashes in the input.
   * @param d2
   *     Position of the second dash or -1 if there is not a second dash in the input.
   * @param dN
   *     Position indicating the end of the cluster name. For a server group with a
   *     sequence this will be the final dash. If the sequence is not present, then
   *     it will be the end of the string.
   */
  ServerGroup(String asg, int d1, int d2, int dN) {
    this.asg = asg;
    this.d1 = d1;
    this.d2 = d2;
    this.dN = dN;
  }

  /** Return the application for the server group or null if invalid. */
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

  /** Return the cluster name for the server group or null if invalid. */
  public String cluster() {
    if (d1 == 0) {
      // Application portion is empty
      return null;
    } else {
      return (dN > 0 && dN == asg.length()) ? asg() : substr(asg, 0, dN);
    }
  }

  /** Return the server group name or null if invalid. */
  public String asg() {
    return (d1 != 0 && dN > 0) ? asg : null;
  }

  /** If the server group has a stack, then return the stack name. Otherwise return null. */
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

  /** If the server group has a detail, then return the detail name. Otherwise return null. */
  public String detail() {
    return (d1 != 0 && d2 > 0) ? substr(asg, d2 + 1, dN) : null;
  }

  /** If the server group has a sequence number, then return it. Otherwise return null. */
  public String sequence() {
    return dN == asg.length() ? null : substr(asg, dN + 1, asg.length());
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ServerGroup that = (ServerGroup) o;
    return d1 == that.d1
        && d2 == that.d2
        && dN == that.dN
        && Objects.equals(asg, that.asg);
  }

  @Override public int hashCode() {
    return Objects.hash(asg, d1, d2, dN);
  }

  @Override public String toString() {
    return "ServerGroup(" + asg + ", " + d1 + ", " + d2 + ", " + dN + ")";
  }
}
