package com.circleci.connector.gitlab.singleorg.resources;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.api.ImmutableHookResponse;
import com.circleci.connector.gitlab.singleorg.api.ImmutablePushHook;
import com.circleci.connector.gitlab.singleorg.api.PushHook;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import com.circleci.connector.gitlab.singleorg.client.PipelineStatusPoller;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consume all hook types. */
@Path("/hook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HookResource {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(HookResource.class);

  /** A configured GitLab API client. */
  @NotNull private final GitLab gitLabClient;

  /** The CircleCI API library, configured to call the CircleCI REST API. */
  @NotNull private final DefaultApi circleCiApi;

  /** The configuration for this service. */
  @NotNull private final ConnectorConfiguration config;

  @NotNull private final ScheduledExecutorService scheduledJobRunner;

  /**
   * @param gitLabClient A configured GitLab API client.
   * @param circleCiApi The CircleCI API library, configured to call the CircleCI REST API.
   * @param config The configuration for this service.
   */
  public HookResource(
      GitLab gitLabClient,
      DefaultApi circleCiApi,
      ScheduledExecutorService scheduledJobRunner,
      ConnectorConfiguration config) {
    this.gitLabClient = gitLabClient;
    this.circleCiApi = circleCiApi;
    this.scheduledJobRunner = scheduledJobRunner;
    this.config = config;
  }

  /** Consume all hooks. */
  @POST
  @Timed
  public HookResponse processHook(
      String body,
      @HeaderParam("X-Gitlab-Event") String type,
      @HeaderParam("X-Gitlab-Token") String token)
      throws Exception {
    LOGGER.debug("Received hook type \"{}\" raw body: {}", type, body);
    maybeValidateGitLabToken(token);

    if ("Push Hook".equals(type)) {
      return processPushHook(body);
    } else if (type != null) {
      return ImmutableHookResponse.builder()
          .id(UUID.randomUUID())
          .status(HookResponse.Status.IGNORED)
          .build();
    } else {
      throw new BadRequestException("Expected X-Gitlab-Event header");
    }
  }

  /** Consume push hooks. */
  private HookResponse processPushHook(String body) throws Exception {
    // Parse the hook
    PushHook hook = MAPPER.readValue(body, ImmutablePushHook.class);
    LOGGER.info("Received a hook: {}", hook);
    int projectId = hook.project().id();

    ImmutableHookResponse.Builder responseBuilder = ImmutableHookResponse.builder().id(hook.id());

    // Find the slug for the GitHub project which we're using as a fake for the GitLab project
    // referred to in the push hook.
    String projectSlug = config.getDomainMapping().getRepositories().getOrDefault(projectId, null);
    if (projectSlug == null) {
      throw new NotFoundException("No project found with ID " + projectId);
    }

    String sshFingerprint =
        config.getDomainMapping().getSshFingerprints().getOrDefault(projectId, "");

    // Fetch the config from GitLab
    Optional<String> circleCiConfig = gitLabClient.fetchCircleCiConfig(projectId, hook.ref());
    if (circleCiConfig.isEmpty()) {
      LOGGER.info("Ignoring hook referring to project id {} without config", projectId);
      return responseBuilder.status(HookResponse.Status.IGNORED).build();
    }

    // Trigger a Pipeline on CircleCI
    PipelineLight pipeline;
    try {
      var params = new TriggerPipelineWithConfigParameters();
      params.setConfig(circleCiConfig.get());
      params.setBranch(hook.branch());
      params.setRevision(hook.after());
      params.setParameters(
          Map.of(
              "gitlab_ssh_fingerprint",
              sshFingerprint,
              "gitlab_git_uri",
              hook.project().gitSshUrl()));
      pipeline = circleCiApi.triggerPipeline(projectSlug, params);
    } catch (ApiException e) {
      LOGGER.error("Failed to trigger pipeline", e);

      // Pass through 4xx responses verbatim for ease of debugging.
      if (e.getCode() >= 400 && e.getCode() < 500) {
        throw new ClientErrorException(
            maybeGetCircleCiApiErrorMessage(e), Response.Status.fromStatusCode(e.getCode()));
      }
      throw e;
    }

    // Poll the CircleCI API for status updates to the pipeline and update GitLab appropriately
    (new PipelineStatusPoller(projectId, pipeline, circleCiApi, gitLabClient, scheduledJobRunner))
        .start();

    return responseBuilder.status(HookResponse.Status.SUBMITTED).pipeline(pipeline).build();
  }

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

  /**
   * Throw a 403 if the supplied X-Gitlab-Token header value does not match the required value.
   *
   * @param token The value of the X-Gitlab-Token header.
   * @throws WebApplicationException If the value of the header does not match the configured,
   *     expected value.
   */
  private void maybeValidateGitLabToken(String token) {
    String gitLabToken = config.getGitlab().getSharedSecretForHooks();
    if (gitLabToken != null && !gitLabToken.equals(token)) {
      throw new WebApplicationException(
          "Value of X-Gitlab-Token did not match configured value", Response.Status.FORBIDDEN);
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
