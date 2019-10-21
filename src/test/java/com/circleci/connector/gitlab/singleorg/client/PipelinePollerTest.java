package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.ImmutableWorkflow;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class PipelinePollerTest {
  private static final CircleCi CIRCLECI = mock(CircleCi.class);
  private static final GitLab GITLAB = mock(GitLab.class);
  private static final ScheduledExecutorService JOB_RUNNER = mock(ScheduledExecutorService.class);
  private static final Workflow WORKFLOW =
      ImmutableWorkflow.of(UUID.randomUUID(), "workflow", State.RUNNING);

  @Test
  void pollSleepsWhenTheCircleCiApiCallFails() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    int projectId = 123456;

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, projectId, "", "master");

    when(CIRCLECI.refreshPipeline(pipeline)).thenThrow(new RuntimeException());
    PipelinePoller poller = new PipelinePoller(pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void pollSleepsWhenThePipelineHasNoWorkflows() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";

    Pipeline pipeline = ImmutablePipeline.of(pipelineId, 123456, sha1, "master");

    when(CIRCLECI.refreshPipeline(pipeline)).thenReturn(pipeline);
    PipelinePoller poller = new PipelinePoller(pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
  }

  @Test
  void pollSleepsWhenThePipelineHasNewWorkflows() throws ApiException {
    UUID pipelineId = UUID.randomUUID();
    String sha1 = "8e38b1205365ed98c8f27ed2e1f35166a3f5858f";

    Pipeline pipeline =
        ImmutablePipeline.of(pipelineId, 123456, sha1, "master").withWorkflows(Set.of(WORKFLOW));

    when(CIRCLECI.refreshPipeline(pipeline)).thenReturn(pipeline);
    PipelinePoller poller = new PipelinePoller(pipeline, CIRCLECI, GITLAB, JOB_RUNNER);
    assertTrue(poller.poll() > 0);
    assertTrue(poller.getWorkflowPollers().containsKey(WORKFLOW.id()));
  }

  @Test
  void retryPolicyStopsAfterConsecutiveUnchangedWorkflows() {
    Set<Workflow> workflows = Set.of(WORKFLOW);
    var policy = new PipelinePoller.RetryPolicy();
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(1000, policy.delayFor(workflows));
    assertEquals(-1, policy.delayFor(workflows));
  }

  @Test
  void retryPolicyGivesUpAfterEnoughConsecutiveErrors() {
    var policy = new PipelinePoller.RetryPolicy();
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

  @Test
  void retryPolicyGivesUpAfterEnoughEmptyWorkflows() {
    Set<Workflow> workflows = Set.of();
    var policy = new PipelinePoller.RetryPolicy();
    assertEquals(200, policy.delayFor(workflows));
    assertEquals(400, policy.delayFor(workflows));
    assertEquals(800, policy.delayFor(workflows));
    assertEquals(1600, policy.delayFor(workflows));
    assertEquals(3200, policy.delayFor(workflows));
    assertEquals(6400, policy.delayFor(workflows));
    assertEquals(10000, policy.delayFor(workflows));
    assertEquals(10000, policy.delayFor(workflows));
    assertEquals(10000, policy.delayFor(workflows));
    assertEquals(-1, policy.delayFor(workflows));
  }
}
