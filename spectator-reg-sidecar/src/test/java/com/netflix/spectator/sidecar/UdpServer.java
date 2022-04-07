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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

class UdpServer implements Closeable {

  private final DatagramChannel channel;

  UdpServer() throws IOException {
    channel = DatagramChannel.open();
    channel.bind(new InetSocketAddress("localhost", 0));
  }

  String address() throws IOException {
    InetSocketAddress addr = (InetSocketAddress) channel.getLocalAddress();
    return "udp://" + addr.getHostName() + ":" + addr.getPort();
  }

  String read() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    channel.receive(buffer);
    int length = buffer.position();
    return new String(buffer.array(), 0, length, StandardCharsets.UTF_8);
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
