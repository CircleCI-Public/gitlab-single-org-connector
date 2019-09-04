package com.circleci.connector.gitlab.singleorg.api;

import com.circleci.connector.gitlab.singleorg.resources.HookResource;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** For responses to the /hooks/* endpoints as implemented by {@link HookResource}. */
public class HookResponse {
  public enum Status {
    SUBMITTED,
    IGNORED
  }

  private final UUID id;

  private final Status status;

  public HookResponse(UUID hookId, Status s) {
    id = hookId;
    status = s;
  }

  @JsonProperty
  public UUID getId() {
    return id;
  }

  @JsonProperty
  public Status getStatus() {
    return status;
  }
}
