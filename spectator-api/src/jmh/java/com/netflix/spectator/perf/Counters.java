package com.netflix.spectator.perf;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Spectator;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Thread)
public class Counters {

  private final Counter cached = Spectator.registry().counter("cachedIncrement");

  @Benchmark
  public void cachedIncrement() {
    cached.increment();
  }

  @Benchmark
  public void lookupIncrement() {
    Spectator.registry().counter("lookupIncrement").increment();
  }

  @TearDown
  public void check() {
    final long cv = cached.count();
    final long lv = Spectator.registry().counter("lookupIncrement").count();
    assert cv > 0 || lv > 0 : "counters haven't been incremented";
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*")
        .forks(1)
        .build();
    new Runner(opt).run();
  }
}
