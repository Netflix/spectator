package com.netflix.spectator.spark;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SpectatorConfigTest {

  private final SpectatorConfig config = new SpectatorConfig(
      ConfigFactory.load().getConfig("spectator.spark.sidecar")
  );

  @Test
  public void enabled() {
    Assertions.assertFalse(config.outputLocation().isEmpty());
  }
}
