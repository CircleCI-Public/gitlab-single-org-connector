package com.circleci.connector.gitlab.singleorg.client;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.PipelineWithWorkflows;
import com.circleci.client.v2.model.PipelineWithWorkflowsWorkflows;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.client.v2.model.Workflow.StatusEnum;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.ImmutableWorkflow;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

  public static final Map<StatusEnum, State> CIRCLECI_TO_WORKFLOW_STATE_MAP =
      Map.of(
          StatusEnum.RUNNING, State.RUNNING,
          StatusEnum.FAILING, State.RUNNING,
          StatusEnum.ON_HOLD, State.RUNNING,
          StatusEnum.SUCCESS, State.SUCCESS,
          StatusEnum.FAILED, State.FAILED,
          StatusEnum.NOT_RUN, State.FAILED,
          StatusEnum.ERROR, State.FAILED,
          StatusEnum.UNAUTHORIZED, State.FAILED,
          StatusEnum.CANCELED, State.CANCELED);

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

    try {
      pipelineWithWorkflows = circleCiApi.getPipelineById(pipeline.id());
    } catch (ApiException e) {
      LOGGER.error("Failed to fetch pipeline", e);
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

    List<PipelineWithWorkflowsWorkflows> circleCiWorkflows = pipelineWithWorkflows.getWorkflows();
    Set<Workflow> workflows = new HashSet<>();
    for (PipelineWithWorkflowsWorkflows circleCiWorkflow : circleCiWorkflows) {
      workflows.add(fetchWorkflow(circleCiWorkflow.getId()));
    }

    return ImmutablePipeline.copyOf(pipeline).withWorkflows(workflows);
  }

  private Workflow fetchWorkflow(UUID circleCiWorkflowId) {
    com.circleci.client.v2.model.Workflow circleCiWorkflow = null;
    try {
      circleCiWorkflow = circleCiApi.getWorkflowById(circleCiWorkflowId);
    } catch (ApiException e) {
      LOGGER.error("Failed to fetch workflow", e);
      throw new RuntimeException(e);
    }

    StatusEnum circleCiState = circleCiWorkflow.getStatus();

    if (circleCiState == null || !CIRCLECI_TO_WORKFLOW_STATE_MAP.containsKey(circleCiState)) {
      throw new IllegalArgumentException(
          String.format("Unknown workflow state %s", circleCiState.name()));
    }

    State workflowState = CIRCLECI_TO_WORKFLOW_STATE_MAP.get(circleCiState);

    return ImmutableWorkflow.of(circleCiWorkflowId, circleCiWorkflow.getName(), workflowState);
  }

  public Pipeline triggerPipeline(
      Pipeline pipeline,
      String circleCiConfig,
      String userId,
      String login,
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
      PipelineLight pipelineLight = circleCiApi.triggerPipeline(projectSlug, login, userId, params);
      return ImmutablePipeline.builder().from(pipeline).id(pipelineLight.getId()).build();
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

  public Workflow refreshWorkflow(Workflow workflow) {
    return fetchWorkflow(workflow.id());
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
