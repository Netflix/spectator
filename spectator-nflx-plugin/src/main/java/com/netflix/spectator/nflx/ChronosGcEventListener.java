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
package com.netflix.spectator.nflx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.spectator.api.Registry;
import iep.com.netflix.iep.http.RxHttp;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.gc.GcEvent;
import com.netflix.spectator.gc.GcEventListener;
import com.sun.management.GcInfo;
import io.netty.buffer.ByteBuf;
import iep.io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action0;
import rx.functions.Action1;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listener that sends GC events to a chronos backend.
 */
class ChronosGcEventListener implements GcEventListener {

  private static final DynamicBooleanProperty ENABLED =
    DynamicPropertyFactory.getInstance().getBooleanProperty("spectator.gc.chronosEnabled", true);

  private static final DynamicStringProperty CHRONOS_URI =
      DynamicPropertyFactory.getInstance().getStringProperty(
          "spectator.gc.chronosUri", "niws://chronos_gc/api/v2/event");

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Registry registry = Spectator.registry();
  private final Id requestCount = registry.createId("spectator.gc.chronosPost");

  private final ObjectMapper mapper = new ObjectMapper();

  // Keep track of the previous end time to provide additional context for chronos visualization
  // without needing to find the previous event. Stored as atomic long as there is no guarantee
  // that the listener won't receive events from multiple threads.
  private final AtomicLong previousEndTime = new AtomicLong(-1L);

  private final RxHttp rxHttp;

  /** Create a new instance. */
  ChronosGcEventListener(RxHttp rxHttp) {
    this.rxHttp = rxHttp;
  }

  private String getenv(String k) {
    String v = System.getenv(k);
    return (v == null || v.length() == 0) ? "unknown" : v;
  }

  /** Convert a GC event into a map. */
  Map<String, Object> toGcInfoMap(GcEvent event) {
    final GcInfo info = event.getInfo().getGcInfo();
    Map<String, Object> map = new HashMap<>();
    map.put("id", info.getId());
    map.put("startTime", event.getStartTime());
    map.put("endTime", event.getStartTime() + info.getDuration());
    map.put("previousEndTime", previousEndTime.get());
    map.put("duration", info.getDuration());
    map.put("memoryBeforeGc", info.getMemoryUsageBeforeGc());
    map.put("memoryAfterGc", info.getMemoryUsageAfterGc());
    return map;
  }

  /** Convert a GC event into a map. */
  Map<String, Object> toEventMap(GcEvent event) {
    Map<String, Object> map = new HashMap<>();
    map.put("action", event.getInfo().getGcAction());
    map.put("cause", event.getInfo().getGcCause());
    map.put("name", event.getName());
    map.put("gcInfo", toGcInfoMap(event));
    map.put("app", getenv("NETFLIX_APP"));
    map.put("cluster", getenv("NETFLIX_CLUSTER"));
    map.put("asg", getenv("NETFLIX_AUTO_SCALE_GROUP"));
    map.put("region", getenv("EC2_REGION"));
    map.put("zone", getenv("EC2_AVAILABILITY_ZONE"));
    map.put("ami", getenv("EC2_AMI_ID"));
    map.put("node", getenv("EC2_INSTANCE_ID"));
    return map;
  }

  private void sendToChronos(final byte[] json, final boolean blocking) {
    final URI uri = URI.create(CHRONOS_URI.get());

    final CountDownLatch latch = new CountDownLatch(1);
    final long start = System.nanoTime();
    rxHttp.postJson(uri, json).subscribe(
        new Action1<HttpClientResponse<ByteBuf>>() {
          @Override
          public void call(HttpClientResponse<ByteBuf> response) {
            final int code = response.getStatus().code();
            if (code != 200) {
              logger.warn("failed to send GC event to chronos (status={})", code);
            }
            final long latency = System.nanoTime() - start;
            final Id timerId = requestCount.withTag("status", "" + code);
            registry.timer(timerId).record(latency, TimeUnit.NANOSECONDS);
          }
        },
        new Action1<Throwable>() {
          @Override
          public void call(Throwable t) {
            logger.warn("failed to send GC event to chronos", t);
            final String status = t.getClass().getSimpleName();
            final long latency = System.nanoTime() - start;
            final Id timerId = requestCount.withTag("status", status);
            registry.timer(timerId).record(latency, TimeUnit.NANOSECONDS);
            latch.countDown();
          }
        },
        new Action0() {
          @Override
          public void call() {
            latch.countDown();
          }
        }
    );

    // Used for unit tests so we can reliably detect completion
    if (blocking) {
      try {
        latch.await();
      } catch (InterruptedException e) {
        // Ignore
      }
    }
  }

  /**
   * Send GC event to chronos.
   */
  void onComplete(final GcEvent event, boolean blocking) {
    if (!ENABLED.get()) {
      return;
    }

    try {
      final byte[] json = mapper.writeValueAsBytes(toEventMap(event));
      sendToChronos(json, blocking);
    } catch (IOException e) {
      logger.warn("failed to send GC event to chronos", e);
    }

    previousEndTime.set(event.getStartTime() + event.getInfo().getGcInfo().getDuration());
  }

  @Override public void onComplete(final GcEvent event) {
    onComplete(event, false);
  }

  /** Shutdown the client used to send data to chronos. */
  public void shutdown() {
  }
}
