package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.Optional;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryFileApi;
import org.gitlab4j.api.models.RepositoryFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GitLabTest {
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final int PROJECT_ID = 22;
  private static final String REF = "1234";
  private static final String VALID_CONFIG_CONTENTS =
      "version: 2.1\n"
          + "jobs:\n"
          + "  build:\n"
          + "    docker: \n"
          + "      - image: circleci/node:4.8.2 # the primary container, where your job's commands are run\n"
          + "    steps:\n"
          + "      - checkout # check out the code in the project directory\n"
          + "      - run: echo \"hello world\" # run the `echo` command";

  private GitLabApi mockGitLabApi = Mockito.mock(GitLabApi.class);
  private RepositoryFileApi mockRepositoryFileApi = Mockito.mock(RepositoryFileApi.class);
  private GitLab gitLab = new GitLab(mockGitLabApi);

  @BeforeEach
  void setUp() {
    Mockito.when(mockGitLabApi.getRepositoryFileApi()).thenReturn(mockRepositoryFileApi);
  }

  @Test
  void extendEmptyCircleCiConfig() throws IOException {
    ObjectNode expected =
        (ObjectNode)
            YAML_MAPPER.readTree(
                "parameters:\n"
                    + "  gitlab_git_uri:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "  gitlab_ssh_fingerprint:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "commands:\n"
                    + "  git-checkout:\n"
                    + "    steps:\n"
                    + "    - add_ssh_keys:\n"
                    + "        fingerprints:\n"
                    + "        - \"<< pipeline.parameters.gitlab_ssh_fingerprint >>\"\n"
                    + "    - run: \"mkdir -p ~/.ssh\\nssh-keyscan gitlab.com >> ~/.ssh/known_hosts\\ngit clone\\\n"
                    + "        \\ --depth=1 << pipeline.parameters.gitlab_git_uri >> project\"\n");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(""));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithoutParametersOrCommands() throws IOException {
    ObjectNode expected =
        (ObjectNode)
            YAML_MAPPER.readTree(
                "version: 2.1\n"
                    + "jobs:\n"
                    + "  build:\n"
                    + "    docker:\n"
                    + "      - image: circleci/node:4.8.2\n"
                    + "    steps:\n"
                    + "      - git-checkout\n"
                    + "      - run: echo hello\n"
                    + "parameters:\n"
                    + "  gitlab_git_uri:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "  gitlab_ssh_fingerprint:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "commands:\n"
                    + "  git-checkout:\n"
                    + "    steps:\n"
                    + "    - add_ssh_keys:\n"
                    + "        fingerprints:\n"
                    + "        - \"<< pipeline.parameters.gitlab_ssh_fingerprint >>\"\n"
                    + "    - run: \"mkdir -p ~/.ssh\\nssh-keyscan gitlab.com >> ~/.ssh/known_hosts\\ngit clone\\\n"
                    + "        \\ --depth=1 << pipeline.parameters.gitlab_git_uri >> project\"\n");
    String config =
        "version: 2.1\n"
            + "jobs:\n"
            + "  build:\n"
            + "    docker:\n"
            + "      - image: circleci/node:4.8.2\n"
            + "    steps:\n"
            + "      - git-checkout\n"
            + "      - run: echo hello";
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithParameters() throws IOException {
    ObjectNode expected =
        (ObjectNode)
            YAML_MAPPER.readTree(
                "version: 2.1\n"
                    + "jobs:\n"
                    + "  build:\n"
                    + "    docker:\n"
                    + "      - image: circleci/node:4.8.2\n"
                    + "    steps:\n"
                    + "      - git-checkout\n"
                    + "      - run: echo hello\n"
                    + "parameters:\n"
                    + "  foo:\n"
                    + "    type: boolean\n"
                    + "    default: false\n"
                    + "  gitlab_git_uri:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "  gitlab_ssh_fingerprint:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "commands:\n"
                    + "  git-checkout:\n"
                    + "    steps:\n"
                    + "    - add_ssh_keys:\n"
                    + "        fingerprints:\n"
                    + "        - \"<< pipeline.parameters.gitlab_ssh_fingerprint >>\"\n"
                    + "    - run: \"mkdir -p ~/.ssh\\nssh-keyscan gitlab.com >> ~/.ssh/known_hosts\\ngit clone\\\n"
                    + "        \\ --depth=1 << pipeline.parameters.gitlab_git_uri >> project\"\n");
    String config =
        "version: 2.1\n"
            + "jobs:\n"
            + "  build:\n"
            + "    docker:\n"
            + "      - image: circleci/node:4.8.2\n"
            + "    steps:\n"
            + "      - git-checkout\n"
            + "      - run: echo hello\n"
            + "parameters:\n"
            + "  foo:\n"
            + "    type: boolean\n"
            + "    default: false";
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithCommand() throws IOException {
    ObjectNode expected =
        (ObjectNode)
            YAML_MAPPER.readTree(
                "version: 2.1\n"
                    + "jobs:\n"
                    + "  build:\n"
                    + "    docker:\n"
                    + "      - image: circleci/node:4.8.2\n"
                    + "    steps:\n"
                    + "      - git-checkout\n"
                    + "      - run: echo hello\n"
                    + "parameters:\n"
                    + "  gitlab_git_uri:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "  gitlab_ssh_fingerprint:\n"
                    + "    type: \"string\"\n"
                    + "    default: \"\"\n"
                    + "commands:\n"
                    + "  sayhi:\n"
                    + "    steps:\n"
                    + "    - run: echo hi\n"
                    + "  git-checkout:\n"
                    + "    steps:\n"
                    + "    - add_ssh_keys:\n"
                    + "        fingerprints:\n"
                    + "        - \"<< pipeline.parameters.gitlab_ssh_fingerprint >>\"\n"
                    + "    - run: \"mkdir -p ~/.ssh\\nssh-keyscan gitlab.com >> ~/.ssh/known_hosts\\ngit clone\\\n"
                    + "        \\ --depth=1 << pipeline.parameters.gitlab_git_uri >> project\"\n");
    String config =
        "version: 2.1\n"
            + "jobs:\n"
            + "  build:\n"
            + "    docker:\n"
            + "      - image: circleci/node:4.8.2\n"
            + "    steps:\n"
            + "      - git-checkout\n"
            + "      - run: echo hello\n"
            + "commands:\n"
            + "  sayhi:\n"
            + "    steps:\n"
            + "    - run: echo hi\n";
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendInvalidCircleCiConfigFails() throws IOException {
    String config = "not valid config";
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertNull(result);
  }

  @Test
  void fetchCircleCiConfigSuccessfully() throws GitLabApiException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString()).thenReturn(VALID_CONFIG_CONTENTS);

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertFalse(result.isEmpty());
  }

  @Test
  void fetchCircleCiConfigError() throws GitLabApiException {
    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenThrow(new GitLabApiException("Expected exception"));

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertTrue(result.isEmpty());
  }

  @Test
  void fetchInvalidYamlError() throws GitLabApiException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString()).thenReturn("{{{");

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertTrue(result.isEmpty());
  }
}
