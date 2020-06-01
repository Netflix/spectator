package com.netflix.spectator.perf;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Tag;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

/**
 *
 * Benchmark different approaches to override the statistic tag if present.
 *
 * <pre>
 * Benchmark                      Mode  Cnt        Score        Error  Units
 * DefaultStat.withIdWithTags    thrpt    9  2693269.613 ± 189599.585  ops/s
 * DefaultStat.withNameWithTags  thrpt    9  3485856.299 ± 116456.216  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class DefaultStat {
  private final Registry registry = new DefaultRegistry();

  private final Id baseId = registry.createId("http.req.complete")
      .withTag("nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag("nf.asg", "test_app-main-v042")
      .withTag("nf.stack", "main")
      .withTag("nf.ami", "ami-0987654321")
      .withTag("nf.region", "us-east-1")
      .withTag("nf.zone", "us-east-1e")
      .withTag("nf.node", "i-1234567890");

  @Threads(1)
  @Benchmark
  public void withIdWithTags(Blackhole bh) {
    bh.consume(baseId.withTag(Statistic.count).withTags(baseId.tags()).withTag(DsType.rate));
  }

  @Threads(1)
  @Benchmark
  public void withNameWithTags(Blackhole bh) {
    bh.consume(
        registry.createId(
            baseId.name()).withTag(Statistic.count).withTags(baseId.tags()).withTag(DsType.rate));
  }

  static enum DsType implements Tag {
    gauge,
    rate;

    @Override
    public String key() {
      return "atlas.dstype";
    }

    @Override
    public String value() {
      return name();
    }
  }
}
