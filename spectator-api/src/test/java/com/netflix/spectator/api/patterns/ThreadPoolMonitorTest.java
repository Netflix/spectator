package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class ThreadPoolMonitorTest {

  private static final String THREAD_POOL_NAME = ThreadPoolMonitorTest.class.getSimpleName();

  private Registry registry;
  private ThreadPoolExecutor threadPoolExecutor;

  @Before
  public void setUp() throws Exception {
    registry = new DefaultRegistry();
    threadPoolExecutor = new ThreadPoolExecutor(3, 10, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
  }

  @After
  public void tearDown() throws Exception {
    registry = null;
    threadPoolExecutor.shutdown();
    threadPoolExecutor = null;
  }

  @Test(expected = NullPointerException.class)
  public void monitorThrowsIfNullRegistry() throws Exception {
    ThreadPoolMonitor.monitor(null, threadPoolExecutor, THREAD_POOL_NAME);
  }

  @Test(expected = NullPointerException.class)
  public void monitorThrowsIfNullThreadPool() throws Exception {
    ThreadPoolMonitor.monitor(registry, null, THREAD_POOL_NAME);
  }

  @Test
  public void monitorAcceptsNullThreadPoolName() {
    ThreadPoolMonitor.monitor(registry, threadPoolExecutor, null);
  }

  private Meter getMeter(String meterName) {
    ThreadPoolMonitor.monitor(registry, threadPoolExecutor, THREAD_POOL_NAME);
    PolledMeter.update(registry);
    Id id = registry.createId(meterName).withTag(ThreadPoolMonitor.ID_TAG_NAME, THREAD_POOL_NAME);
    return registry.get(id);
  }

  @Test
  public void metricsAreTaggedWithProvidedThreadPoolName() {
    Meter meter = getMeter(ThreadPoolMonitor.MAX_THREADS);

    Iterable<Measurement> measurements = meter.measure();
    Iterator<Measurement> measurementIterator = measurements.iterator();
    assertTrue(measurementIterator.hasNext());

    Iterator<Tag> tags = measurementIterator.next().id().tags().iterator();
    assertTrue(tags.hasNext());
    assertEquals(getClass().getSimpleName(), tags.next().value());
  }

  @Test
  public void threadPoolMonitorHasTaskCountMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.TASK_COUNT));
  }

  @Test
  public void threadPoolMonitorHasCompletedTaskCountMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.COMPLETED_TASK_COUNT));
  }

  @Test
  public void threadPoolMonitorHasCurrentThreadsBusyMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.CURRENT_THREADS_BUSY));
  }

  @Test
  public void threadPoolMonitorHasMaxThreadsMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.MAX_THREADS));
  }

  @Test
  public void threadPoolMonitorHasPoolSizeMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.POOL_SIZE));
  }

  @Test
  public void threadPoolMonitorHasCorePoolSizeMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.CORE_POOL_SIZE));
  }

  @Test
  public void threadPoolMonitorHasQueueSizeMeter() {
    assertNotNull(getMeter(ThreadPoolMonitor.QUEUE_SIZE));
  }

  @Test
  public void maxThreadsUpdatesWhenRegistryIsUpdated() {
    Meter meter = getMeter(ThreadPoolMonitor.MAX_THREADS);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(10.0, measurements.next().value(), 0.0);

    threadPoolExecutor.setMaximumPoolSize(42);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(42.0, measurements.next().value(), 0.0);
  }

  @Test
  public void corePoolSizeUpdatesWhenRegistryIsUpdated() {
    Meter meter = getMeter(ThreadPoolMonitor.CORE_POOL_SIZE);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(3.0, measurements.next().value(), 0.0);

    threadPoolExecutor.setCorePoolSize(42);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(42.0, measurements.next().value(), 0.0);
  }

  @Test
  public void taskCountUpdates() throws InterruptedException {
    Meter meter = getMeter(ThreadPoolMonitor.TASK_COUNT);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    Cancellable command = new Cancellable();
    threadPoolExecutor.execute(command);

    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(1.0, measurements.next().value(), 0.0);

    command.canceled = true;
  }

  @Test
  public void currentThreadsBusyCountUpdates() throws InterruptedException {
    Meter meter = getMeter(ThreadPoolMonitor.CURRENT_THREADS_BUSY);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    Cancellable command = new Cancellable();
    threadPoolExecutor.execute(command);

    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(1.0, measurements.next().value(), 0.0);

    command.canceled = true;
    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);
  }

  @Test
  public void completedTaskCountUpdates() throws InterruptedException {
    Meter meter = getMeter(ThreadPoolMonitor.COMPLETED_TASK_COUNT);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    Cancellable command1 = new Cancellable();
    Cancellable command2 = new Cancellable();
    threadPoolExecutor.execute(command1);
    threadPoolExecutor.execute(command2);

    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    command1.canceled = true;
    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(1.0, measurements.next().value(), 0.0);

    command2.canceled = true;
    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(2.0, measurements.next().value(), 0.0);
  }

  @Test
  public void poolSizeUpdates() throws InterruptedException {
    Meter meter = getMeter(ThreadPoolMonitor.POOL_SIZE);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    Cancellable command1 = new Cancellable();
    Cancellable command2 = new Cancellable();
    threadPoolExecutor.execute(command1);
    threadPoolExecutor.execute(command2);

    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(2.0, measurements.next().value(), 0.0);

    command1.canceled = true;
    command2.canceled = true;
    threadPoolExecutor.shutdown();
    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

  }

  @Test
  public void queueSizeUpdates() throws InterruptedException {
    threadPoolExecutor.setCorePoolSize(1);
    threadPoolExecutor.setMaximumPoolSize(1);
    Meter meter = getMeter(ThreadPoolMonitor.QUEUE_SIZE);
    Iterator<Measurement> measurements = meter.measure().iterator();
    assertEquals(0.0, measurements.next().value(), 0.0);

    Cancellable command1 = new Cancellable();
    Cancellable command2 = new Cancellable();
    Cancellable command3 = new Cancellable();
    Cancellable command4 = new Cancellable();
    Cancellable command5 = new Cancellable();
    Cancellable command6 = new Cancellable();
    Cancellable command7 = new Cancellable();
    threadPoolExecutor.execute(command1);
    threadPoolExecutor.execute(command2);
    threadPoolExecutor.execute(command3);
    threadPoolExecutor.execute(command4);
    threadPoolExecutor.execute(command5);
    threadPoolExecutor.execute(command6);
    threadPoolExecutor.execute(command7);

    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(6.0, measurements.next().value(), 0.0);

    command1.canceled = true;
    command2.canceled = true;
    command3.canceled = true;
    Thread.sleep(60);
    PolledMeter.update(registry);
    measurements = meter.measure().iterator();
    assertEquals(3.0, measurements.next().value(), 0.0);

  }

  private static class Cancellable implements Runnable {
    volatile boolean canceled = false;
    @Override
    public void run() {
      while (!canceled) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
