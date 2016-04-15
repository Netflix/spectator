/**
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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.RegistryConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRulesException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class ConfigTest {

  @Test
  public void usingMap() {
    Map<String, String> props = new HashMap<>();
    //props.put("propagateWarnings", "false");
    RegistryConfig cfg = Config.usingMap(props);
    Assert.assertFalse(cfg.propagateWarnings());
  }

  private void assertNoSuchElement(TestConfig cfg, Consumer<TestConfig> f) {
    try {
      f.accept(cfg);
      Assert.fail("should have thrown exception for missing key");
    } catch (NoSuchElementException e) {
      // This is expected, other exceptions should propagate and fail
    }
  }

  private void assertNumberFormat(TestConfig cfg, Consumer<TestConfig> f) {
    try {
      f.accept(cfg);
      Assert.fail("should have thrown exception for malformed input");
    } catch (NumberFormatException e) {
      // This is expected, other exceptions should propagate and fail
    }
  }

  private void assertStringIndexOutOfBounds(TestConfig cfg, Consumer<TestConfig> f) {
    try {
      f.accept(cfg);
      Assert.fail("should have thrown exception for malformed input");
    } catch (StringIndexOutOfBoundsException e) {
      // This is expected, other exceptions should propagate and fail
    }
  }

  private void assertDateTimeParse(TestConfig cfg, Consumer<TestConfig> f) {
    try {
      f.accept(cfg);
      Assert.fail("should have thrown exception for malformed input");
    } catch (DateTimeParseException e) {
      // This is expected, other exceptions should propagate and fail
    }
  }

  private void assertZoneRules(TestConfig cfg, Consumer<TestConfig> f) {
    try {
      f.accept(cfg);
      Assert.fail("should have thrown exception for malformed input");
    } catch (ZoneRulesException e) {
      // This is expected, other exceptions should propagate and fail
    }
  }

  @Test
  public void proxyBooleanWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueBoolean);

    props.put("valueBoolean", "false");
    Assert.assertFalse(cfg.valueBoolean());

    props.put("valueBoolean", "true");
    Assert.assertTrue(cfg.valueBoolean());

    props.put("valueBoolean", "abc");
    Assert.assertFalse(cfg.valueBoolean());
  }

  @Test
  public void proxyBooleanPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveBoolean);

    props.put("primitiveBoolean", "false");
    Assert.assertFalse(cfg.primitiveBoolean());

    props.put("primitiveBoolean", "true");
    Assert.assertTrue(cfg.primitiveBoolean());

    props.put("primitiveBoolean", "abc");
    Assert.assertFalse(cfg.primitiveBoolean());
  }

  @Test
  public void proxyByteWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueByte);

    props.put("valueByte", "0");
    Assert.assertEquals(Byte.valueOf((byte) 0), cfg.valueByte());

    props.put("valueByte", "42");
    Assert.assertEquals(Byte.valueOf((byte) 42), cfg.valueByte());

    props.put("valueByte", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueByte);
  }

  @Test
  public void proxyBytePrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveByte);

    props.put("primitiveByte", "0");
    Assert.assertEquals((byte) 0, cfg.primitiveByte());

    props.put("primitiveByte", "42");
    Assert.assertEquals((byte) 42, cfg.primitiveByte());

    props.put("primitiveByte", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveByte);
  }

  @Test
  public void proxyShortWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueShort);

    props.put("valueShort", "0");
    Assert.assertEquals(Short.valueOf((short) 0), cfg.valueShort());

    props.put("valueShort", "42");
    Assert.assertEquals(Short.valueOf((short) 42), cfg.valueShort());

    props.put("valueShort", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueShort);
  }

  @Test
  public void proxyShortPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveShort);

    props.put("primitiveShort", "0");
    Assert.assertEquals((short) 0, cfg.primitiveShort());

    props.put("primitiveShort", "42");
    Assert.assertEquals((short) 42, cfg.primitiveShort());

    props.put("primitiveShort", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveShort);
  }

  @Test
  public void proxyIntegerWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueInteger);

    props.put("valueInteger", "0");
    Assert.assertEquals(Integer.valueOf(0), cfg.valueInteger());

    props.put("valueInteger", "42");
    Assert.assertEquals(Integer.valueOf(42), cfg.valueInteger());

    props.put("valueInteger", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueInteger);
  }

  @Test
  public void proxyIntegerPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveInteger);

    props.put("primitiveInteger", "0");
    Assert.assertEquals(0, cfg.primitiveInteger());

    props.put("primitiveInteger", "42");
    Assert.assertEquals(42, cfg.primitiveInteger());

    props.put("primitiveInteger", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveInteger);
  }

  @Test
  public void proxyLongWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueLong);

    props.put("valueLong", "0");
    Assert.assertEquals(Long.valueOf(0L), cfg.valueLong());

    props.put("valueLong", "42");
    Assert.assertEquals(Long.valueOf(42L), cfg.valueLong());

    props.put("valueLong", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueLong);
  }

  @Test
  public void proxyLongPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveLong);

    props.put("primitiveLong", "0");
    Assert.assertEquals(0L, cfg.primitiveLong());

    props.put("primitiveLong", "42");
    Assert.assertEquals(42L, cfg.primitiveLong());

    props.put("primitiveLong", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveLong);
  }

  @Test
  public void proxyFloatWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueFloat);

    props.put("valueFloat", "0");
    Assert.assertEquals(Float.valueOf(0.0f), cfg.valueFloat());

    props.put("valueFloat", "42");
    Assert.assertEquals(Float.valueOf(42.0f), cfg.valueFloat());

    props.put("valueFloat", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueFloat);
  }

  @Test
  public void proxyFloatPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveFloat);

    props.put("primitiveFloat", "0");
    Assert.assertEquals(0.0f, cfg.primitiveFloat(), 1e-9);

    props.put("primitiveFloat", "42");
    Assert.assertEquals(42.0f, cfg.primitiveFloat(), 1e-9);

    props.put("primitiveFloat", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveFloat);
  }

  @Test
  public void proxyDoubleWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueDouble);

    props.put("valueDouble", "0");
    Assert.assertEquals(Double.valueOf(0.0), cfg.valueDouble());

    props.put("valueDouble", "42");
    Assert.assertEquals(Double.valueOf(42.0), cfg.valueDouble());

    props.put("valueDouble", "abczyx");
    assertNumberFormat(cfg, TestConfig::valueDouble);
  }

  @Test
  public void proxyDoublePrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveDouble);

    props.put("primitiveDouble", "0");
    Assert.assertEquals(0.0, cfg.primitiveDouble(), 1e-9);

    props.put("primitiveDouble", "42");
    Assert.assertEquals(42.0, cfg.primitiveDouble(), 1e-9);

    props.put("primitiveDouble", "abczyx");
    assertNumberFormat(cfg, TestConfig::primitiveDouble);
  }

  @Test
  public void proxyCharacterWrapper() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueCharacter);

    props.put("valueCharacter", "0");
    Assert.assertEquals(Character.valueOf('0'), cfg.valueCharacter());

    props.put("valueCharacter", "42");
    Assert.assertEquals(Character.valueOf('4'), cfg.valueCharacter());

    props.put("valueCharacter", "");
    assertStringIndexOutOfBounds(cfg, TestConfig::valueCharacter);
  }

  @Test
  public void proxyCharacterPrimitive() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::primitiveCharacter);

    props.put("primitiveCharacter", "0");
    Assert.assertEquals('0', cfg.primitiveCharacter());

    props.put("primitiveCharacter", "42");
    Assert.assertEquals('4', cfg.primitiveCharacter());

    props.put("primitiveCharacter", "");
    assertStringIndexOutOfBounds(cfg, TestConfig::primitiveCharacter);
  }

  @Test
  public void proxyString() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueString);

    props.put("valueString", "0");
    Assert.assertEquals("0", cfg.valueString());

    props.put("valueString", "forty-two: 42");
    Assert.assertEquals("forty-two: 42", cfg.valueString());

    props.put("valueString", "");
    Assert.assertEquals("", cfg.valueString());
  }

  @Test
  public void proxyDuration() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueDuration);

    props.put("valueDuration", "PT5M");
    Assert.assertEquals(Duration.ofMinutes(5), cfg.valueDuration());

    props.put("valueDuration", "abczyx");
    assertDateTimeParse(cfg, TestConfig::valueDuration);
  }

  @Test
  public void proxyPeriod() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valuePeriod);

    props.put("valuePeriod", "P7M");
    Assert.assertEquals(Period.ofMonths(7), cfg.valuePeriod());

    props.put("valuePeriod", "abczyx");
    assertDateTimeParse(cfg, TestConfig::valuePeriod);
  }

  @Test
  public void proxyInstant() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueInstant);

    props.put("valueInstant", "2007-12-03T10:15:30.00Z");
    Instant expected = ZonedDateTime.of(2007, 12, 3, 10, 15, 30, 0, ZoneOffset.UTC).toInstant();
    Assert.assertEquals(expected, cfg.valueInstant());

    props.put("valueInstant", "abczyx");
    assertDateTimeParse(cfg, TestConfig::valueInstant);
  }

  @Test
  public void proxyZonedDateTime() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueZonedDateTime);

    props.put("valueZonedDateTime", "2007-12-03T10:15:30.00Z");
    ZonedDateTime expected = ZonedDateTime.of(2007, 12, 3, 10, 15, 30, 0, ZoneOffset.UTC);
    Assert.assertEquals(expected, cfg.valueZonedDateTime());

    props.put("valueZonedDateTime", "abczyx");
    assertDateTimeParse(cfg, TestConfig::valueZonedDateTime);
  }

  @Test
  public void proxyZoneId() {
    Map<String, String> props = new HashMap<>();
    TestConfig cfg = Config.usingMap(TestConfig.class, props);
    assertNoSuchElement(cfg, TestConfig::valueZoneId);

    props.put("valueZoneId", "UTC");
    Assert.assertEquals(ZoneId.of("UTC"), cfg.valueZoneId());

    props.put("valueZoneId", "US/Pacific");
    Assert.assertEquals(ZoneId.of("US/Pacific"), cfg.valueZoneId());

    props.put("valueZoneId", "abczyx");
    assertZoneRules(cfg, TestConfig::valueZoneId);
  }

  public interface TestConfig {
    Boolean valueBoolean();
    boolean primitiveBoolean();

    Byte valueByte();
    byte primitiveByte();
    Short valueShort();
    short primitiveShort();
    Integer valueInteger();
    int primitiveInteger();
    Long valueLong();
    long primitiveLong();

    Float valueFloat();
    float primitiveFloat();
    Double valueDouble();
    double primitiveDouble();

    Character valueCharacter();
    char primitiveCharacter();

    String valueString();

    Duration valueDuration();
    Period valuePeriod();
    Instant valueInstant();
    ZonedDateTime valueZonedDateTime();
    ZoneId valueZoneId();
  }
}
