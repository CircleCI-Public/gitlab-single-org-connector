package com.circleci.connector.gitlab.singleorg.api;

import com.circleci.connector.gitlab.singleorg.resources.HookResource;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.immutables.value.Value;

/** For responses to the /hooks/* endpoints as implemented by {@link HookResource}. */
@Value.Immutable
public abstract class HookResponse {
  public enum Status {
    SUBMITTED,
    IGNORED
  }

  @JsonProperty
  public abstract UUID id();

  @JsonProperty
  public abstract Status status();
}
