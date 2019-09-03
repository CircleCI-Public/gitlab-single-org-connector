package com.circleci.gitlab_single_org_connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.FixtureHelpers;
import org.junit.jupiter.api.Test;

class PushHookTest {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

  @Test
  void testSampleGitlabHookFromGitlabDocs() throws Exception {
    String hookJson = FixtureHelpers.fixture("gitlab-push-hook-from-docs.json");
    assertNotNull(hookJson);
    PushHook hook = MAPPER.readValue(hookJson, PushHook.class);
    assertEquals("push", hook.getObjectKind().toString());
    assertEquals("95790bf891e76fee5e1747ab589903a6a1f80f22", hook.getBefore());
    assertEquals("da1560886d4f094c3e6c9ef40349f7d38b5d27d7", hook.getAfter());
    assertEquals("refs/heads/master", hook.getRef());
    assertEquals(4, hook.getUserId());
    assertEquals("John Smith", hook.getUserName());
    assertEquals("jsmith", hook.getUserUsername());
    assertEquals(15, hook.getProject().getId());
    assertEquals("Diaspora", hook.getProject().getName());
    assertEquals("git@example.com:mike/diaspora.git", hook.getProject().getGitSshUrl());
  }
}
