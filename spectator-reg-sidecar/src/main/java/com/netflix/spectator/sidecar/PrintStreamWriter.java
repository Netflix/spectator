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
import java.io.PrintStream;

/** Writer that outputs data to a PrintStream instance. */
final class PrintStreamWriter extends SidecarWriter {

  private final PrintStream stream;

  /** Create a new instance. */
  PrintStreamWriter(PrintStream stream) {
    super();
    this.stream = stream;
  }

  @Override public void writeImpl(String line) throws IOException {
    stream.println(line);
  }

  @Override public void close() throws IOException {
    stream.close();
  }
}
