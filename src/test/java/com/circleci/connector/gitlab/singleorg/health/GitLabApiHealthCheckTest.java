package com.circleci.connector.gitlab.singleorg.health;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codahale.metrics.health.HealthCheck;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.HealthCheckApi;
import org.gitlab4j.api.models.HealthCheckInfo;
import org.gitlab4j.api.models.HealthCheckItem;
import org.gitlab4j.api.models.HealthCheckStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GitLabApiHealthCheckTest {
  private GitLabApi mockGitLabApi = Mockito.mock(GitLabApi.class);
  private HealthCheckApi mockHealthCheckApi = Mockito.mock(HealthCheckApi.class);
  private GitLabApiHealthCheck gitLabApiHealthCheck = new GitLabApiHealthCheck(mockGitLabApi);

  @BeforeEach
  void setUp() {
    Mockito.when(mockGitLabApi.getHealthCheckApi()).thenReturn(mockHealthCheckApi);
  }

  @Test
  void checkReturnsHealthyWhenGetLivenessHealthChecksOk() throws GitLabApiException {
    HealthCheckItem dbCheck = new HealthCheckItem();
    dbCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem cacheCheck = new HealthCheckItem();
    cacheCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem fsShardsCheck = new HealthCheckItem();
    fsShardsCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem gitalyCheck = new HealthCheckItem();
    gitalyCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem queuesCheck = new HealthCheckItem();
    queuesCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem redisCheck = new HealthCheckItem();
    redisCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem sharedStateCheck = new HealthCheckItem();
    sharedStateCheck.setStatus(HealthCheckStatus.OK);

    HealthCheckInfo healthCheckInfo = new HealthCheckInfo();
    healthCheckInfo.setDbCheck(dbCheck);
    healthCheckInfo.setCacheCheck(sharedStateCheck);
    healthCheckInfo.setFsShardsCheck(fsShardsCheck);
    healthCheckInfo.setGitalyCheck(gitalyCheck);
    healthCheckInfo.setQueuesCheck(queuesCheck);
    healthCheckInfo.setRedisCheck(redisCheck);
    healthCheckInfo.setSharedStateCheck(sharedStateCheck);

    Mockito.when(mockHealthCheckApi.getLiveness()).thenReturn(healthCheckInfo);
    HealthCheck.Result check = gitLabApiHealthCheck.check();
    assertTrue(check.isHealthy());
  }

  @Test
  void checkReturnsUnhealthyWhenGetLivenessSomeHealthCheckFailed() throws GitLabApiException {
    HealthCheckItem dbCheck = new HealthCheckItem();
    dbCheck.setStatus(HealthCheckStatus.OK);
    HealthCheckItem cacheCheck = new HealthCheckItem();
    cacheCheck.setStatus(HealthCheckStatus.FAILED);

    HealthCheckInfo healthCheckInfo = new HealthCheckInfo();
    healthCheckInfo.setDbCheck(dbCheck);
    healthCheckInfo.setCacheCheck(cacheCheck);

    Mockito.when(mockHealthCheckApi.getLiveness()).thenReturn(healthCheckInfo);
    HealthCheck.Result check = gitLabApiHealthCheck.check();
    assertFalse(check.isHealthy());
  }

  @Test
  void checkReturnsUnhealthyWhenGetLivenessThrows() throws GitLabApiException {
    Mockito.when(mockHealthCheckApi.getLiveness())
        .thenThrow(new GitLabApiException("expected exception"));
    HealthCheck.Result check = gitLabApiHealthCheck.check();
    assertFalse(check.isHealthy());
  }
}
