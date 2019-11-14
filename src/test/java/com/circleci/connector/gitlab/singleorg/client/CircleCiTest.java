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
import com.circleci.client.v2.model.PipelineWithWorkflowsVcs;
import com.circleci.client.v2.model.PipelineWithWorkflowsWorkflows;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.client.v2.model.Workflow.StatusEnum;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.ImmutableWorkflow;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
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
  private static final UUID WORKFLOW_ID = UUID.randomUUID();

  private static final ImmutablePipeline PIPELINE_WITHOUT_ID =
      ImmutablePipeline.of(null, 1, PIPELINE_REVISION, "branch");
  private static final ImmutablePipeline PIPELINE_WITH_ID =
      ImmutablePipeline.copyOf(PIPELINE_WITHOUT_ID).withId(PIPELINE_ID);

  private static final ImmutableWorkflow WORKFLOW =
      ImmutableWorkflow.of(WORKFLOW_ID, "my-workflow", State.RUNNING);

  private static final PipelineLight PIPELINE_LIGHT;
  private static final PipelineWithWorkflows PIPELINE_WITH_WORKFLOWS;

  private static final PipelineWithWorkflowsWorkflows PIPELINE_WITH_WORKFLOWS_WORKFLOWS;
  private static final com.circleci.client.v2.model.Workflow CIRCLECI_WORKFLOW;

  static {
    CIRCLECI_HAPPY = mock(DefaultApi.class);
    CIRCLECI_404 = mock(DefaultApi.class);
    CIRCLECI_404_JSON = mock(DefaultApi.class);
    CIRCLECI_500 = mock(DefaultApi.class);

    PIPELINE_LIGHT = new PipelineLight();
    PIPELINE_LIGHT.setId(PIPELINE_ID);

    PIPELINE_WITH_WORKFLOWS = new PipelineWithWorkflows();
    PIPELINE_WITH_WORKFLOWS.setId(PIPELINE_ID);
    PIPELINE_WITH_WORKFLOWS.setVcs(
        new PipelineWithWorkflowsVcs() {
          @Override
          public String getRevision() {
            return PIPELINE_REVISION;
          }
        });
    PIPELINE_WITH_WORKFLOWS_WORKFLOWS = new PipelineWithWorkflowsWorkflows();
    PIPELINE_WITH_WORKFLOWS_WORKFLOWS.setId(WORKFLOW_ID);
    PIPELINE_WITH_WORKFLOWS.setWorkflows(List.of(PIPELINE_WITH_WORKFLOWS_WORKFLOWS));
    CIRCLECI_WORKFLOW = new com.circleci.client.v2.model.Workflow();
    CIRCLECI_WORKFLOW.setId(WORKFLOW_ID);
    CIRCLECI_WORKFLOW.setName("my-workflow");
    CIRCLECI_WORKFLOW.setStatus(StatusEnum.RUNNING);

    try {
      when(CIRCLECI_HAPPY.triggerPipeline(
              anyString(), anyString(), anyString(), any(TriggerPipelineParameters.class)))
          .thenReturn(PIPELINE_LIGHT);
      when(CIRCLECI_HAPPY.getPipelineById(PIPELINE_ID)).thenReturn(PIPELINE_WITH_WORKFLOWS);
      when(CIRCLECI_HAPPY.getWorkflowById(WORKFLOW_ID)).thenReturn(CIRCLECI_WORKFLOW);

      when(CIRCLECI_404.triggerPipeline(
              anyString(), anyString(), anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "No such project"));
      when(CIRCLECI_404.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(404, "No such pipeline"));
      when(CIRCLECI_404.getWorkflowById(any(UUID.class)))
          .thenThrow(new ApiException(404, "No such workflow"));

      when(CIRCLECI_404_JSON.triggerPipeline(
              anyString(), anyString(), anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such project\"}"));
      when(CIRCLECI_404_JSON.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such pipeline\"}"));
      when(CIRCLECI_404_JSON.getWorkflowById(any(UUID.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such workflow\"}"));

      when(CIRCLECI_500.triggerPipeline(
              anyString(), anyString(), anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
      when(CIRCLECI_500.getPipelineById(any(UUID.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
      when(CIRCLECI_500.getWorkflowById(any(UUID.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
    } catch (ApiException e) {
      // Ignore when mocking
    }
  }

  @Test
  void allWorkflowsStatesCovered() {
    List<StatusEnum> states = Arrays.asList(StatusEnum.values());
    assertEquals(new HashSet<>(states), CircleCi.CIRCLECI_TO_WORKFLOW_STATE_MAP.keySet());
  }

  @Test
  void refreshPipelineIfCircleCiReturns4xxWeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(RuntimeException.class, () -> circleCi.refreshPipeline(PIPELINE_WITH_ID));
  }

  @Test
  void refreshPipelineIfCircleCiReturns4xxWithAJsonMessageWeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(RuntimeException.class, () -> circleCi.refreshPipeline(PIPELINE_WITH_ID));
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
    assertEquals(Set.of(WORKFLOW), pipeline.workflows());
  }

  @Test
  void refreshWorkflowIfCircleCiReturns4xxWeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(RuntimeException.class, () -> circleCi.refreshWorkflow(WORKFLOW));
  }

  @Test
  void refreshWorkflowIfCircleCiReturns4xxWithAJsonMessageWeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(RuntimeException.class, () -> circleCi.refreshWorkflow(WORKFLOW));
  }

  @Test
  void refreshWorkflowIfCircleCiReturns500WeThrow() {
    CircleCi circleCi = new CircleCi(CIRCLECI_500);
    assertThrows(RuntimeException.class, () -> circleCi.refreshWorkflow(WORKFLOW));
  }

  @Test
  void refreshWorkflowSuccess() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);
    com.circleci.connector.gitlab.singleorg.model.Workflow workflow =
        circleCi.refreshWorkflow(WORKFLOW);
    assertNotNull(workflow);
    assertEquals(CIRCLECI_WORKFLOW.getId(), workflow.id());
  }

  @Test
  void triggerPipelineIfCircleCiReturns4xxWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturns4xxWithAJsonMessageWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturns500WeThrowRuntimeException() {
    CircleCi circleCi = new CircleCi(CIRCLECI_500);
    assertThrows(
        RuntimeException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", "", "", ""));
  }

  @Test
  void triggerPipelineIfPipelineAlreadyTriggeredError() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);

    assertThrows(
        RuntimeException.class,
        () -> circleCi.triggerPipeline(PIPELINE_WITH_ID, "", "", "", "", "", ""));
  }

  @Test
  void triggerPipelineIfCircleCiReturnsPipelineSuccess() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);
    Pipeline pipeline = circleCi.triggerPipeline(PIPELINE_WITHOUT_ID, "", "", "", "", "", "");
    assertNotNull(pipeline);
    assertEquals(PIPELINE_LIGHT.getId(), pipeline.id());
  }
}
