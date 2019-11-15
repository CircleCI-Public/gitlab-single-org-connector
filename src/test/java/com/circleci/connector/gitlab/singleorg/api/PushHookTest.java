package com.circleci.connector.gitlab.singleorg.api;

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
    assertEquals("push", hook.objectKind().toString());
    assertEquals("95790bf891e76fee5e1747ab589903a6a1f80f22", hook.before());
    assertEquals("da1560886d4f094c3e6c9ef40349f7d38b5d27d7", hook.after());
    assertEquals("refs/heads/master", hook.ref());
    assertEquals(4, hook.userId());
    assertEquals("John Smith", hook.userName());
    assertEquals("jsmith", hook.userUsername());
    assertEquals("john@example.com", hook.userEmail());
    assertEquals(15, hook.project().id());
    assertEquals("Diaspora", hook.project().name());
    assertEquals("git@example.com:mike/diaspora.git", hook.project().gitSshUrl());
  }
}
