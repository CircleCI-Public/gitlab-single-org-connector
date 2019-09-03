package com.circleci.connector.gitlab.singleorg.resources;

import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.api.PushHook;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.UUID;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consume all hook types. */
@Path("/hooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HookResource {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(HookResource.class);

  /**
   * If non-null, the value of the X-Gitlab-Token header must be equal to the value of this string
   * or else we will return a 403.
   */
  private final String gitLabToken;

  /**
   * @param gitLabToken A shared secret that - if non-null - will be checked against the value of
   *     the X-Gitlab-Token header. If the values do no match then the hooks will be rejected with a
   *     403.
   */
  public HookResource(String gitLabToken) {
    this.gitLabToken = gitLabToken;
  }

  /** Consume push hooks. */
  @POST
  @Timed
  @Path("/push")
  public HookResponse processHook(String body, @HeaderParam("X-Gitlab-Token") String token)
      throws Exception {
    LOGGER.debug("Push hook raw body: {}", body);
    maybeValidateGitLabToken(token);

    PushHook hook = MAPPER.readValue(body, PushHook.class);
    hook.setId(UUID.randomUUID());
    LOGGER.info("Received a hook: {}", hook);
    return new HookResponse(hook.getId(), HookResponse.Status.SUBMITTED);
  }

  /**
   * Throw a 403 if the supplied X-Gitlab-Token header value does not match the required value.
   *
   * @param token The value of the X-Gitlab-Token header.
   * @throws WebApplicationException If the value of the header does not match the configured,
   *     expected value.
   */
  private void maybeValidateGitLabToken(String token) {
    if (gitLabToken != null && !gitLabToken.equals(token)) {
      throw new WebApplicationException(
          "Value of X-Gitlab-Token did not match configured value", Response.Status.FORBIDDEN);
    }
  }
}
