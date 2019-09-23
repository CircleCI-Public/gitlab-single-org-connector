package com.circleci.connector.gitlab.singleorg;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

public class ConnectorConfiguration extends Configuration {
  @Valid private CircleCi circleCi;

  @Valid private GitLab gitlab;

  @Valid private Statsd statsd;

  public ConnectorConfiguration() {}

  CircleCi getCircleCi() {
    if (circleCi == null) {
      return new CircleCi();
    }
    return circleCi;
  }

  void setCircleCi(CircleCi cci) {
    circleCi = cci;
  }

  GitLab getGitlab() {
    if (gitlab == null) {
      return new GitLab();
    }
    return gitlab;
  }

  void setGitlab(GitLab g) {
    gitlab = g;
  }

  Statsd getStatsd() {
    if (statsd == null) {
      return new Statsd();
    }
    return statsd;
  }

  void setStatsd(Statsd s) {
    statsd = s;
  }

  static class CircleCi {
    @NotEmpty private String apiToken;

    public CircleCi() {}

    @JsonProperty
    String getApiToken() {
      return apiToken;
    }

    @JsonProperty
    void setApiToken(String token) {
      apiToken = token;
    }
  }

  static class GitLab {
    private String sharedSecretForHooks;

    private String host = "https://gitlab.com";

    private String authToken;

    GitLab() {}

    @JsonProperty
    String getSharedSecretForHooks() {
      return sharedSecretForHooks;
    }

    @JsonProperty
    void setSharedSecretForHooks(String secret) {
      sharedSecretForHooks = secret;
    }

    @JsonProperty
    public String getHost() {
      return host;
    }

    @JsonProperty
    public void setHost(String host) {
      this.host = host;
    }

    @JsonProperty
    public String getAuthToken() {
      return authToken;
    }

    @JsonProperty
    public void setAuthToken(String authToken) {
      this.authToken = authToken;
    }
  }

  static class Statsd {
    private String host;

    @Range(min = 0, max = 65535)
    private int port = 8125;

    @Range(min = 1)
    private int refreshPeriodSeconds = 10;

    public Statsd() {}

    @JsonProperty
    String getHost() {
      return host;
    }

    @JsonProperty
    void setHost(String hostname) {
      host = hostname;
    }

    @JsonProperty
    int getPort() {
      return port;
    }

    @JsonProperty
    void setPort(int p) {
      port = p;
    }

    @JsonProperty
    int getRefreshPeriodSeconds() {
      return refreshPeriodSeconds;
    }

    @JsonProperty
    void setRefreshPeriodSeconds(int seconds) {
      refreshPeriodSeconds = seconds;
    }
  }
}
