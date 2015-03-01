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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Write measurements to a file. Each call to write will open the file, append the data, and
 * close the file. This class is mostly used for testing.
 */
public class FileTDigestWriter extends TDigestWriter {

  private final File file;
  private final byte[] buf = new byte[BUFFER_SIZE];

  /** Create a new instance. */
  public FileTDigestWriter(File file) {
    super();
    this.file = file;
  }

  @Override void write(ByteBuffer data) throws IOException {
    try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file, true))) {
      int len = data.limit();
      data.get(buf, 0, len);
      out.writeInt(data.limit());
      out.write(buf, 0, len);
    }
  }

  @Override public void close() throws IOException {
  }
}
