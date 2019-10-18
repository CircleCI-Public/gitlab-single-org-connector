package com.circleci.connector.gitlab.singleorg.client;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.PipelineWithWorkflows;
import com.circleci.client.v2.model.PipelineWithWorkflowsWorkflows;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.client.v2.model.Workflow;
import com.circleci.client.v2.model.Workflow.StatusEnum;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline.State;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircleCi {

  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(CircleCi.class);

  /** The CircleCI API library, configured to call the CircleCI REST API. */
  @NotNull private final DefaultApi circleCiApi;

  public CircleCi(@NotNull DefaultApi circleCiApi) {
    this.circleCiApi = circleCiApi;
  }

  public static final Map<StatusEnum, State> CIRCLECI_WORKFLOW_TO_PIPELINE_STATE_MAP =
      Map.of(
          StatusEnum.RUNNING, State.RUNNING,
          StatusEnum.SUCCESS, State.SUCCESS,
          StatusEnum.FAILING, State.FAILED,
          StatusEnum.FAILED, State.FAILED,
          StatusEnum.ERROR, State.FAILED,
          StatusEnum.NOT_RUN, State.FAILED,
          StatusEnum.UNAUTHORIZED, State.FAILED,
          StatusEnum.CANCELED, State.CANCELED,
          StatusEnum.ON_HOLD, State.RUNNING);

  /**
   * Some ApiExceptions have JSON as their message. The JSON is just {"message": "The real message"}
   * so we attempt to unwrap it here in order to avoid double JSON in the output.
   *
   * @param e An ApiException as thrown by the CircleCI API.
   * @return The error message from the exception, possibly unwrapped from JSON.
   */
  private static String maybeGetCircleCiApiErrorMessage(ApiException e) {
    try {
      return MAPPER.readValue(e.getMessage(), CircleCiErrorResponse.class).getMessage();
    } catch (Exception _e) {
      return e.getMessage();
    }
  }

  public Pipeline refreshPipeline(Pipeline pipeline) {
    PipelineWithWorkflows pipelineWithWorkflows;
    HashSet<State> workflowStates = new HashSet<>();

    try {
      pipelineWithWorkflows = circleCiApi.getPipelineById(pipeline.id());
      for (PipelineWithWorkflowsWorkflows pipelineWithWorkflowsWorkflows :
          pipelineWithWorkflows.getWorkflows()) {
        workflowStates.add(getWorkflowState(pipelineWithWorkflowsWorkflows));
      }
    } catch (ApiException e) {
      // Pass through 4xx responses verbatim for ease of debugging.
      if (e.getCode() >= 400 && e.getCode() < 500) {
        throw new ClientErrorException(
            maybeGetCircleCiApiErrorMessage(e), Response.Status.fromStatusCode(e.getCode()));
      }
      throw new RuntimeException(e);
    }

    if (pipeline.id() != null && !pipeline.id().equals(pipelineWithWorkflows.getId())) {
      throw new IllegalArgumentException(
          String.format(
              "Unexpected id %s in triggered pipeline %s",
              pipelineWithWorkflows.getId(), pipeline));
    }

    if (!pipeline.revision().equals(pipelineWithWorkflows.getVcs().getRevision())) {
      throw new IllegalArgumentException(
          String.format("Unexpected revision in triggered pipeline %s", pipeline));
    }

    State pipelineState = pipelineStateFromWorkflowStates(workflowStates);
    return ImmutablePipeline.copyOf(pipeline).withState(pipelineState);
  }

  @VisibleForTesting
  static State pipelineStateFromWorkflowStates(Set<State> workflowStates) {
    if (workflowStates.contains(State.FAILED)) {
      return State.FAILED;
    }
    if (workflowStates.contains(State.CANCELED)) {
      return State.CANCELED;
    }
    if (workflowStates.contains(State.RUNNING)) {
      return State.RUNNING;
    }
    if (workflowStates.contains(State.PENDING)) {
      return State.PENDING;
    }
    if (workflowStates.contains(State.SUCCESS)) {
      return State.SUCCESS;
    }
    return State.PENDING;
  }

  private State getWorkflowState(PipelineWithWorkflowsWorkflows wf) throws ApiException {
    Workflow workflow = circleCiApi.getWorkflowById(wf.getId());
    StatusEnum circleCiState = workflow.getStatus();

    if (circleCiState == null
        || !CIRCLECI_WORKFLOW_TO_PIPELINE_STATE_MAP.containsKey(circleCiState)) {
      throw new IllegalArgumentException(String.format("Unknown workflow state {}", circleCiState));
    }

    return CIRCLECI_WORKFLOW_TO_PIPELINE_STATE_MAP.get(circleCiState);
  }

  public Pipeline triggerPipeline(
      Pipeline pipeline,
      String circleCiConfig,
      String projectSlug,
      String sshFingerprint,
      String gitSshUrl) {
    if (pipeline.triggered()) {
      throw new IllegalStateException("This pipeline was already triggered.");
    }
    try {
      var params = new TriggerPipelineWithConfigParameters();
      params.setConfig(circleCiConfig);
      params.setBranch(pipeline.branch());
      params.setRevision(pipeline.revision());
      params.setParameters(
          Map.of("gitlab_ssh_fingerprint", sshFingerprint, "gitlab_git_uri", gitSshUrl));
      PipelineLight pipelineLight = circleCiApi.triggerPipeline(projectSlug, params);
      return ImmutablePipeline.builder()
          .from(pipeline)
          .state(State.PENDING)
          .id(pipelineLight.getId())
          .build();
    } catch (ApiException e) {
      LOGGER.error("Failed to trigger pipeline", e);

      // Pass through 4xx responses verbatim for ease of debugging.
      if (e.getCode() >= 400 && e.getCode() < 500) {
        throw new ClientErrorException(
            maybeGetCircleCiApiErrorMessage(e), Response.Status.fromStatusCode(e.getCode()));
      }
      throw new RuntimeException(e);
    }
  }

  static class TriggerPipelineWithConfigParameters extends TriggerPipelineParameters {

    @JsonProperty private String config;

    @JsonProperty private String revision;

    @Nullable
    public String getConfig() {
      return config;
    }

    public void setConfig(String cfg) {
      config = cfg;
    }

    @Nullable
    public String getRevision() {
      return revision;
    }

    public void setRevision(String rev) {
      revision = rev;
    }
  }

  static class CircleCiErrorResponse {

    @JsonProperty private String message;

    public String getMessage() {
      return message;
    }

    public void setMessage(String msg) {
      message = msg;
    }
  }
}
