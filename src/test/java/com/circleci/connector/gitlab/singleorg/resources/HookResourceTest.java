package com.circleci.connector.gitlab.singleorg.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.FixtureHelpers;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class HookResourceTest {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final ConnectorConfiguration EMPTY_CONFIG;
  private static final ConnectorConfiguration MINIMAL_CONFIG;
  private static final ConnectorConfiguration CONFIG_WITH_SECRET;
  private static final String GITLAB_DOCS_HOOK =
      FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");
  private static final GitLab GITLAB_HAPPY;
  private static final GitLab GITLAB_SAD;
  private static final DefaultApi CIRCLECI_HAPPY;
  private static final DefaultApi CIRCLECI_404;
  private static final DefaultApi CIRCLECI_404_JSON;
  private static final DefaultApi CIRCLECI_500;

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

    GITLAB_HAPPY = mock(GitLab.class);
    when(GITLAB_HAPPY.fetchCircleCiConfig(anyInt(), anyString())).thenReturn(Optional.of("config"));

    GITLAB_SAD = mock(GitLab.class);
    when(GITLAB_SAD.fetchCircleCiConfig(anyInt(), anyString())).thenReturn(Optional.empty());

    CIRCLECI_HAPPY = mock(DefaultApi.class);
    CIRCLECI_404 = mock(DefaultApi.class);
    CIRCLECI_404_JSON = mock(DefaultApi.class);
    CIRCLECI_500 = mock(DefaultApi.class);
    try {
      when(CIRCLECI_HAPPY.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenReturn(new PipelineLight());
      when(CIRCLECI_404.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "No such project"));
      when(CIRCLECI_404_JSON.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such project\"}"));
      when(CIRCLECI_500.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
    } catch (ApiException e) {
      // Ignore when mocking
    }
  }

  private static ConnectorConfiguration configFromString(String config) {
    try {
      return MAPPER.readValue(config, ConnectorConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void withNoMappedRepositoriesWeReturn404() {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, EMPTY_CONFIG);
    assertThrows(
        NotFoundException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void ifCircleCiReturns4xxWePassItOn() {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_404, MINIMAL_CONFIG);
    assertThrows(
        ClientErrorException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void ifCircleCiReturns4xxWithAJsonMessageWePassItOn() {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_404_JSON, MINIMAL_CONFIG);
    assertThrows(
        ClientErrorException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void ifCircleCiReturns500WeThrowTheSameException() {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_500, MINIMAL_CONFIG);
    assertThrows(ApiException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void weCanProcessTheHookFromGitlabDocs() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null);
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeAgree() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, CONFIG_WITH_SECRET);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "super-secret");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeDoNotCare() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void ignoreTheHookWhenWeCannotFindACircleCIConfig() throws Exception {
    HookResource hr = new HookResource(GITLAB_SAD, CIRCLECI_HAPPY, MINIMAL_CONFIG);
    HookResponse response = hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.IGNORED, response.status());
  }

  @Test
  void weThrowA403IfTheGitlabTokenIsNotSupplied() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, CONFIG_WITH_SECRET);
    assertThrows(
        WebApplicationException.class, () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", null));
  }

  @Test
  void weThrowA403IfTheGitlabTokenDoesNotMatch() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, CONFIG_WITH_SECRET);
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(GITLAB_DOCS_HOOK, "Push Hook", "wrong-token"));
  }

  @Test
  void nonPushHookTypesReturnIgnored() throws Exception {
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, MINIMAL_CONFIG);
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
    HookResource hr = new HookResource(GITLAB_HAPPY, CIRCLECI_HAPPY, MINIMAL_CONFIG);
    assertThrows(WebApplicationException.class, () -> hr.processHook("{}", null, null));
  }
}
