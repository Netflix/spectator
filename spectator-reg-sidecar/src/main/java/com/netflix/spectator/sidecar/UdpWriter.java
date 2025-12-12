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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/** Writer that outputs data to UDP socket. */
final class UdpWriter extends SidecarWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(UdpWriter.class);

  private final SocketAddress address;
  private final ReentrantLock lock;
  private volatile DatagramChannel channel;

  /** Create a new instance. */
  UdpWriter(String location, SocketAddress address) throws IOException {
    super(location);
    this.address = address;
    this.lock = new ReentrantLock();
    connect();
  }

  private void connect() throws IOException {
    DatagramChannel newChannel = DatagramChannel.open();
    try {
      newChannel.connect(address);
      channel = newChannel;
    } catch (Exception e) {
      try {
        newChannel.close();
      } catch (IOException ignored) {
        // Suppress close exception during error handling
      }
      throw e;
    }
  }

  @Override public void writeImpl(String line) throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
    DatagramChannel ch = channel;
    try {
      ch.write(buffer);
    } catch (ClosedChannelException e) {
      lock.lock();
      try {
        // Double-check: another thread may have already reconnected
        if (channel == ch) {
          try {
            connect();
            // After successful reconnection, retry the write once
            buffer.rewind();
            channel.write(buffer);
            // Write succeeded after reconnection
          } catch (IOException ex) {
            LOGGER.warn("channel closed, failed to reconnect", ex);
            ex.initCause(e);
            throw ex;
          }
        } else {
          // Another thread reconnected, retry the write once with new channel
          try {
            buffer.rewind();
            channel.write(buffer);
            // Write succeeded with reconnected channel
          } catch (IOException ex) {
            LOGGER.warn("failed to write after reconnection by another thread", ex);
            ex.initCause(e);
            throw ex;
          }
        }
      } finally {
        lock.unlock();
      }
    }
  }

  @Override public void close() throws IOException {
    lock.lock();
    try {
      if (channel != null) {
        channel.close();
      }
    } finally {
      lock.unlock();
    }
  }
}
