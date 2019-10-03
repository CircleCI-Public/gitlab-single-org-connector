package com.circleci.connector.gitlab.singleorg.client;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.Optional;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitLab {
  private static final Logger LOGGER = LoggerFactory.getLogger(GitLab.class);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private GitLabApi gitLabApi;

  public GitLab(GitLabApi gitLabApi) {
    this.gitLabApi = gitLabApi;
  }

  static final String CIRCLECI_CONFIG_PATH = ".circleci/config.yml";
  private static final String STRING_PARAMETER_YAML = "{type: string, default: ''}";
  private static final String GIT_CHECKOUT_COMMAND_YAML =
      "steps:\n"
          + "  - add_ssh_keys:\n"
          + "      fingerprints: [<< pipeline.parameters.gitlab_ssh_fingerprint >>]\n"
          + "  - run: |\n"
          + "      mkdir -p ~/.ssh\n"
          + "      ssh-keyscan gitlab.com >> ~/.ssh/known_hosts\n"
          + "      git clone --depth=1 << pipeline.parameters.gitlab_git_uri >> project";

  /**
   * Extends the given CircleCI config with a git-checkout command and gitlab_ssh_fingerprint and
   * gitlab_git_uri pipeline parameters
   *
   * @param root root node of the YAML config
   * @return The config extended with the git-checkout command and git-related parameters
   */
  ObjectNode extendCircleCiConfig(TreeNode root) {
    try {
      ObjectNode rootNode;
      if (root == null) {
        rootNode = YAML_MAPPER.createObjectNode();
      } else {
        rootNode = (ObjectNode) root;
      }

      JsonNode stringParameterNode = YAML_MAPPER.readTree(STRING_PARAMETER_YAML);
      JsonNode gitCheckoutCommandNode = YAML_MAPPER.readTree(GIT_CHECKOUT_COMMAND_YAML);

      ObjectNode parametersNode = rootNode.with("parameters");
      ObjectNode commandsNode = rootNode.with("commands");

      parametersNode.set("gitlab_ssh_fingerprint", stringParameterNode);
      parametersNode.set("gitlab_git_uri", stringParameterNode);
      commandsNode.set("git-checkout", gitCheckoutCommandNode);

      return rootNode;
    } catch (IOException | ClassCastException e) {
      LOGGER.warn("Error parsing CircleCI config", e);
      return null;
    }
  }
  /**
   * Get CircleCI config contents from GitLab project at ref
   *
   * @param projectId GitLab project id
   * @return A string with the contents of CircleCI configuration file
   */
  public Optional<String> fetchCircleCiConfig(int projectId, String ref) {
    LOGGER.info("Fetching CircleCI config for project {} at ref {}", projectId, ref);
    try {
      String config =
          gitLabApi
              .getRepositoryFileApi()
              .getFile(projectId, CIRCLECI_CONFIG_PATH, ref)
              .getDecodedContentAsString();

      ObjectNode rootNode = (ObjectNode) YAML_MAPPER.readTree(config);
      return Optional.ofNullable(
          YAML_MAPPER.writer().writeValueAsString(extendCircleCiConfig(rootNode)));
    } catch (GitLabApiException e) {
      LOGGER.warn("Error fetching CircleCI config", e);
    } catch (IOException e) {
      LOGGER.warn("Error reading CircleCI config", e);
    }
    return Optional.empty();
  }
}
