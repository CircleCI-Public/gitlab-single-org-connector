package com.circleci.connector.gitlab.singleorg.health;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.codahale.metrics.health.HealthCheck;

/**
 * A health check to ensure that we have access to the CircleCI API. If this connector can't make
 * authenticated calls to the CircleCI API then it can't process hooks and turn them into new
 * pipelines.
 */
public class CircleCiApiHealthCheck extends HealthCheck {
  private final DefaultApi api;

  public CircleCiApiHealthCheck(DefaultApi circleCiApi) {
    api = circleCiApi;
  }

  /**
   * Connect to the CircleCI API and return healthy if we can make an authenticated call to CircleCI
   * and unhealthy if we can't.
   */
  @Override
  protected Result check() {
    try {
      if (api.getCurrentUser() != null) {
        return Result.healthy();
      } else {
        return Result.unhealthy("Returned null user from a successful API request");
      }
    } catch (ApiException e) {
      return Result.unhealthy(e.getMessage());
    }
  }
}
