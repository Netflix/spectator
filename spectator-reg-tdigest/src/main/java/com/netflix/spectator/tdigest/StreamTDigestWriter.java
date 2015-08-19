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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Write measurements to an output stream.
 */
public class StreamTDigestWriter extends TDigestWriter {

  private final DataOutputStream out;
  private final byte[] buf = new byte[BUFFER_SIZE];

  /** Create a new instance. */
  public StreamTDigestWriter(Registry registry, TDigestConfig config, OutputStream out) {
    this(registry, config, new DataOutputStream(out));
  }

  /** Create a new instance. */
  public StreamTDigestWriter(Registry registry, TDigestConfig config, DataOutputStream out) {
    super(registry, config);
    this.out = out;
  }

  @Override void write(ByteBuffer data) throws IOException {
    int len = data.limit();
    data.get(buf, 0, len);
    out.writeInt(len);
    out.write(buf, 0, len);
  }

  @Override public void close() throws IOException {
    out.close();
  }
}
