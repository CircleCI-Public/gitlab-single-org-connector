package com.circleci.gitlab_single_org_connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Test the representation of responses to GitLab hooks. */
class HookResponseTest {
  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

  @Test
  void hookResponseSerializes() throws Exception {
    UUID id = UUID.fromString("ae45d0e4-763a-493e-a655-76ce1195320a");
    HookResponse hr = new HookResponse(id, HookResponse.Status.SUBMITTED);
    assertEquals(id, hr.getId());
    assertEquals(HookResponse.Status.SUBMITTED, hr.getStatus());
    assertEquals(
        "{\"id\":\"ae45d0e4-763a-493e-a655-76ce1195320a\",\"status\":\"SUBMITTED\"}",
        MAPPER.writeValueAsString(hr));
  }
}
