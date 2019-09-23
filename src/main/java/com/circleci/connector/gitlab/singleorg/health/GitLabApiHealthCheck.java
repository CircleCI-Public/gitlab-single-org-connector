package com.circleci.connector.gitlab.singleorg.health;

import com.codahale.metrics.health.HealthCheck;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.HealthCheckInfo;
import org.gitlab4j.api.models.HealthCheckStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitLabApiHealthCheck extends HealthCheck {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitLabApiHealthCheck.class);

  private final GitLabApi gitLabApi;

  public GitLabApiHealthCheck(GitLabApi gitLabApi) {
    this.gitLabApi = gitLabApi;
  }

  /**
   * Try to connect to GitLab API to determine health
   *
   * @return Result.healthy if GitLab liveness check passes, Result.unhealthy if check fails in any
   *     manner
   */
  @Override
  protected Result check() {
    boolean alive = false;
    try {
      HealthCheckInfo liveness = gitLabApi.getHealthCheckApi().getLiveness();
      alive =
          liveness.getCacheCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getDbCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getFsShardsCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getGitalyCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getQueuesCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getRedisCheck().getStatus() == HealthCheckStatus.OK
              && liveness.getSharedStateCheck().getStatus() == HealthCheckStatus.OK;
    } catch (GitLabApiException e) {
      LOGGER.warn("GitLab API liveness threw exception", e);
    } catch (RuntimeException e) {
      LOGGER.error("Error checking GitLab API liveness", e);
    }

    if (alive) {
      return Result.healthy();
    }
    return Result.unhealthy("GitLab API liveness probe failed");
  }
}
