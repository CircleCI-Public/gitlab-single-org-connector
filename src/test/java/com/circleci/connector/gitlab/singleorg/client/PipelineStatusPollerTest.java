package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.PipelineWithWorkflows;
import com.circleci.client.v2.model.PipelineWithWorkflowsVcs;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.gitlab4j.api.Constants.CommitBuildState;
import org.junit.jupiter.api.Test;

class PipelineStatusPollerTest {
  private static final CircleCi CIRCLECI = mock(CircleCi.class);
  private static final GitLab GITLAB = mock(GitLab.class);
  private static final ScheduledExecutorService JOB_RUNNER = mock(ScheduledExecutorService.class);

  @Test
  void pollSleepsWhenTheCircleCiApiCallFails() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    int projectId = 123456;
    PipelineLight pipeline = (new PipelineLight()).id(pipelineId);

    when(CIRCLECI.getPipelineById(pipelineId)).thenThrow(new RuntimeException());
    PipelineStatusPoller poller =
        new PipelineStatusPoller(projectId, pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void pollSleepsWhenThePipelineIsRunning() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    int projectId = 123456;
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";
    PipelineLight pipeline = (new PipelineLight()).id(pipelineId);
    PipelineWithWorkflows p =
        (new PipelineWithWorkflows())
            .id(pipelineId)
            .state(PipelineWithWorkflows.StateEnum.RUNNING)
            .vcs(new PipelineWithWorkflowsVcs().revision(sha1));

    when(CIRCLECI.getPipelineById(pipelineId)).thenReturn(p);
    when(GITLAB.updateCommitStatus(projectId, p)).thenReturn(CommitBuildState.RUNNING);
    PipelineStatusPoller poller =
        new PipelineStatusPoller(projectId, pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void retryPolicyCoversAllGitLabStatuses() {
    for (var state : CommitBuildState.values()) {
      var policy = new PipelineStatusPoller.RetryPolicy();
      long delay = policy.delayFor(state);
      assertTrue(delay >= -1);
    }
  }

  @Test
  void retryPolicyDoesNotBackOffAsStatesTransition() {
    var policy = new PipelineStatusPoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(1000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(-1, policy.delayFor(CommitBuildState.SUCCESS));
  }

  @Test
  void retryPolicyBacksOffForRepeatedIdenticalStatuses() {
    var policy = new PipelineStatusPoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(2000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(4000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(8000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(10000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(10000, policy.delayFor(CommitBuildState.PENDING));
    assertEquals(1000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(2000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(4000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(8000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(10000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(10000, policy.delayFor(CommitBuildState.RUNNING));
    assertEquals(-1, policy.delayFor(CommitBuildState.SUCCESS));
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
