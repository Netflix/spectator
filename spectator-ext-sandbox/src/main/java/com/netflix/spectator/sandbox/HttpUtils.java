/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Helper functions for the http client.
 */
final class HttpUtils {
  private HttpUtils() {
  }

  /** Empty byte array constant. */
  static final byte[] EMPTY = new byte[0];

  private static final String DEFAULT = "default";

  private static final Pattern PREFIX = Pattern.compile("^([^.-]+).*$");

  /**
   * Extract a client name based on the host. This will currently select up
   * to the first dash or dot in the name. The goal is to have a reasonable
   * name, but avoid a large explosion in number of names in dynamic environments
   * such as EC2. Examples:
   *
   * <pre>
   * name      host
   * ----------------------------------------------------
   * foo       foo.test.netflix.com
   * ec2       ec2-127-0-0-1.compute-1.amazonaws.com
   * </pre>
   */
  static String clientNameForHost(String host) {
    Matcher m = PREFIX.matcher(host);
    return m.matches() ? m.group(1) : DEFAULT;
  }

  /**
   * Extract a client name based on the uri host. See {@link #clientNameForHost(String)}
   * for more details.
   */
  static String clientNameForURI(URI uri) {
    String host = uri.getHost();
    return (host == null) ? DEFAULT : clientNameForHost(host);
  }

  /** Compress a byte array using GZIP. */
  static byte[] gzip(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
    try (OutputStream out = new GZIPOutputStream(baos)) {
      out.write(data);
    }
    return baos.toByteArray();
  }

  /** Decompress a GZIP compressed byte array. */
  @SuppressWarnings("PMD.AssignmentInOperand")
  static byte[] gunzip(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length * 10);
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
      byte[] buffer = new byte[4096];
      int length;
      while ((length = in.read(buffer)) > 0) {
        baos.write(buffer, 0, length);
      }
    }
    return baos.toByteArray();
  }
}
