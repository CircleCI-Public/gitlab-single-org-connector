package com.circleci.connector.gitlab.singleorg.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.UUID;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePushHook.class)
@JsonDeserialize(as = ImmutablePushHook.class)
public abstract class PushHook {
  enum ObjectKind {
    push
  }

  @Value.Derived
  @JsonIgnore
  public UUID id() {
    return UUID.randomUUID();
  }

  @Value.Derived
  @JsonIgnore
  public String branch() {
    if (ref() != null && ref().startsWith("refs/heads/")) {
      return ref().substring(11);
    }
    return null;
  }

  @JsonProperty("object_kind")
  @NotEmpty
  abstract ObjectKind objectKind();

  @NotEmpty
  abstract String before();

  @NotEmpty
  public abstract String after();

  @NotEmpty
  public abstract String ref();

  @JsonProperty("user_id")
  @Range(min = 0)
  public abstract int userId();

  @JsonProperty("user_name")
  @NotEmpty
  abstract String userName();

  @JsonProperty("user_username")
  @NotEmpty
  abstract String userUsername();

  @JsonProperty("user_email")
  @NotEmpty
  public abstract String userEmail();

  @NotNull
  public abstract Project project();

  @Value.Immutable
  @JsonSerialize(as = ImmutableProject.class)
  @JsonDeserialize(as = ImmutableProject.class)
  public abstract static class Project {
    @Range(min = 1)
    public abstract int id();

    @NotEmpty
    abstract String name();

    @JsonProperty("git_ssh_url")
    @NotEmpty
    public abstract String gitSshUrl();
  }
}
