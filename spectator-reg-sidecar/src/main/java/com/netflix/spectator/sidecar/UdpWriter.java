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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

/** Writer that outputs data to UDP socket. */
final class UdpWriter extends SidecarWriter {

  private final DatagramChannel channel;

  /** Create a new instance. */
  UdpWriter(String location, SocketAddress address) throws IOException {
    super(location);
    this.channel = DatagramChannel.open();
    this.channel.connect(address);
  }

  @Override public void writeImpl(String line) throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
    channel.write(buffer);
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
