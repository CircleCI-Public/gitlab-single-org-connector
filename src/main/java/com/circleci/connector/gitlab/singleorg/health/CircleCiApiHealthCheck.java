package com.circleci.connector.gitlab.singleorg.health;

import com.codahale.metrics.health.HealthCheck;

/**
 * A health check to ensure that we have access to the CircleCI API. If this connector can't make
 * authenticated calls to the CircleCI API then it can't process hooks and turn them into new
 * pipelines.
 */
public class CircleCiApiHealthCheck extends HealthCheck {
  /**
   * Connect to the CircleCI API and return healthy if we can make an authenticated call to CircleCI
   * and unhealthy if we can't.
   */
  @Override
  protected Result check() {
    // TODO: Actually run a CircleCI API call to ensure we have access to the
    // CircleCI API.
    return Result.healthy();
  }
}
