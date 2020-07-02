/*
 * Copyright 2014-2020 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/** Wrap GZIPOutputStream to set the best speed compression level. */
final class GzipLevelOutputStream extends GZIPOutputStream {
  /** Creates a new output stream with a best speed compression level. */
  GzipLevelOutputStream(OutputStream outputStream) throws IOException {
    super(outputStream);
    def.setLevel(Deflater.BEST_SPEED);
  }
}
