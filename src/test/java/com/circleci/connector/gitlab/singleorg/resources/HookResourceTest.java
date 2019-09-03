package com.circleci.connector.gitlab.singleorg.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import io.dropwizard.testing.FixtureHelpers;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class HookResourceTest {
  private static final String gitLabPushHookFromDocs =
      FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");

  @Test
  void weCanProcessTheHookFromGitlabDocs() throws Exception {
    HookResource hr = new HookResource(null);
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "Push Hook", null);
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeAgree() throws Exception {
    HookResource hr = new HookResource("super-secret");
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "Push Hook", "super-secret");
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeDoNotCare() throws Exception {
    HookResource hr = new HookResource(null);
    HookResponse response =
        hr.processHook(gitLabPushHookFromDocs, "Push Hook", "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weThrowA403IfTheGitlabTokenIsNotSupplied() throws Exception {
    HookResource hr = new HookResource("super-secret");
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(gitLabPushHookFromDocs, "Push Hook", null));
  }

  @Test
  void weThrowA403IfTheGitlabTokenDoesNotMatch() throws Exception {
    HookResource hr = new HookResource("super-secret");
    assertThrows(
        WebApplicationException.class,
        () -> hr.processHook(gitLabPushHookFromDocs, "Push Hook", "wrong-token"));
  }

  @Test
  void nonPushHookTypesReturnIgnored() throws Exception {
    HookResource hr = new HookResource(null);
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
      assertEquals(HookResponse.Status.IGNORED, response.getStatus());
    }
  }

  @Test
  void ifThereIsNoGitlabEventHeaderWeThrow400() throws Exception {
    HookResource hr = new HookResource(null);
    assertThrows(WebApplicationException.class, () -> hr.processHook("{}", null, null));
  }
}
