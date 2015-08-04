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

import com.netflix.spectator.api.Registry;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Reader based on an underlying {@link java.io.InputStream}.
 */
public class StreamTDigestReader implements TDigestReader {

  private final Json json;
  private final DataInputStream in;

  private final byte[] buf = new byte[TDigestWriter.BUFFER_SIZE];

  /** Create a new instance. */
  public StreamTDigestReader(Registry registry, InputStream in) {
    this(registry, new DataInputStream(in));
  }

  /** Create a new instance. */
  public StreamTDigestReader(Registry registry, DataInputStream in) {
    this.json = new Json(registry);
    this.in = in;
  }

  @Override public List<TDigestMeasurement> read() throws IOException {
    if (in.available() == 0) {
      return Collections.emptyList();
    } else {
      int size = in.readInt();
      if (size > buf.length) {
        throw new IOException("buffer exceeds max size (" + size + " > " + buf.length + ")");
      }
      int length = in.read(buf, 0, size);
      if (length != size) {
        throw new IOException("unexpected end of stream");
      }
      return json.decode(buf, 0, length);
    }
  }

  @Override public void close() throws IOException {
    in.close();
  }
}
