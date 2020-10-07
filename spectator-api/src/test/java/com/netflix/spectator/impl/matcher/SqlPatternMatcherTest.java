/*
 * Copyright 2014-2020 Netflix, Inc.
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SqlPatternMatcherTest extends AbstractPatternMatcherTest {

  @Override
  protected void testRE(String regex, String value) {
    PatternMatcher matcher = PatternMatcher.compile(regex);
    String sqlPattern = PatternMatcher.compile(regex).toSqlPattern();
    if (sqlPattern != null) {
      String desc = "[" + matcher + "] => [" + sqlPattern + "]";
      if (matcher.matches(value)) {
        Assertions.assertTrue(sqlMatches(sqlPattern, value), desc + " should match " + value);
      } else {
        Assertions.assertFalse(sqlMatches(sqlPattern, value), desc + " shouldn't match " + value);
      }
    }
  }

  private boolean sqlMatches(String pattern, String value) {
    try {
      Class.forName("org.hsqldb.jdbcDriver");
      try (Connection con = DriverManager.getConnection("jdbc:hsqldb:mem:test")) {
        try (Statement stmt = con.createStatement()) {
          String v = enquoteLiteral(value);
          String p = enquoteLiteral(pattern);
          stmt.executeUpdate("drop table if exists test");
          stmt.executeUpdate("create table test(v clob)");
          stmt.executeUpdate("insert into test (v) values (" + v + ")");
          String query = "select * from test where v like " + p + " escape '\\'";
          try (ResultSet rs = stmt.executeQuery(query)) {
            return rs.next();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Corresponding method on the Statement interface wasn't added until JDK9 so we cannot
  // use it.
  private String enquoteLiteral(String str) {
    return "'" + str.replace("'", "''") + "'";
  }
}
