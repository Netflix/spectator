/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadPoolMonitorTest {

  private static final String THREAD_POOL_NAME = ThreadPoolMonitorTest.class.getSimpleName();

  private Registry registry;
  private LatchedThreadPoolExecutor latchedExecutor;

  private static class TestRunnable implements Runnable {
    private final CountDownLatch synchronizer;
    private final CountDownLatch terminator;

    TestRunnable(final CountDownLatch synchronizer, final CountDownLatch terminator) {
      this.synchronizer = synchronizer;
      this.terminator = terminator;
    }

    @Override
    public void run() {
      try {
        synchronizer.countDown();
        terminator.await(6, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static class LatchedThreadPoolExecutor extends ThreadPoolExecutor {

    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicLong tasks = new AtomicLong();
    private final AtomicLong completedTasks = new AtomicLong();
    private volatile CountDownLatch completed;

    LatchedThreadPoolExecutor(final CountDownLatch completed) {
      super(3, 10, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
      this.completed = completed;
    }

    CountDownLatch getCompletedLatch() {
      return this.completed;
    }

    void setCompletedLatch(final CountDownLatch completedLatch) {
      this.completed = completedLatch;
    }

    @Override
    public void execute(Runnable task) {
      queueSize.incrementAndGet();
      super.execute(task);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      queueSize.decrementAndGet();
      active.incrementAndGet();
      tasks.incrementAndGet();
      super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      active.decrementAndGet();
      completedTasks.incrementAndGet();
      completed.countDown();
      super.afterExecute(r, t);
    }

    @Override
    public int getActiveCount() {
      return active.get();
    }

    @Override
    public long getTaskCount() {
      return tasks.get();
    }

    @Override
    public long getCompletedTaskCount() {
      return completedTasks.get();
    }

    @Override
    public BlockingQueue<Runnable> getQueue() {
      return new LinkedBlockingQueue<Runnable>() {
        @Override
        public int size() {
          return queueSize.get();
        }
      };
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    registry = new DefaultRegistry();
    latchedExecutor = new LatchedThreadPoolExecutor(new CountDownLatch(1));
  }

  @AfterEach
  public void tearDown() throws Exception {
    registry = null;
    latchedExecutor.shutdown();
    latchedExecutor = null;
  }

  @Test
  public void monitorThrowsIfNullRegistry() throws Exception {
    Assertions.assertThrows(NullPointerException.class,
        () -> ThreadPoolMonitor.attach(null, latchedExecutor, THREAD_POOL_NAME));
  }

  @Test
  public void monitorThrowsIfNullThreadPool() throws Exception {
    Assertions.assertThrows(NullPointerException.class,
        () -> ThreadPoolMonitor.attach(registry, null, THREAD_POOL_NAME));
  }

  @Test
  public void monitorAcceptsNullThreadPoolName() {
    ThreadPoolMonitor.attach(registry, latchedExecutor, null);
  }

  private Meter getMeter(String meterName, String threadPoolName) {
    ThreadPoolMonitor.attach(registry, latchedExecutor, threadPoolName);
    PolledMeter.update(registry);
    final Id id = registry.createId(meterName).withTag(ThreadPoolMonitor.ID_TAG_NAME,
        (threadPoolName == null || threadPoolName.isEmpty()) ? ThreadPoolMonitor.DEFAULT_ID : threadPoolName);
    return registry.get(id);
  }

  private Meter getMeter(String meterName) {
    return getMeter(meterName, THREAD_POOL_NAME);
  }

  private Gauge getGauge(String meterName) {
    return (Gauge) getMeter(meterName, THREAD_POOL_NAME);
  }

  private Counter getCounter(String meterName) {
    return (Counter) getMeter(meterName, THREAD_POOL_NAME);
  }

  @Test
  public void metricsAreTaggedWithProvidedThreadPoolName() {
    checkIdTagValue(getMeter(ThreadPoolMonitor.MAX_THREADS), getClass().getSimpleName());
  }

  @Test
  public void metricsAreTaggedWithDefaultThreadPoolNameIfNull() {
    checkIdTagValue(getMeter(ThreadPoolMonitor.MAX_THREADS, null), ThreadPoolMonitor.DEFAULT_ID);
  }

  @Test
  public void metricsAreTaggedWithDefaultThreadPoolNameIfEmpty() {
    checkIdTagValue(getMeter(ThreadPoolMonitor.MAX_THREADS, ""), ThreadPoolMonitor.DEFAULT_ID);
  }

  private void checkIdTagValue(Meter meter, String expectedIdValue) {
    final Iterable<Measurement> measurements = meter.measure();
    final Iterator<Measurement> measurementIterator = measurements.iterator();
    Assertions.assertTrue(measurementIterator.hasNext());

    final Iterator<Tag> tags = measurementIterator.next().id().tags().iterator();
    Assertions.assertTrue(tags.hasNext());
    Tag tag = tags.next();
    Assertions.assertEquals(ThreadPoolMonitor.ID_TAG_NAME, tag.key());
    Assertions.assertEquals(expectedIdValue, tag.value());
  }

  @Test
  public void threadPoolMonitorHasTaskCountMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.TASK_COUNT));
  }

  @Test
  public void threadPoolMonitorHasCompletedTaskCountMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.COMPLETED_TASK_COUNT));
  }

  @Test
  public void threadPoolMonitorHasCurrentThreadsBusyMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.CURRENT_THREADS_BUSY));
  }

  @Test
  public void threadPoolMonitorHasMaxThreadsMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.MAX_THREADS));
  }

  @Test
  public void threadPoolMonitorHasPoolSizeMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.POOL_SIZE));
  }

  @Test
  public void threadPoolMonitorHasCorePoolSizeMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.CORE_POOL_SIZE));
  }

  @Test
  public void threadPoolMonitorHasQueueSizeMeter() {
    Assertions.assertNotNull(getMeter(ThreadPoolMonitor.QUEUE_SIZE));
  }

  @Test
  public void maxThreadsUpdatesWhenRegistryIsUpdated() {
    final Gauge gauge = getGauge(ThreadPoolMonitor.MAX_THREADS);
    Assertions.assertEquals(10.0, gauge.value(), 1e-12);

    latchedExecutor.setMaximumPoolSize(42);
    PolledMeter.update(registry);
    Assertions.assertEquals(42.0, gauge.value(), 1e-12);
  }

  @Test
  public void corePoolSizeUpdatesWhenRegistryIsUpdated() {
    final Gauge gauge = getGauge(ThreadPoolMonitor.CORE_POOL_SIZE);
    Assertions.assertEquals(3.0, gauge.value(), 1e-12);

    // Must be <= 10 because that is the max pool size used in the test. Starting with
    // jdk9 the it will validate and fail if trying to set the pool size larger than
    // the max
    latchedExecutor.setCorePoolSize(7);

    PolledMeter.update(registry);
    Assertions.assertEquals(7.0, gauge.value(), 1e-12);
  }

  @Test
  public void taskCountUpdates() throws InterruptedException {
    final Counter counter = getCounter(ThreadPoolMonitor.TASK_COUNT);
    Assertions.assertEquals(0, counter.count());

    final CountDownLatch synchronizer = new CountDownLatch(1);
    final CountDownLatch terminator = new CountDownLatch(1);
    final TestRunnable command = new TestRunnable(synchronizer, terminator);

    latchedExecutor.execute(command);

    synchronizer.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(1, counter.count(), 1e-12);
    terminator.countDown();
  }

  @Test
  public void currentThreadsBusyCountUpdates() throws InterruptedException {
    final Gauge gauge = getGauge(ThreadPoolMonitor.CURRENT_THREADS_BUSY);
    Assertions.assertEquals(0.0, gauge.value(), 1e-12);

    final CountDownLatch synchronizer = new CountDownLatch(1);
    final CountDownLatch terminator = new CountDownLatch(1);
    final TestRunnable command = new TestRunnable(synchronizer, terminator);

    latchedExecutor.execute(command);

    synchronizer.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(1.0, gauge.value(), 1e-12);

    terminator.countDown();
    latchedExecutor.getCompletedLatch().await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(0.0, gauge.value(), 1e-12);
  }

  @Test
  public void completedTaskCountUpdates() throws InterruptedException {
    final Counter counter = getCounter(ThreadPoolMonitor.COMPLETED_TASK_COUNT);
    Assertions.assertEquals(0, counter.count());

    final CountDownLatch synchronizer = new CountDownLatch(2);
    final CountDownLatch terminator1 = new CountDownLatch(1);
    final CountDownLatch terminator2 = new CountDownLatch(1);
    final TestRunnable command1 = new TestRunnable(synchronizer, terminator1);
    final TestRunnable command2 = new TestRunnable(synchronizer, terminator2);

    latchedExecutor.execute(command1);
    latchedExecutor.execute(command2);

    synchronizer.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(0, counter.count());

    terminator1.countDown();
    latchedExecutor.getCompletedLatch().await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(1, counter.count(), 1e-12);

    latchedExecutor.setCompletedLatch(new CountDownLatch(1));
    terminator2.countDown();
    latchedExecutor.getCompletedLatch().await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(2, counter.count());
  }

  @Test
  public void poolSizeUpdates() throws InterruptedException {
    final Gauge gauge = getGauge(ThreadPoolMonitor.POOL_SIZE);
    Assertions.assertEquals(0.0, gauge.value(), 1e-12);

    final CountDownLatch synchronizer = new CountDownLatch(2);
    final CountDownLatch terminator1 = new CountDownLatch(1);
    final CountDownLatch terminator2 = new CountDownLatch(1);
    final TestRunnable command1 = new TestRunnable(synchronizer, terminator1);
    final TestRunnable command2 = new TestRunnable(synchronizer, terminator2);

    latchedExecutor.execute(command1);
    latchedExecutor.execute(command2);

    synchronizer.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(2.0, gauge.value(), 1e-12);

    terminator1.countDown();
    terminator2.countDown();
  }

  @Test
  public void queueSizeUpdates() throws InterruptedException {
    latchedExecutor.setCorePoolSize(1);
    latchedExecutor.setMaximumPoolSize(1);
    final Gauge gauge = getGauge(ThreadPoolMonitor.QUEUE_SIZE);
    Assertions.assertEquals(0.0, gauge.value(), 1e-12);

    final CountDownLatch synchronizer1 = new CountDownLatch(1);
    final CountDownLatch synchronizer2 = new CountDownLatch(1);
    final CountDownLatch terminator123 = new CountDownLatch(1);
    final CountDownLatch terminator2 = new CountDownLatch(1);
    final TestRunnable command1 = new TestRunnable(synchronizer1, terminator123);
    final TestRunnable command2 = new TestRunnable(synchronizer1, terminator123);
    final TestRunnable command3 = new TestRunnable(synchronizer1, terminator123);
    final TestRunnable command4 = new TestRunnable(synchronizer2, terminator2);
    final TestRunnable command5 = new TestRunnable(synchronizer2, terminator2);
    final TestRunnable command6 = new TestRunnable(synchronizer2, terminator2);
    final TestRunnable command7 = new TestRunnable(synchronizer2, terminator2);

    latchedExecutor.execute(command1);
    latchedExecutor.execute(command2);
    latchedExecutor.execute(command3);
    latchedExecutor.execute(command4);
    latchedExecutor.execute(command5);
    latchedExecutor.execute(command6);
    latchedExecutor.execute(command7);

    synchronizer1.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(6.0, gauge.value(), 1e-12);

    latchedExecutor.setCompletedLatch(new CountDownLatch(3));
    terminator123.countDown();
    latchedExecutor.getCompletedLatch().await(6, TimeUnit.SECONDS);

    synchronizer2.await(6, TimeUnit.SECONDS);
    PolledMeter.update(registry);
    Assertions.assertEquals(3.0, gauge.value(), 1e-12);

    terminator2.countDown();
  }
}
