package com.circleci.connector.gitlab.singleorg.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.Test;

class CircleCiApiHealthCheckTest {
  @Test
  void healthCheckPassesWhenWeHaveAPIAccess() {
    CircleCiApiHealthCheck healthCheck = new CircleCiApiHealthCheck();
    assertEquals(HealthCheck.Result.healthy(), healthCheck.check());
  }

  @Test
  void healthCheckPassesWhenWeHaveNoAPIAccess() {
    // TODO: Ensure that the health check fails when we have no API access
  }
}
