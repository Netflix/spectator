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
package com.netflix.spectator.impl.matcher;

/** Matcher that matches if the position is at the end of the input string. */
enum EndMatcher implements Matcher {

  /** Singleton instance. */
  INSTANCE;

  @Override
  public int matches(String str, int start, int length) {
    return (start == str.length()) ? start : Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return 0;
  }

  @Override
  public boolean isEndAnchored() {
    return true;
  }

  @Override
  public String toString() {
    return "$";
  }
}
