/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.sidecar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PrintStreamWriterTest {

  private SidecarWriter newWriter(Path path) throws IOException {
    String location = "file://" + path.toString();
    return SidecarWriter.create(location);
  }

  @Test
  public void file() throws IOException {
    Path tmp = Files.createTempFile("spectator", "test");
    try (SidecarWriter w = newWriter(tmp)) {
      w.write("foo");
      w.write("bar");
    }

    List<String> lines = Files.readAllLines(tmp, StandardCharsets.UTF_8);
    Assertions.assertEquals(2, lines.size());
    Assertions.assertEquals("foo", lines.get(0));
    Assertions.assertEquals("bar", lines.get(1));

    Files.deleteIfExists(tmp);
  }

  @Test
  public void concurrentWrites() throws Exception {
    Path tmp = Files.createTempFile("spectator", "test");
    try (SidecarWriter w = newWriter(tmp)) {
      Thread[] threads = new Thread[4];
      for (int i = 0; i < threads.length; ++i) {
        final int n = i;
        Runnable task = () -> {
          int base = n * 10_000;
          for (int j = 0; j < 10_000; ++j) {
            w.write("" + (base + j));
          }
        };
        threads[i] = new Thread(task);
        threads[i].start();
      }
      for (Thread t : threads) {
        t.join();
      }
    }

    List<String> lines = Files.readAllLines(tmp, StandardCharsets.UTF_8);
    int N = 40_000;
    Assertions.assertEquals(N, lines.size());
    Assertions.assertEquals(N * (N - 1) / 2, lines.stream().mapToInt(Integer::parseInt).sum());

    Files.deleteIfExists(tmp);
  }
}
