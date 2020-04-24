package com.netflix.spectator.spark;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class SpectatorConfigTest {

  private final SpectatorConfig config = new SpectatorConfig(
      ConfigFactory.load().getConfig("spectator.spark.stateless")
  );

  @Test
  public void enabled() {
    Assertions.assertTrue(config.enabled());
  }

  @Test
  public void frequency() {
    Assertions.assertEquals(Duration.ofSeconds(5), config.frequency());
  }

  @Test
  public void meterTTL() {
    Assertions.assertEquals(Duration.ofMinutes(15), config.meterTTL());
  }
}
