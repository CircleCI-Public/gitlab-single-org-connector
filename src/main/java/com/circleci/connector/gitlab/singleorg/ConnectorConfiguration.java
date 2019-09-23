package com.circleci.connector.gitlab.singleorg;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Range;

public class ConnectorConfiguration extends Configuration {
  @Valid private CircleCi circleCi;

  @Valid private GitLab gitlab;

  @Valid private Statsd statsd;

  @Valid private DomainMapping domainMapping;

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

  public DomainMapping getDomainMapping() {
    if (domainMapping == null) {
      return new DomainMapping();
    }
    return domainMapping;
  }

  public void setDomainMapping(DomainMapping domainMapping) {
    this.domainMapping = domainMapping;
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

  public static class DomainMapping {
    private Map<@Range(min = 0) Integer, @Pattern(regexp = "[^/]+/[^/]+/[^/]+") String>
        repositories;

    public Map<Integer, String> getRepositories() {
      return repositories;
    }

    public DomainMapping() {}

    public DomainMapping(Map<Integer, String> repositories) {
      this.repositories = repositories;
    }

    /**
     * @param repositories A map of GitLab repository ids to CircleCI repository paths, like
     *     "gh/org/repo"
     */
    public void setRepositories(Map<Integer, String> repositories) {
      this.repositories = repositories;
    }
  }
}
