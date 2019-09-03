package com.circleci.gitlab_single_org_connector.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;

public class PushHook {
  enum ObjectKind {
    push
  }

  // This is a CircleCI-provided value and not from GitLab.
  private UUID id;

  @NotEmpty private ObjectKind objectKind;

  @NotEmpty private String before;

  @NotEmpty private String after;

  @NotEmpty private String ref;

  @NotNull private Project project;

  @Range(min = 0)
  private int userId;

  @NotEmpty private String userName;

  @NotEmpty private String userUsername;

  @JsonIgnore
  public UUID getId() {
    return id;
  }

  @JsonIgnore
  public void setId(UUID circleCiId) {
    id = circleCiId;
  }

  @JsonProperty("object_kind")
  public ObjectKind getObjectKind() {
    return objectKind;
  }

  @JsonProperty("object_kind")
  public void setObjectKind(ObjectKind ok) {
    objectKind = ok;
  }

  @JsonProperty
  public String getBefore() {
    return before;
  }

  @JsonProperty
  public void setBefore(String sha1) {
    before = sha1;
  }

  @JsonProperty
  public String getAfter() {
    return after;
  }

  @JsonProperty
  public void setAfter(String sha1) {
    after = sha1;
  }

  @JsonProperty
  public String getRef() {
    return ref;
  }

  @JsonProperty
  public void setRef(String gitRef) {
    ref = gitRef;
  }

  @JsonProperty("user_id")
  public int getUserId() {
    return userId;
  }

  @JsonProperty("user_id")
  public void setUserId(int id) {
    userId = id;
  }

  @JsonProperty("user_name")
  public String getUserName() {
    return userName;
  }

  @JsonProperty("user_name")
  public void setUserName(String name) {
    userName = name;
  }

  @JsonProperty("user_username")
  public String getUserUsername() {
    return userUsername;
  }

  @JsonProperty("user_username")
  public void setUserUsername(String username) {
    userUsername = username;
  }

  @JsonProperty
  public Project getProject() {
    return project;
  }

  @JsonProperty
  public void setProject(Project p) {
    project = p;
  }

  static class Project {
    @Range(min = 1)
    private int id;

    @NotEmpty private String name;

    @NotEmpty private String gitSshUrl;

    public Project() {}

    @JsonProperty
    public int getId() {
      return id;
    }

    @JsonProperty
    public void setId(int projectId) {
      id = projectId;
    }

    @JsonProperty
    public String getName() {
      return name;
    }

    @JsonProperty
    public void setName(String n) {
      name = n;
    }

    @JsonProperty("git_ssh_url")
    public String getGitSshUrl() {
      return gitSshUrl;
    }

    @JsonProperty("git_ssh_url")
    public void setGitSshUrl(String sshUrl) {
      gitSshUrl = sshUrl;
    }
  }
}
