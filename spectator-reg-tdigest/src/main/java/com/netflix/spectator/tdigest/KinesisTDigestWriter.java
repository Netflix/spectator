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

import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.netflix.spectator.api.Registry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Writer that sends the measurements to a Kinesis stream. The measurements will be grouped
 * to try and keep the record size close to 50k will have random partition keys.
 */
public class KinesisTDigestWriter extends TDigestWriter {

  private final Random random = new Random();

  private final AmazonKinesisClient client;
  private final String stream;

  /**
   * Create a new instance.
   *
   * @param registry
   *     Registry for creating metrics.
   * @param client
   *     Client for sending data to Kinesis.
   * @param config
   *     Digest configuration settings.
   */
  public KinesisTDigestWriter(Registry registry, AmazonKinesisClient client, TDigestConfig config) {
    super(registry, config);
    this.client = client;
    this.stream = config.getStream();
  }

  private String partitionKey() {
    StringBuilder buf = new StringBuilder(8);
    for (int i = 0; i < 8; ++i) {
      buf.append((char) '0' + random.nextInt('z' - '0'));
    }
    return buf.toString();
  }

  @Override void write(ByteBuffer data) throws IOException {
    client.putRecord(stream, data, partitionKey());
  }

  @Override public void close() throws IOException {
  }
}
