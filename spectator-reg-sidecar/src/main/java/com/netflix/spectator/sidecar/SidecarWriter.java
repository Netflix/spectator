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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Base type for writer that accepts SpectatorD line protocol. */
abstract class SidecarWriter implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SidecarWriter.class);

  /**
   * Create a new writer based on a location string.
   */
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  static SidecarWriter create(String location) {
    try {
      if ("none".equals(location)) {
        return new NoopWriter();
      } else if ("stderr".equals(location)) {
        return new PrintStreamWriter(location, System.err);
      } else if ("stdout".equals(location)) {
        return new PrintStreamWriter(location, System.out);
      } else if (location.startsWith("file://")) {
        OutputStream out = Files.newOutputStream(Paths.get(URI.create(location)));
        PrintStream stream = new PrintStream(out, false, "UTF-8");
        return new PrintStreamWriter(location, stream);
      } else if ("udp".equals(location)) {
        String host = "127.0.0.1";
        int port = 1234;
        SocketAddress address = new InetSocketAddress(host, port);
        return new UdpWriter(location, address);
      } else if (location.startsWith("udp://")) {
        URI uri = URI.create(location);
        String host = uri.getHost();
        int port = uri.getPort();
        SocketAddress address = new InetSocketAddress(host, port);
        return new UdpWriter(location, address);
      } else {
        throw new IllegalArgumentException("unsupported location: " + location);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private final String location;

  private volatile boolean suppressWarnings;

  SidecarWriter(String location) {
    this.location = location;
    this.suppressWarnings = false;
  }

  abstract void writeImpl(String line) throws IOException;

  void write(String line) {
    try {
      LOGGER.trace("writing to {}: {}", location, line);
      writeImpl(line);
    } catch (IOException e) {
      // Some writers such as the UDP writer can be quite noisy if the sidecar is not present.
      // To avoid spamming the user with warnings, they will be suppressed after a warning is
      // logged. Note, in some cases UDP writes will fail without throwing an exception.
      if (!suppressWarnings) {
        LOGGER.warn("write to {} failed: {}", location, line, e);
        suppressWarnings = true;
      }
    }
  }

  void write(String prefix, long value) {
    write(prefix + value);
  }

  void write(String prefix, double value) {
    write(prefix + value);
  }
}
