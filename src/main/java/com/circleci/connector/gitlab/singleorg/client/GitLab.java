package com.circleci.connector.gitlab.singleorg.client;

import java.util.Optional;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitLab {
  private static final Logger LOGGER = LoggerFactory.getLogger(GitLab.class);
  private GitLabApi gitLabApi;

  public GitLab(GitLabApi gitLabApi) {
    this.gitLabApi = gitLabApi;
  }

  static final String CIRCLECI_CONFIG_PATH = ".circleci/config.yml";

  /**
   * Get CircleCI config contents from GitLab project at ref
   *
   * @param projectId GitLab project id
   * @return A string with the contents of CircleCI configuration file
   */
  public Optional<String> fetchCircleCiConfig(int projectId, String ref) {
    LOGGER.info("Fetching CircleCI config for project {} at ref {}", projectId, ref);
    try {
      return Optional.ofNullable(
          gitLabApi
              .getRepositoryFileApi()
              .getFile(projectId, CIRCLECI_CONFIG_PATH, ref)
              .getContent());
    } catch (GitLabApiException e) {
      LOGGER.warn("Error fetching CircleCI config", e);
    }
    return Optional.empty();
  }
}
