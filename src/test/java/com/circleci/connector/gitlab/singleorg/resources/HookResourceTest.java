package com.circleci.connector.gitlab.singleorg.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import io.dropwizard.testing.FixtureHelpers;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HookResourceTest {
  private static final String gitLabPushHookFromDocs =
      FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");
  private GitLab mockGitLab = Mockito.mock(GitLab.class);
  private static final ConnectorConfiguration.DomainMapping domainMapping =
      new ConnectorConfiguration.DomainMapping(Map.of(15, "gh/myorg/myrepo"));

  @Test
  void weCanProcessTheHookFromGitlabDocs() throws Exception {
    Mockito.when(mockGitLab.fetchCircleCiConfig(Mockito.anyInt(), Mockito.anyString()))
        .thenReturn(Optional.of("config"));
    HookResource hr = new HookResource(mockGitLab, null, domainMapping);
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "Push Hook", null);
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeAgree() throws Exception {
    Mockito.when(mockGitLab.fetchCircleCiConfig(Mockito.anyInt(), Mockito.anyString()))
        .thenReturn(Optional.of("config"));
    HookResource hr = new HookResource(mockGitLab, "super-secret", domainMapping);
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "Push Hook", "super-secret");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeDoNotCare() throws Exception {
    Mockito.when(mockGitLab.fetchCircleCiConfig(Mockito.anyInt(), Mockito.anyString()))
        .thenReturn(Optional.of("config"));
    HookResource hr = new HookResource(mockGitLab, null, domainMapping);
    HookResponse response =
        hr.processHook(gitLabPushHookFromDocs, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.SUBMITTED, response.status());
  }

  @Test
  void ignoreTheHookWhenRepositoryIsNotMapped() throws Exception {
    Mockito.when(mockGitLab.fetchCircleCiConfig(Mockito.anyInt(), Mockito.anyString()))
        .thenReturn(Optional.of("config"));
    HookResource hr =
        new HookResource(
            mockGitLab,
            null,
            new ConnectorConfiguration.DomainMapping(
                Map.of(1000, "gh/myorg/myrepo"))); // id 15 not mapped
    HookResponse response =
        hr.processHook(gitLabPushHookFromDocs, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.IGNORED, response.status());
  }

  @Test
  void ignoreTheHookWhenWeCannotFindACircleCIConfig() throws Exception {
    Mockito.when(mockGitLab.fetchCircleCiConfig(Mockito.anyInt(), Mockito.anyString()))
        .thenReturn(Optional.empty());
    HookResource hr = new HookResource(mockGitLab, null, domainMapping);
    HookResponse response =
        hr.processHook(gitLabPushHookFromDocs, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.IGNORED, response.status());
  }

  @Test
  void weThrowA403IfTheGitlabTokenIsNotSupplied() throws Exception {
    HookResource hr = new HookResource(mockGitLab, "super-secret", domainMapping);
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(gitLabPushHookFromDocs, "Push Hook", null));
  }

  @Test
  void weThrowA403IfTheGitlabTokenDoesNotMatch() throws Exception {
    HookResource hr = new HookResource(mockGitLab, "super-secret", domainMapping);
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(gitLabPushHookFromDocs, "Push Hook", "wrong-token"));
  }

  @Test
  void nonPushHookTypesReturnIgnored() throws Exception {
    HookResource hr = new HookResource(mockGitLab, null, domainMapping);
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
    HookResource hr = new HookResource(mockGitLab, null, domainMapping);
    assertThrows(WebApplicationException.class, () -> hr.processHook("{}", null, null));
  }
}
