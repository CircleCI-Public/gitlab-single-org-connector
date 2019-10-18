package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline.State;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class PipelineStatusPollerTest {
  private static final CircleCi CIRCLECI = mock(CircleCi.class);
  private static final GitLab GITLAB = mock(GitLab.class);
  private static final ScheduledExecutorService JOB_RUNNER = mock(ScheduledExecutorService.class);

  @Test
  void pollSleepsWhenTheCircleCiApiCallFails() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    int projectId = 123456;

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, projectId, State.PENDING, "", "master");

    when(CIRCLECI.refreshPipeline(pipeline)).thenThrow(new RuntimeException());
    PipelineStatusPoller poller = new PipelineStatusPoller(pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void pollSleepsWhenThePipelineIsRunning() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, 123456, State.PENDING, sha1, "master");

    when(CIRCLECI.refreshPipeline(pipeline)).thenReturn(pipeline);
    when(GITLAB.updateCommitStatus(pipeline)).thenReturn(State.RUNNING);
    PipelineStatusPoller poller = new PipelineStatusPoller(pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void retryPolicyCoversAllGitLabStatuses() {
    for (var state : State.values()) {
      var policy = new PipelineStatusPoller.RetryPolicy();
      long delay = policy.delayFor(state);
      assertTrue(delay >= -1);
    }
  }

  @Test
  void retryPolicyDoesNotBackOffAsStatesTransition() {
    var policy = new PipelineStatusPoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(State.PENDING));
    assertEquals(1000, policy.delayFor(State.RUNNING));
    assertEquals(-1, policy.delayFor(State.SUCCESS));
  }

  @Test
  void retryPolicyBacksOffForRepeatedIdenticalStatuses() {
    var policy = new PipelineStatusPoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(State.PENDING));
    assertEquals(2000, policy.delayFor(State.PENDING));
    assertEquals(4000, policy.delayFor(State.PENDING));
    assertEquals(8000, policy.delayFor(State.PENDING));
    assertEquals(10000, policy.delayFor(State.PENDING));
    assertEquals(10000, policy.delayFor(State.PENDING));
    assertEquals(1000, policy.delayFor(State.RUNNING));
    assertEquals(2000, policy.delayFor(State.RUNNING));
    assertEquals(4000, policy.delayFor(State.RUNNING));
    assertEquals(8000, policy.delayFor(State.RUNNING));
    assertEquals(10000, policy.delayFor(State.RUNNING));
    assertEquals(10000, policy.delayFor(State.RUNNING));
    assertEquals(-1, policy.delayFor(State.SUCCESS));
  }

  @Test
  void retryPolicyGivesUpAfterEnoughConsecutiveErrors() {
    var policy = new PipelineStatusPoller.RetryPolicy();
    assertEquals(200, policy.delayFor(null));
    assertEquals(400, policy.delayFor(null));
    assertEquals(800, policy.delayFor(null));
    assertEquals(1600, policy.delayFor(null));
    assertEquals(3200, policy.delayFor(null));
    assertEquals(6400, policy.delayFor(null));
    assertEquals(10000, policy.delayFor(null));
    assertEquals(10000, policy.delayFor(null));
    assertEquals(10000, policy.delayFor(null));
    assertEquals(-1, policy.delayFor(null));
  }
}
