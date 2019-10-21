package com.circleci.connector.gitlab.singleorg.client;

import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import org.gitlab4j.api.Constants.CommitBuildState;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.CommitStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitLab {
  private static final Logger LOGGER = LoggerFactory.getLogger(GitLab.class);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private GitLabApi gitLabApi;

  public GitLab(GitLabApi gitLabApi) {
    this.gitLabApi = gitLabApi;
  }

  public static final Map<State, CommitBuildState> WORKFLOW_TO_GITLAB_STATE_MAP =
      Map.of(
          State.PENDING, CommitBuildState.PENDING,
          State.RUNNING, CommitBuildState.RUNNING,
          State.SUCCESS, CommitBuildState.SUCCESS,
          State.FAILED, CommitBuildState.FAILED,
          State.CANCELED, CommitBuildState.CANCELED);

  static final String CIRCLECI_CONFIG_PATH = ".circleci/config.yml";
  private static final String STRING_PARAMETER_YAML = "{type: string, default: ''}";
  private static final String GIT_CHECKOUT_COMMAND_YAML =
      "steps:\n"
          + "  - add_ssh_keys:\n"
          + "      fingerprints: [<< pipeline.parameters.gitlab_ssh_fingerprint >>]\n"
          + "  - run: |\n"
          + "      mkdir -p ~/.ssh\n"
          + "      ssh-keyscan gitlab.com >> ~/.ssh/known_hosts\n"
          + "      git clone --single-branch --branch $CIRCLE_BRANCH "
          + "        --depth=1 << pipeline.parameters.gitlab_git_uri >> project";

  private static final JsonNode STRING_PARAMETER_NODE;
  private static final JsonNode GIT_CHECKOUT_COMMAND_NODE;

  static {
    try {
      STRING_PARAMETER_NODE = YAML_MAPPER.readTree(STRING_PARAMETER_YAML);
      GIT_CHECKOUT_COMMAND_NODE = YAML_MAPPER.readTree(GIT_CHECKOUT_COMMAND_YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Calls GitLab to update the commit status of the given project with the status of the given
   * workflow
   *
   * @param pipeline the CircleCI pipeline
   * @return The state submitted to GitLab
   */
  public State updateCommitStatus(Pipeline pipeline, Workflow workflow) {
    String sha = pipeline.revision();
    State state = workflow.state();

    if (state == null || !WORKFLOW_TO_GITLAB_STATE_MAP.containsKey(state)) {
      LOGGER.error("Unknown workflow state {}", state);
      return null;
    }
    CommitBuildState buildState = WORKFLOW_TO_GITLAB_STATE_MAP.get(state);
    CommitStatus commitStatus = new CommitStatus();
    commitStatus.setName(workflow.name());
    commitStatus.setDescription("CircleCI Workflow");
    commitStatus.setStatus(state.name());
    String targetUrl = String.format("https://circleci.com/workflow-run/%s", workflow.id());
    commitStatus.setTargetUrl(targetUrl);
    try {
      LOGGER.info(
          "Setting the state of CircleCI workflow {} as {} in GitLab", workflow.id(), buildState);
      gitLabApi
          .getCommitsApi()
          .addCommitStatus(pipeline.projectId(), sha, buildState, commitStatus);
    } catch (GitLabApiException e) {
      LOGGER.error("Failed to update GitLab status", e);
      return null;
    }

    return state;
  }

  /**
   * Extends the given CircleCI config with a git-checkout command and gitlab_ssh_fingerprint and
   * gitlab_git_uri pipeline parameters
   *
   * @param root root node of the YAML config
   * @return The config extended with the git-checkout command and git-related parameters
   */
  @VisibleForTesting
  ObjectNode extendCircleCiConfig(ObjectNode root) {
    ObjectNode rootNode;
    if (root == null) {
      rootNode = YAML_MAPPER.createObjectNode();
    } else {
      rootNode = root;
    }

    ObjectNode parametersNode = rootNode.with("parameters");
    ObjectNode commandsNode = rootNode.with("commands");

    parametersNode.set("gitlab_ssh_fingerprint", STRING_PARAMETER_NODE);
    parametersNode.set("gitlab_git_uri", STRING_PARAMETER_NODE);
    commandsNode.set("git-checkout", GIT_CHECKOUT_COMMAND_NODE);

    return rootNode;
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

      return readYamlAsObject(config)
          .map(this::extendCircleCiConfig)
          .map(this::safeWriteValueAsString);
    } catch (GitLabApiException e) {
      LOGGER.warn("Error fetching CircleCI config", e);
    }
    return Optional.empty();
  }

  /**
   * Reads a YAML string as an {@link ObjectNode}. If parsing fails or the YAML does not represent
   * an object, returns {@code Optional.empty}.
   *
   * @param yaml the YAML string to parse
   * @return the parsed {@code ObjectNode}
   */
  private Optional<ObjectNode> readYamlAsObject(String yaml) {
    try {
      ObjectNode objectNode = (ObjectNode) YAML_MAPPER.readTree(yaml);
      return Optional.of(objectNode);
    } catch (ClassCastException | IOException e) {
      LOGGER.warn("Error parsing CircleCI config", e);
      return Optional.empty();
    }
  }

  /**
   * Writes an {@link ObjectNode} value as a YAML string.
   *
   * <p>Provides an unchecked alternative to {@link ObjectMapper#writeValueAsString}.
   */
  private String safeWriteValueAsString(ObjectNode objectNode) {
    try {
      return YAML_MAPPER.writer().writeValueAsString(objectNode);
    } catch (JsonProcessingException e) {
      LOGGER.error("Unexpected error writing CircleCI config to String", e);
      return null;
    }
  }
}
