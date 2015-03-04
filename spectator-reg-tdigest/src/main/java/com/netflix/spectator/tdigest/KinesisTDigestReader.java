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
import com.amazonaws.services.kinesis.model.GetRecordsRequest;
import com.amazonaws.services.kinesis.model.GetRecordsResult;
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest;
import com.amazonaws.services.kinesis.model.GetShardIteratorResult;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Spectator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reader to consume data from a single shard of a Kinesis stream. The records should have been
 * written by {@link com.netflix.spectator.tdigest.KinesisTDigestWriter}.
 */
public class KinesisTDigestReader implements TDigestReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(KinesisTDigestReader.class);

  private final AmazonKinesisClient client;
  private final GetShardIteratorRequest iterRequest;

  private final Counter recordsProcessed;
  private final Counter recordsSkipped;

  private GetRecordsRequest recRequest;

  /**
   * Create a new instance that reads from the beginning of the shard. The iterator type is
   * set to {@code TRIM_HORIZON}.
   *
   * @param client
   *     Client for interacting with the Kinesis service.
   * @param stream
   *     Name of the stream to read from.
   * @param shard
   *     Id of the shard to consume.
   */
  public KinesisTDigestReader(AmazonKinesisClient client, String stream, String shard) {
    this(client, new GetShardIteratorRequest()
        .withStreamName(stream)
        .withShardId(shard)
        .withShardIteratorType(ShardIteratorType.TRIM_HORIZON));
  }

  /**
   * Create a new instance.
   *
   * @param client
   *     Client for interacting with the Kinesis service.
   * @param iterRequest
   *     Request for getting the initial shard iterator.
   */
  public KinesisTDigestReader(AmazonKinesisClient client, GetShardIteratorRequest iterRequest) {
    this.client = client;
    this.iterRequest = iterRequest;
    this.recordsProcessed = counter("recordsProcessed", iterRequest);
    this.recordsSkipped = counter("recordsSkipped", iterRequest);
    this.recRequest = null;
  }

  private Counter counter(String name, GetShardIteratorRequest req) {
    return Spectator.registry().counter("spectator.tdigest." + name,
        "stream", req.getStreamName(),
        "shard", req.getShardId());
  }

  private void init() {
    if (recRequest == null) {
      GetShardIteratorResult result = client.getShardIterator(iterRequest);
      recRequest = new GetRecordsRequest()
          .withLimit(10)
          .withShardIterator(result.getShardIterator());
    }
  }

  @Override public List<TDigestMeasurement> read() throws IOException {
    init();
    if (recRequest.getShardIterator() == null) {
      return Collections.emptyList();
    } else {
      List<TDigestMeasurement> ms = new ArrayList<>();
      GetRecordsResult result = client.getRecords(recRequest);
      recRequest.setShardIterator(result.getNextShardIterator());
      for (Record r : result.getRecords()) {
        recordsProcessed.increment();
        ByteBuffer data = r.getData();
        try {
          ms.addAll(Json.decode(data.array()));
        } catch (Exception e) {
          recordsSkipped.increment();
          LOGGER.warn("failed to decode record, skipping (" + iterRequest + ")", e);
        }
      }
      return ms;
    }
  }

  @Override public void close() throws IOException {
  }
}
