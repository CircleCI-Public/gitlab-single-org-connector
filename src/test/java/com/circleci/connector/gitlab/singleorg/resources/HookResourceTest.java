package com.circleci.connector.gitlab.singleorg.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.circleci.connector.gitlab.singleorg.api.HookResponse;
import io.dropwizard.testing.FixtureHelpers;
import javax.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class HookResourceTest {
  private static final String gitLabPushHookFromDocs =
      FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");

  @Test
  void weCanProcessTheHookFromGitlabDocs() throws Exception {
    HookResource hr = new HookResource(null);
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, null);
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeAgree() throws Exception {
    HookResource hr = new HookResource("super-secret");
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "super-secret");
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weCanProcessTheHookFromGitlabDocsWhenItSuppliesATokenAndWeDoNotCare() throws Exception {
    HookResource hr = new HookResource(null);
    HookResponse response = hr.processHook(gitLabPushHookFromDocs, "token-for-us-to-ignore");
    assertEquals(HookResponse.Status.SUBMITTED, response.getStatus());
  }

  @Test
  void weThrowA403IfTheGitlabTokenIsNotSupplied() throws Exception {
    HookResource hr = new HookResource("super-secret");
    org.junit.jupiter.api.Assertions.assertThrows(
        WebApplicationException.class, () -> hr.processHook(gitLabPushHookFromDocs, null));
  }

  @Test
  void weThrowA403IfTheGitlabTokenDoesNotMatch() throws Exception {
    HookResource hr = new HookResource("super-secret");
    org.junit.jupiter.api.Assertions.assertThrows(
        WebApplicationException.class, () -> hr.processHook(gitLabPushHookFromDocs, "wrong-token"));
  }
}
