/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.tdigest;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Output stream with an underlying ByteBuffer. If the amount of data written exceeds the
 * capacity of the buffer, then the overflow flag will be set.
 */
final class ByteBufferOutputStream extends OutputStream implements DataOutput {

  private final ByteBuffer buf;
  private final int reserve;
  private boolean overflow;

  /**
   * Create a new instance.
   *
   * @param buf
   *     Buffer to write data into.
   * @param reserve
   *     Amount of space to reserve when checking the capacity of the buffer. The common use-case
   *     is for writing records that can be at most size X. The reserve param is used to indicate
   *     how much space is needed for closing the record, e.g., to add the ']' to terminate a
   *     a json array.
   */
  ByteBufferOutputStream(ByteBuffer buf, int reserve) {
    super();
    this.buf = buf;
    this.reserve = reserve;
    reset();
  }

  /** Return the underlying buffer. */
  ByteBuffer buffer() {
    return buf;
  }

  /** Clear the buffer and overflow flag. */
  void reset() {
    buf.clear();
    overflow = false;
  }

  /** Returns true if the amount of data written exceeds the capacity of the buffer. */
  boolean overflow() {
    return overflow;
  }

  private boolean checkCapacity(int size) {
    if (buf.remaining() < size + reserve) {
      overflow = true;
    }
    return !overflow;
  }

  @Override public void writeBoolean(boolean v) throws IOException {
    if (checkCapacity(1)) {
      buf.put((byte) (v ? 1 : 0));
    }
  }

  @Override public void writeByte(int v) throws IOException {
    if (checkCapacity(1)) {
      buf.put((byte) v);
    }
  }

  @Override public void writeShort(int v) throws IOException {
    if (checkCapacity(2)) {
      buf.putShort((short) v);
    }
  }

  @Override public void writeChar(int v) throws IOException {
    if (checkCapacity(2)) {
      buf.putChar((char) v);
    }
  }

  @Override public void writeInt(int v) throws IOException {
    if (checkCapacity(4)) {
      buf.putInt((int) v);
    }
  }

  @Override public void writeLong(long v) throws IOException {
    if (checkCapacity(8)) {
      buf.putLong((long) v);
    }
  }

  @Override public void writeFloat(float v) throws IOException {
    if (checkCapacity(4)) {
      buf.putFloat((float) v);
    }
  }

  @Override public void writeDouble(double v) throws IOException {
    if (checkCapacity(8)) {
      buf.putDouble((double) v);
    }
  }

  @Override public void writeBytes(String s) throws IOException {
    writeUTF(s);
  }

  @Override public void writeChars(String s) throws IOException {
    writeUTF(s);
  }

  @Override public void writeUTF(String s) throws IOException {
    byte[] data = s.getBytes("UTF-8");
    if (checkCapacity(4 + data.length)) {
      buf.putInt(data.length);
      buf.put(data);
    }
  }

  @Override public void write(int b) throws IOException {
    if (checkCapacity(1)) {
      buf.put((byte) b);
    }
  }

  /** Flip the buffer so it is ready to be consumed. */
  @Override public void close() throws IOException {
    buf.flip();
  }
}
