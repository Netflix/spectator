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

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Base-class for TDigestWriter implementations. This class will take care of mapping a set of
 * measurements into capped-size byte buffers.
 */
abstract class TDigestWriter implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(TDigestWriter.class);

  /** Kinesis has a 50k limit for the record size. */
  static final int BUFFER_SIZE = 50000;

  /**
   * Minimum amount of free space for the buffer to try and write another measurement. If less
   * space is available, go ahead and flush the buffer.
   */
  static final int MIN_FREE = 4096;

  private final ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBufferOutputStream out = new ByteBufferOutputStream(buf, 2);

  private final Json json;
  private final Map<String, String> commonTags;

  /** Create a new instance. */
  TDigestWriter(Registry registry, TDigestConfig config) {
    this.json = new Json(registry);
    this.commonTags = config.getCommonTags();
  }

  /**
   * Writes a buffer of data.
   *
   * @param buffer
   *     Buffer to write to the underlying storage. The buffer will be setup so it is ready to
   *     consume, i.e., position=0 and limit=N where N is the amount of data to write. No
   *     guarantees are made about data in the remaining part of the buffer. The buffer will be
   *     reused when this method returns.
   */
  abstract void write(ByteBuffer buffer) throws IOException;

  /**
   * Write a list of measurements to some underlying storage.
   */
  void write(List<TDigestMeasurement> measurements) throws IOException {
    JsonGenerator gen = json.newGenerator(out);
    gen.writeStartArray();
    gen.flush();
    int pos = buf.position();
    for (TDigestMeasurement m : measurements) {
      json.encode(commonTags, m, gen);
      gen.flush();

      if (out.overflow()) {
        // Ignore the last entry written to the buffer
        out.setPosition(pos);
        gen.writeEndArray();
        gen.close();
        write(buf);

        // Reuse the buffer and write the current entry
        out.reset();
        gen = json.newGenerator(out);
        gen.writeStartArray();
        json.encode(commonTags, m, gen);
        gen.flush();

        // If a single entry is too big, then drop it
        if (out.overflow()) {
          LOGGER.warn("dropping measurement {}, serialized size exceeds the buffer cap", m.id());
          out.reset();
          gen = json.newGenerator(out);
          gen.writeStartArray();
          gen.flush();
        }

        pos = buf.position();
      } else if (buf.remaining() < MIN_FREE) {
        // Not enough free-space, go ahead and write
        gen.writeEndArray();
        gen.close();
        write(buf);

        // Reuse the buffer
        out.reset();
        gen = json.newGenerator(out);
        gen.writeStartArray();
        gen.flush();
        pos = buf.position();
      } else {
        pos = buf.position();
      }
    }

    // Write any data that is still in the buffer
    if (buf.position() > 1) {
      gen.writeEndArray();
      gen.close();
      write(buf);
    }
  }

  // This is needed to avoid issues with AutoCloseable.close throwing Exception
  @Override public void close() throws IOException {
  }
}
