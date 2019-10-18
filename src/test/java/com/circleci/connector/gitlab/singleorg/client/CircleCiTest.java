package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.PipelineWithWorkflows;
import com.circleci.client.v2.model.PipelineWithWorkflows.StateEnum;
import com.circleci.client.v2.model.PipelineWithWorkflowsVcs;
import com.circleci.client.v2.model.PipelineWithWorkflowsWorkflows;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.client.v2.model.Workflow;
import com.circleci.client.v2.model.Workflow.StatusEnum;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;

class CircleCiTest {

  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final DefaultApi CIRCLECI_HAPPY;
  private static final DefaultApi CIRCLECI_404;
  private static final DefaultApi CIRCLECI_404_JSON;
  private static final DefaultApi CIRCLECI_500;

  private static final UUID PIPELINE_ID = UUID.randomUUID();
  private static final String PIPELINE_REVISION = "foo";

  private static final ImmutablePipeline PIPELINE_WITHOUT_ID =
      ImmutablePipeline.of(null, 1, State.PENDING, PIPELINE_REVISION, "branch");
  private static final ImmutablePipeline PIPELINE_WITH_ID =
      ImmutablePipeline.copyOf(PIPELINE_WITHOUT_ID).withId(PIPELINE_ID);

  private static final PipelineLight PIPELINE_LIGHT;
  private static final PipelineWithWorkflows PIPELINE_WITH_WORKFLOWS;

  private static final Workflow RUNNING_WORKFLOW;

  static {
    CIRCLECI_HAPPY = mock(DefaultApi.class);
    CIRCLECI_404 = mock(DefaultApi.class);
    CIRCLECI_404_JSON = mock(DefaultApi.class);
    CIRCLECI_500 = mock(DefaultApi.class);

    PIPELINE_LIGHT = new PipelineLight();
    PIPELINE_LIGHT.setId(PIPELINE_ID);

    PIPELINE_WITH_WORKFLOWS = new PipelineWithWorkflows();
    PIPELINE_WITH_WORKFLOWS.setId(PIPELINE_ID);
    PIPELINE_WITH_WORKFLOWS.setState(StateEnum.RUNNING);
    PIPELINE_WITH_WORKFLOWS.setVcs(
        new PipelineWithWorkflowsVcs() {
          @Override
          public String getRevision() {
            return PIPELINE_REVISION;
          }
        });

    RUNNING_WORKFLOW = new Workflow();
    RUNNING_WORKFLOW.setId(UUID.randomUUID());
    RUNNING_WORKFLOW.setStatus(StatusEnum.RUNNING);

    PipelineWithWorkflowsWorkflows pipelineWithWorkflowsWorkflows =
        new PipelineWithWorkflowsWorkflows();
    pipelineWithWorkflowsWorkflows.setId(RUNNING_WORKFLOW.getId());

    PIPELINE_WITH_WORKFLOWS.setWorkflows(List.of(pipelineWithWorkflowsWorkflows));

    try {
      when(CIRCLECI_HAPPY.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenReturn(PIPELINE_LIGHT);
      when(CIRCLECI_HAPPY.getPipelineById(any(UUID.class))).thenReturn(PIPELINE_WITH_WORKFLOWS);
      when(CIRCLECI_HAPPY.getWorkflowById(any(UUID.class))).thenReturn(RUNNING_WORKFLOW);

      when(CIRCLECI_404.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "No such project"));
      when(CIRCLECI_404.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(404, "No such pipeline"));

      when(CIRCLECI_404_JSON.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such project\"}"));
      when(CIRCLECI_404_JSON.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such pipeline\"}"));

      when(CIRCLECI_500.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
      when(CIRCLECI_500.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
    } catch (ApiException e) {
      // Ignore when mocking
    }
  }

  @Test
  void pipelineStatePendingWhenNoWorkflows() {
    assertEquals(State.PENDING, CircleCi.pipelineStateFromWorkflowStates(Set.of()));
  }

  @Test
  void pipelineStateFailedWhenFailedWorkflows() {
    assertEquals(
        State.FAILED,
        CircleCi.pipelineStateFromWorkflowStates(
            Set.of(State.PENDING, State.RUNNING, State.SUCCESS, State.FAILED, State.CANCELED)));
  }

  @Test
  void pipelineStateCanceledWhenCanceledWorkflows() {
    assertEquals(
        State.CANCELED,
        CircleCi.pipelineStateFromWorkflowStates(
            Set.of(State.PENDING, State.RUNNING, State.SUCCESS, State.CANCELED)));
  }

  @Test
  void pipelineStateRunningWhenRunningWorkflows() {
    assertEquals(
        State.RUNNING,
        CircleCi.pipelineStateFromWorkflowStates(
            Set.of(State.PENDING, State.RUNNING, State.SUCCESS)));
  }

  @Test
  void pipelineStatePendingWhenPendingWorkflows() {
    assertEquals(
        State.PENDING,
        CircleCi.pipelineStateFromWorkflowStates(Set.of(State.PENDING, State.SUCCESS)));
  }

  @Test
  void pipelineStateSucessWhenAllWorkflowsSuccess() {
    assertEquals(State.SUCCESS, CircleCi.pipelineStateFromWorkflowStates(Set.of(State.SUCCESS)));
  }

  @Test
  void allWorkflowsStatesCovered() {
    List<StatusEnum> states = Arrays.asList(StatusEnum.values());
    assertEquals(new HashSet<>(states), CircleCi.CIRCLECI_WORKFLOW_TO_PIPELINE_STATE_MAP.keySet());
  }

  @Test
  void refreshPipelineIfCircleCiReturns4xxWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(ClientErrorException.class, () -> circleCi.refreshPipeline(PIPELINE_WITH_ID));
  }

  @Test
  void refreshPipelineIfCircleCiReturns4xxWithAJsonMessageWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(ClientErrorException.class, () -> circleCi.refreshPipeline(PIPELINE_WITH_ID));
  }

  @Test
  void refreshPipelineIfCircleCiReturns500WeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_500);
    assertThrows(RuntimeException.class, () -> circleCi.refreshPipeline(PIPELINE_WITH_ID));
  }

  @Test
  void refreshPipelineSuccess() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);
    Pipeline pipeline = circleCi.refreshPipeline(PIPELINE_WITH_ID);
    assertNotNull(pipeline);
    assertEquals(PIPELINE_LIGHT.getId(), pipeline.id());
  }

  @Test
  void triggerPipelineIfCircleCiReturns4xxWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturns4xxWithAJsonMessageWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturns500WeThrowRuntimeException() {
    CircleCi circleCi = new CircleCi(CIRCLECI_500);
    assertThrows(
        RuntimeException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", ""));
  }

  @Test
  void triggerPipelineIfPipelineAlreadyTriggeredError() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);

    assertThrows(
        RuntimeException.class, () -> circleCi.triggerPipeline(PIPELINE_WITH_ID, "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturnsPipelineSuccess() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);
    Pipeline pipeline = circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", "");
    assertNotNull(pipeline);
    assertEquals(PIPELINE_LIGHT.getId(), pipeline.id());
  }
}
