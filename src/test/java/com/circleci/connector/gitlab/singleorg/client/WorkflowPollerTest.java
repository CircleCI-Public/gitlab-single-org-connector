package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.ImmutableWorkflow;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class WorkflowPollerTest {
  private static final CircleCi CIRCLECI = mock(CircleCi.class);
  private static final GitLab GITLAB = mock(GitLab.class);
  private static final ScheduledExecutorService JOB_RUNNER = mock(ScheduledExecutorService.class);

  @Test
  void pollSleepsWhenTheCircleCiApiCallFails() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    int projectId = 123456;

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, projectId, "", "master");
    Workflow workflow = ImmutableWorkflow.of(UUID.randomUUID(), "my-workflow", State.PENDING);

    when(CIRCLECI.refreshWorkflow(workflow)).thenThrow(new RuntimeException());
    WorkflowPoller poller = new WorkflowPoller(pipeline, workflow, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void pollSleepsWhenTheWorkflowIsRunning() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, 123456, sha1, "master");
    Workflow workflow = ImmutableWorkflow.of(UUID.randomUUID(), "my-workflow", State.PENDING);
    Workflow newWorkflow = ImmutableWorkflow.copyOf(workflow).withState(State.RUNNING);

    when(CIRCLECI.refreshWorkflow(workflow)).thenReturn(newWorkflow);
    when(GITLAB.updateCommitStatus(pipeline, workflow)).thenReturn(State.RUNNING);
    WorkflowPoller poller = new WorkflowPoller(pipeline, workflow, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
    verify(GITLAB, times(1)).updateCommitStatus(pipeline, newWorkflow);
  }

  @Test
  void pollSleepsWhenTheWorkflowStaysPending() {
    UUID pipelineId = UUID.randomUUID();
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, 123456, sha1, "master");
    Workflow workflow = ImmutableWorkflow.of(UUID.randomUUID(), "my-workflow", State.PENDING);

    when(CIRCLECI.refreshWorkflow(workflow))
        .thenReturn(ImmutableWorkflow.copyOf(workflow).withState(State.PENDING));
    WorkflowPoller poller = new WorkflowPoller(pipeline, workflow, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
    verify(GITLAB, times(1)).updateCommitStatus(pipeline, workflow);
  }

  @Test
  void retryPolicyCoversAllGitLabStatuses() {
    for (var state : State.values()) {
      var policy = new WorkflowPoller.RetryPolicy();
      long delay = policy.delayFor(state);
      assertTrue(delay >= -1);
    }
  }

  @Test
  void retryPolicyDoesNotBackOffAsStatesTransition() {
    var policy = new WorkflowPoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(State.PENDING));
    assertEquals(1000, policy.delayFor(State.RUNNING));
    assertEquals(-1, policy.delayFor(State.SUCCESS));
  }

  @Test
  void retryPolicyBacksOffForRepeatedIdenticalStatuses() {
    var policy = new WorkflowPoller.RetryPolicy();
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
    var policy = new WorkflowPoller.RetryPolicy();
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
