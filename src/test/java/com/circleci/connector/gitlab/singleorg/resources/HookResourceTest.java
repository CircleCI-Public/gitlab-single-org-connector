package com.circleci.connector.gitlab.singleorg.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.client.CircleCi;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.FixtureHelpers;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HookResourceTest {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final ConnectorConfiguration EMPTY_CONFIG;
  private static final ConnectorConfiguration MINIMAL_CONFIG;
  private static final ConnectorConfiguration CONFIG_WITH_SECRET;
  private static final String GITLAB_DOCS_HOOK =
      FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");
  private static GitLab GITLAB_HAPPY;
  private static GitLab GITLAB_SAD;
  private static CircleCi CIRCLECI_HAPPY;
  private static CircleCi CIRCLECI_SAD;
  private static ScheduledExecutorService JOB_RUNNER;

  static {
    EMPTY_CONFIG = configFromString("{}");

    MINIMAL_CONFIG =
        configFromString(
            "{"
                + "\"domainMapping\":{"
                + "\"repositories\":{"
                + "\"15\": \"gh/foo/bar\""
                + "}"
                + "}"
                + "}");

    CONFIG_WITH_SECRET =
        configFromString(
            "{"
                + "\"gitlab\": {"
                + "\"sharedSecretForHooks\":\"super-secret\""
                + "},"
                + "\"domainMapping\":{"
                + "\"repositories\":{"
                + "\"15\": \"gh/foo/bar\""
                + "}"
                + "}"
                + "}");
  }

  @BeforeEach
  void setUp() {

    GITLAB_HAPPY = mock(GitLab.class);
    when(GITLAB_HAPPY.fetchCircleCiConfig(anyInt(), anyString())).thenReturn(Optional.of("config"));

    GITLAB_SAD = mock(GitLab.class);
    when(GITLAB_SAD.fetchCircleCiConfig(anyInt(), anyString())).thenReturn(Optional.empty());

    CIRCLECI_HAPPY = mock(CircleCi.class);
    CIRCLECI_SAD = mock(CircleCi.class);
    when(CIRCLECI_HAPPY.triggerPipeline(
            any(Pipeline.class),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
        .thenReturn(ImmutablePipeline.of(UUID.randomUUID(), 43, "abcd", "master"));
    when(CIRCLECI_SAD.triggerPipeline(
            any(Pipeline.class),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
        .thenThrow(new RuntimeException("bad things happened"));

    JOB_RUNNER = mock(ScheduledExecutorService.class);
  }

  private static ConnectorConfiguration configFromString(String config) {
    try {
      return MAPPER.readValue(config, ConnectorConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void wePassTheRightArgumentsToTriggerPipelineWhenProcessingHook() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null);
    verify(CIRCLECI_HAPPY)
        .triggerPipeline(
            any(Pipeline.class),
            eq("config"),
            eq("4"),
            eq("john@example.com"),
            eq("gh/foo/bar"),
            eq(""),
            eq("git@example.com:mike/diaspora.git"));
  }

  @Test
  void weCanProcessTheHookFromGitlabDocs() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null);
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeAgree() throws Exception {
    HookResource hr =
        new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, CONFIG_WITH_SECRET);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "super-secret");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeDoNotCare() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void ignoreTheHookWhenWeCannotFindACircleCIConfig() throws Exception {
    HookResource hr = new HookResource(GITLAB_SAD, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.IGNORED, response.status());
  }

  @Test
  void weThrowA403IfTheGitlabTokenIsNotSupplied() throws Exception {
    HookResource hr =
        new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, CONFIG_WITH_SECRET);
    assertThrows(
        WebApplicationException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void weThrowA403IfTheGitlabTokenDoesNotMatch() throws Exception {
    HookResource hr =
        new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, CONFIG_WITH_SECRET);
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "wrong-token"));
  }

  @Test
  void nonPushHookTypesReturnIgnored() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    List<String> nonPushHookTypes =
        Arrays.asList(
            "Tag Push Hook",
            "Issue Hook",
            "Note Hook",
            "Merge Request Hook",
            "Wiki Page Hook",
            "Pipeline Hook",
            "Job Hook");
    for (String nonPushHookType : nonPushHookTypes) {
      HookResponse response = hr.processHook("{}", nonPushHookType, null);
      assertEquals(HookResponse.Status.IGNORED, response.status());
    }
  }

  @Test
  void ifThereIsNoGitlabEventHeaderWeThrow400() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, JOB_RUNNER, MINIMAL_CONFIG);
    assertThrows(WebApplicationException.class, () -> hr.processHook("{}", null, null));
  }
}
