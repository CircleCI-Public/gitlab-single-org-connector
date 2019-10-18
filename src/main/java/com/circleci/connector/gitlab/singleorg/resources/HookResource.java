package com.circleci.connector.gitlab.singleorg.resources;

import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.api.ImmutableHookResponse;
import com.circleci.connector.gitlab.singleorg.api.ImmutablePushHook;
import com.circleci.connector.gitlab.singleorg.api.PushHook;
import com.circleci.connector.gitlab.singleorg.client.CircleCi;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import com.circleci.connector.gitlab.singleorg.client.PipelineStatusPoller;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline.State;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
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

  @NotNull private final CircleCi circleCiClient;

  /** A configured GitLab API client. */
  @NotNull private final GitLab gitLabClient;

  /** The configuration for this service. */
  @NotNull private final ConnectorConfiguration config;

  @NotNull private final ScheduledExecutorService scheduledJobRunner;

  /**
   * @param gitLabClient A configured GitLab API client.
   * @param config The configuration for this service.
   */
  public HookResource(
      GitLab gitLabClient,
      CircleCi circleCiClient,
      ScheduledExecutorService scheduledJobRunner,
      ConnectorConfiguration config) {
    this.circleCiClient = circleCiClient;
    this.gitLabClient = gitLabClient;
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

    Pipeline pipeline =
        ImmutablePipeline.of(null, projectId, State.PENDING, hook.after(), hook.branch());
    // Trigger a Pipeline on CircleCI
    pipeline =
        circleCiClient.triggerPipeline(
            pipeline,
            circleCiConfig.get(),
            projectSlug,
            sshFingerprint,
            hook.project().gitSshUrl());

    // Poll the CircleCI API for status updates to the pipeline and update GitLab appropriately
    (new PipelineStatusPoller(pipeline, circleCiClient, gitLabClient, scheduledJobRunner)).start();

    return responseBuilder.status(HookResponse.Status.SUBMITTED).pipeline(pipeline).build();
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
}
