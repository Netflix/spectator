/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.impl.Hash64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the {@link DistinctCountSketch} hashing and register encoding against a committed
 * set of cross-language test vectors. The same vector file is meant to be copied to the
 * spectatord implementation so that both produce identical registers for the same inputs,
 * which is what allows their sketches to merge (see the design doc, hash contract section).
 *
 * <p>The vectors are generated from this reference implementation. To regenerate after an
 * intentional change, set the {@code GENERATE_VECTORS=true} environment variable and review
 * the diff:</p>
 *
 * <pre>
 *   GENERATE_VECTORS=true ./gradlew :spectator-api:test \
 *       --tests "*DistinctCountSketchVectorTest.generate"
 * </pre>
 *
 * <p>Any unintentional change to the hash or register encoding is a compatibility break and
 * will fail {@link #encodingVectors()} / {@link #sketchVectors()}.</p>
 */
public class DistinctCountSketchVectorTest {

  private static final String RESOURCE = "distinct_count_sketch_test_vectors.txt";

  // Working directory for the test task is the module directory, so the source resource can
  // be regenerated in place and read back without rebuilding.
  private static final String SOURCE_PATH = "src/test/resources/" + RESOURCE;

  private static final int REGISTERS = DistinctCountSketch.REGISTERS;

  private Registry newRegistry() {
    return new DefaultRegistry(Clock.SYSTEM, k -> null);
  }

  // --- Inputs -------------------------------------------------------------

  private enum Type { LONG, STR, BYTES }

  private static final class Input {
    private final Type type;
    private final long longValue;
    private final String stringValue;
    private final byte[] bytesValue;

    private Input(Type type, long l, String s, byte[] b) {
      this.type = type;
      this.longValue = l;
      this.stringValue = s;
      this.bytesValue = b;
    }

    static Input ofLong(long v) {
      return new Input(Type.LONG, v, null, null);
    }

    static Input ofString(String v) {
      return new Input(Type.STR, 0L, v, null);
    }

    static Input ofBytes(byte[] v) {
      return new Input(Type.BYTES, 0L, null, v);
    }
  }

  // Curated inputs covering the three overloads and the interesting edges: signed/extreme
  // longs, empty/ascii/multi-byte UTF-8/supplementary strings, a string past the long-string
  // hashing threshold, and byte sequences including invalid UTF-8.
  private static List<Input> encodingInputs() {
    List<Input> inputs = new ArrayList<>();

    long[] longs = {
        0L, 1L, -1L, 2L, 7L, 42L, 100L, 1000L, 1_000_000L,
        123456789L, -123456789L, 0xCAFEBABEL, Long.MAX_VALUE, Long.MIN_VALUE
    };
    for (long v : longs) {
      inputs.add(Input.ofLong(v));
    }

    String[] strings = {
        "", "a", "ab", "abc", "user-12345", "192.168.0.1", "2001:db8::1",
        "key=value,other:thing", "héllo", "naïve", "世界", "日本語",
        "😀",            // U+1F600 grinning face (supplementary)
        "👍🏽" // thumbs up + skin tone modifier
    };
    for (String v : strings) {
      inputs.add(Input.ofString(v));
    }
    // String longer than Hash64's long-string threshold to exercise that path.
    StringBuilder longStr = new StringBuilder();
    for (int i = 0; i < 10; ++i) {
      longStr.append("/api/v1/users/abcd1234/");
    }
    inputs.add(Input.ofString(longStr.toString()));

    inputs.add(Input.ofBytes(new byte[0]));
    inputs.add(Input.ofBytes(new byte[] {0x00}));
    inputs.add(Input.ofBytes(new byte[] {(byte) 0xff}));
    inputs.add(Input.ofBytes(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}));
    inputs.add(Input.ofBytes(new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef}));
    inputs.add(Input.ofBytes(new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd})); // invalid UTF-8

    return inputs;
  }

  // Specs for the full-sketch-state vectors. Each is recorded into one sketch and the
  // resulting 64 registers are captured, exercising the max-register accumulation including
  // collisions where the larger rho wins.
  private static final String[] SKETCH_SPECS = {
      "encoding-inputs",
      "longs:0:100000"
  };

  private static List<Input> inputsForSpec(String spec) {
    if ("encoding-inputs".equals(spec)) {
      return encodingInputs();
    }
    if (spec.startsWith("longs:")) {
      String[] parts = spec.split(":");
      long start = Long.parseLong(parts[1]);
      long end = Long.parseLong(parts[2]);
      List<Input> inputs = new ArrayList<>();
      for (long i = start; i < end; ++i) {
        inputs.add(Input.ofLong(i));
      }
      return inputs;
    }
    throw new IllegalArgumentException("unknown sketch spec: " + spec);
  }

  // --- Recording helpers --------------------------------------------------

  private static void recordInto(DistinctCountSketch sketch, Input in) {
    switch (in.type) {
      case LONG:  sketch.record(in.longValue); break;
      case STR:   sketch.record(in.stringValue); break;
      case BYTES: sketch.record(in.bytesValue); break;
      default: throw new IllegalStateException();
    }
  }

  private static long hashOf(Input in) {
    switch (in.type) {
      case LONG:  return Hash64.hashLong(in.longValue);
      case STR:   return Hash64.hashString(in.stringValue);
      case BYTES: return Hash64.hashBytes(in.bytesValue);
      default: throw new IllegalStateException();
    }
  }

  // Record a single input and return the populated register as {index, rho}.
  private int[] recordOne(Input in) {
    Registry r = newRegistry();
    DistinctCountSketch sketch = DistinctCountSketch.get(r, r.createId("test"));
    recordInto(sketch, in);
    int[] result = null;
    for (Gauge g : r.gauges().toArray(Gauge[]::new)) {
      if ("test".equals(g.id().name())) {
        Assertions.assertNull(result, "more than one register populated");
        int index = Integer.parseInt(Utils.getTagValue(g.id(), "distinct").substring(1), 16);
        result = new int[] {index, (int) g.value()};
      }
    }
    Assertions.assertNotNull(result, "no register populated");
    return result;
  }

  // Record all inputs for a spec into one sketch and return the 64 register rho values.
  private int[] recordSketch(String spec) {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    for (Input in : inputsForSpec(spec)) {
      recordInto(sketch, in);
    }
    int[] regs = new int[REGISTERS];
    for (int i = 0; i < REGISTERS; ++i) {
      Id rid = id.withTags(Statistic.distinct, new BasicTag("distinct", String.format("R%02X", i)));
      double v = r.maxGauge(rid).value();
      regs[i] = Double.isNaN(v) ? 0 : (int) v;
    }
    return regs;
  }

  // --- Encoding for the vector file --------------------------------------

  private static String typeToken(Type type) {
    switch (type) {
      case LONG:  return "long";
      case STR:   return "str";
      case BYTES: return "bytes";
      default: throw new IllegalStateException();
    }
  }

  private static String encodeInput(Input in) {
    switch (in.type) {
      case LONG:  return Long.toString(in.longValue);
      case STR:   return escape(in.stringValue);
      case BYTES: return toHex(in.bytesValue);
      default: throw new IllegalStateException();
    }
  }

  private static Input decodeInput(String type, String value) {
    switch (type) {
      case "long":  return Input.ofLong(Long.parseLong(value));
      case "str":   return Input.ofString(unescape(value));
      case "bytes": return Input.ofBytes(fromHex(value));
      default: throw new IllegalArgumentException("unknown type: " + type);
    }
  }

  // Backslash escaping so a string value can never contain the tab separator or a newline.
  private static String escape(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      switch (c) {
        case '\\': b.append("\\\\"); break;
        case '\t': b.append("\\t"); break;
        case '\n': b.append("\\n"); break;
        case '\r': b.append("\\r"); break;
        default: b.append(c); break;
      }
    }
    return b.toString();
  }

  private static String unescape(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        switch (n) {
          case '\\': b.append('\\'); break;
          case 't':  b.append('\t'); break;
          case 'n':  b.append('\n'); break;
          case 'r':  b.append('\r'); break;
          default:   b.append(n); break;
        }
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  private static String toHex(byte[] bytes) {
    StringBuilder b = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      b.append(Character.forDigit((value >> 4) & 0xf, 16));
      b.append(Character.forDigit(value & 0xf, 16));
    }
    return b.toString();
  }

  private static byte[] fromHex(String hex) {
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; ++i) {
      int hi = Character.digit(hex.charAt(2 * i), 16);
      int lo = Character.digit(hex.charAt(2 * i + 1), 16);
      bytes[i] = (byte) ((hi << 4) | lo);
    }
    return bytes;
  }

  // --- Vector file IO -----------------------------------------------------

  private static List<String> readVectorLines() throws IOException {
    // Prefer the source file (so generation + verification stay consistent without a
    // rebuild); fall back to the classpath copy.
    if (Files.exists(Paths.get(SOURCE_PATH))) {
      return Files.readAllLines(Paths.get(SOURCE_PATH), StandardCharsets.UTF_8);
    }
    try (InputStream in = DistinctCountSketchVectorTest.class.getResourceAsStream("/" + RESOURCE)) {
      Assertions.assertNotNull(in, "missing vector resource: " + RESOURCE);
      List<String> lines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
      return lines;
    }
  }

  // --- Tests --------------------------------------------------------------

  @Test
  public void encodingVectors() throws IOException {
    List<String> lines = readVectorLines();
    boolean inSection = false;
    int checked = 0;
    for (String line : lines) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("[")) {
        inSection = "[encoding]".equals(line);
        continue;
      }
      if (!inSection) {
        continue;
      }
      String[] f = line.split("\t", -1);
      Assertions.assertEquals(5, f.length, "malformed encoding line: " + line);
      Input in = decodeInput(f[0], f[1]);
      long expectedHash = Long.parseUnsignedLong(f[2], 16);
      int expectedIndex = Integer.parseInt(f[3]);
      int expectedRho = Integer.parseInt(f[4]);

      Assertions.assertEquals(expectedHash, hashOf(in), "hash mismatch: " + line);
      int[] actual = recordOne(in);
      Assertions.assertEquals(expectedIndex, actual[0], "index mismatch: " + line);
      Assertions.assertEquals(expectedRho, actual[1], "rho mismatch: " + line);
      ++checked;
    }
    Assertions.assertTrue(checked > 0, "no encoding vectors were checked");
  }

  @Test
  public void sketchVectors() throws IOException {
    List<String> lines = readVectorLines();
    boolean inSection = false;
    int checked = 0;
    for (String line : lines) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("[")) {
        inSection = "[sketch]".equals(line);
        continue;
      }
      if (!inSection) {
        continue;
      }
      String[] f = line.split("\t", -1);
      Assertions.assertEquals(2, f.length, "malformed sketch line: " + line);
      String spec = f[0];
      String[] values = f[1].split(",", -1);
      Assertions.assertEquals(REGISTERS, values.length, "expected " + REGISTERS + " registers");
      int[] expected = new int[REGISTERS];
      for (int i = 0; i < REGISTERS; ++i) {
        expected[i] = Integer.parseInt(values[i]);
      }
      Assertions.assertArrayEquals(expected, recordSketch(spec), "sketch mismatch: " + spec);
      ++checked;
    }
    Assertions.assertTrue(checked > 0, "no sketch vectors were checked");
  }

  // Documents the byte serialization other implementations must match: a long is hashed as
  // its 8-byte little-endian representation, a string as its UTF-8 bytes. Both reduce to
  // xxHash64(bytes, seed=0).
  @Test
  public void serializationContract() {
    long[] longs = {0L, 1L, -1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE};
    for (long v : longs) {
      byte[] le = new byte[8];
      for (int i = 0; i < 8; ++i) {
        le[i] = (byte) (v >>> (8 * i));
      }
      Assertions.assertEquals(Hash64.hashBytes(le), Hash64.hashLong(v), "long " + v);
    }
    String[] strings = {"", "a", "héllo", "世界", "😀"};
    for (String s : strings) {
      Assertions.assertEquals(
          Hash64.hashBytes(s.getBytes(StandardCharsets.UTF_8)),
          Hash64.hashString(s),
          "str " + s);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "GENERATE_VECTORS", matches = "true")
  public void generate() throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("# Distinct count sketch cross-language test vectors.");
    lines.add("#");
    lines.add("# Generated from the spectator-java reference implementation");
    lines.add("# (DistinctCountSketch). Copy this file to other implementations (e.g.");
    lines.add("# spectatord) and verify they produce the same registers, so that sketches");
    lines.add("# from different clients merge correctly.");
    lines.add("#");
    lines.add("# Hash: xxHash64, seed 0. A long is hashed as its 8-byte little-endian");
    lines.add("# representation; a string as its UTF-8 bytes. Register index = low 6 bits of");
    lines.add("# the hash; rho = 1 + the number of leading zeros in the remaining 58 bits.");
    lines.add("#");
    lines.add("# [encoding]: <type>\\t<input>\\t<hash>\\t<index>\\t<rho>");
    lines.add("#   type  = long | str | bytes");
    lines.add("#   input = long: signed decimal; str: UTF-8 literal with \\\\, \\t, \\n, \\r");
    lines.add("#           escaped; bytes: lowercase hex");
    lines.add("#   hash  = unsigned 64-bit hash as 16 hex digits");
    lines.add("#   index = register index 0..63");
    lines.add("#   rho   = register value 1..59");
    lines.add("#");
    lines.add("# [sketch]: <spec>\\t<r0>,<r1>,...,<r63>");
    lines.add("#   spec = encoding-inputs (all inputs above) | longs:<start>:<end>");
    lines.add("#   followed by the 64 register rho values (0 = unused) after recording the");
    lines.add("#   spec's inputs into a single sketch.");
    lines.add("[encoding]");
    for (Input in : encodingInputs()) {
      int[] ir = recordOne(in);
      lines.add(String.join("\t",
          typeToken(in.type),
          encodeInput(in),
          String.format("%016x", hashOf(in)),
          Integer.toString(ir[0]),
          Integer.toString(ir[1])));
    }
    lines.add("[sketch]");
    for (String spec : SKETCH_SPECS) {
      int[] regs = recordSketch(spec);
      StringBuilder csv = new StringBuilder();
      for (int i = 0; i < regs.length; ++i) {
        if (i > 0) {
          csv.append(',');
        }
        csv.append(regs[i]);
      }
      lines.add(spec + "\t" + csv);
    }
    // Write canonical '\n' line endings (not the platform separator) so the committed file
    // is identical regardless of the platform it is regenerated on, and matches what the
    // BufferedReader/readAllLines parsers expect.
    StringBuilder content = new StringBuilder();
    for (String line : lines) {
      content.append(line).append('\n');
    }
    Files.write(Paths.get(SOURCE_PATH), content.toString().getBytes(StandardCharsets.UTF_8));
  }
}
