/*
 * Copyright 2014-2025 Netflix, Inc.
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UdpWriterTest {

  @Test
  public void udp() throws IOException {
    try (UdpServer server = new UdpServer()) {
      try (SidecarWriter w = SidecarWriter.create(server.address())) {
        w.write("foo");
        Assertions.assertEquals("foo", server.read());
        w.write("bar");
        Assertions.assertEquals("bar", server.read());
      }
    }
  }

  @Test
  public void udpReconnectIfClosed() throws IOException {
    try (UdpServer server = new UdpServer()) {
      try (SidecarWriter w = SidecarWriter.create(server.address())) {
        // Used to simulate close from something like an interrupt. The next write
        // will fail and it should try to reconnect.
        w.close();
        w.write("1");

        w.write("2");
        Assertions.assertEquals("2", server.read());
        w.write("3");
        Assertions.assertEquals("3", server.read());
      }
    }
  }

  // Disabled because it can have issues on CI
  @Test
  @Disabled
  public void concurrentWrites() throws Exception {
    List<String> lines = Collections.synchronizedList(new ArrayList<>());
    try (UdpServer server = new UdpServer()) {
      try (SidecarWriter w = SidecarWriter.create(server.address())) {
        Thread reader = new Thread(() -> {
          while (true) {
            try {
              String line = server.read();
              if (!"done".equals(line)) {
                lines.add(line);
              } else {
                break;
              }
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }
        });
        reader.start();

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

        w.write("done");
        reader.join();
      }
    }

    int N = 40_000;
    Assertions.assertEquals(N, lines.size());
    Assertions.assertEquals(N * (N - 1) / 2, lines.stream().mapToInt(Integer::parseInt).sum());
  }
}
