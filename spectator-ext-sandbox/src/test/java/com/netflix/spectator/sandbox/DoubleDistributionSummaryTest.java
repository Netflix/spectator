package com.netflix.spectator.sandbox;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoubleDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  private Registry registry = new DefaultRegistry();

  private DoubleDistributionSummary newInstance() {
    clock.setWallTime(0L);
    return new DoubleDistributionSummary(clock, registry.createId("foo"), 60000);
  }

  @Test
  public void testInit() {
    DoubleDistributionSummary t = newInstance();
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalAmount(), 0.0, 1e-12);
  }

  @Test
  public void testRecord() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalAmount(), 42.0, 1e-12);
  }

  @Test
  public void testMeasureNotEnoughTime() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    clock.setWallTime(500L);
    int c = 0;
    for (Measurement m : t.measure()) {
      ++c;
    }
    Assert.assertEquals(0L, c);
  }

  @Test
  public void testMeasure() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    clock.setWallTime(65000L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 65000L);
      if (m.id().equals(t.id().withTag("statistic", "count"))) {
        Assert.assertEquals(m.value(), 1.0 / 65.0, 1e-12);
      } else if (m.id().equals(t.id().withTag("statistic", "totalAmount"))) {
        Assert.assertEquals(m.value(), 42.0 / 65.0, 1e-12);
      } else if (m.id().equals(t.id().withTag("statistic", "totalOfSquares"))) {
        Assert.assertEquals(m.value(), 42.0 * 42.0 / 65.0, 1e-12);
      } else if (m.id().equals(t.id().withTag("statistic", "max"))) {
        Assert.assertEquals(m.value(), 42.0, 1e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }

  private double stddev(double[] values) {
    double t = 0.0;
    double t2 = 0.0;
    double n = 0.0;
    for (double v : values) {
      t += v;
      t2 += v * v;
      n += 1.0;
    }
    return Math.sqrt((n * t2 - t * t) / (n * n));
  }

  @Test
  public void testMeasureZeroToOne() {
    double[] values = { 0.1, 0.2, 0.7, 0.8, 0.1, 0.4, 0.6, 0.9, 0.1, 1.0, 0.0, 0.5, 0.4 };
    DoubleDistributionSummary s = newInstance();
    for (double v : values) {
      s.record(v);
    }
    clock.setWallTime(65000L);

    double t = 0.0;
    double t2 = 0.0;
    double n = 0.0;
    double max = 0.0;
    for (Measurement m : s.measure()) {
      if (m.id().equals(s.id().withTag("statistic", "count"))) {
        n = m.value();
      } else if (m.id().equals(s.id().withTag("statistic", "totalAmount"))) {
        t = m.value();
      } else if (m.id().equals(s.id().withTag("statistic", "totalOfSquares"))) {
        t2 = m.value();
      } else if (m.id().equals(s.id().withTag("statistic", "max"))) {
        max = m.value();
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }

    Assert.assertEquals(1.0, max, 1e-12);
    Assert.assertEquals(stddev(values), Math.sqrt((n * t2 - t * t) / (n * n)), 1e-12);
  }

}
