package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.circleci.connector.gitlab.singleorg.model.ImmutablePipeline;
import com.circleci.connector.gitlab.singleorg.model.ImmutableWorkflow;
import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.util.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.UUID;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.Constants.CommitBuildState;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryFileApi;
import org.gitlab4j.api.models.CommitStatus;
import org.gitlab4j.api.models.RepositoryFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class GitLabTest {
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final int PROJECT_ID = 22;
  private static final String REF = "1234";

  private GitLabApi mockGitLabApi = Mockito.mock(GitLabApi.class);
  private RepositoryFileApi mockRepositoryFileApi = Mockito.mock(RepositoryFileApi.class);
  private CommitsApi mockCommitsApi = Mockito.mock(CommitsApi.class);
  private GitLab gitLab = new GitLab(mockGitLabApi);

  private static Pipeline loadYamlAsPipeline(String filename) throws IOException {
    URL resource = Resources.getResource(String.format("model/pipeline/%s", filename));
    return YAML_MAPPER.readValue(resource, Pipeline.class);
  }

  private static CommitStatus loadYamlAsCommitStatus(String filename) throws IOException {
    URL resource = Resources.getResource(String.format("gitlab-api/commit-status/%s", filename));
    return YAML_MAPPER.readValue(resource, CommitStatus.class);
  }

  private static String readCircleCIConfigAsString(String filename) throws IOException {
    URL resource = Resources.getResource(String.format("circleci-config/%s", filename));
    return Resources.toString(resource, Charset.defaultCharset());
  }

  private static ObjectNode readCircleCIConfigAsObjectNode(String filename) throws IOException {
    return readYamlAsObject(readCircleCIConfigAsString(filename));
  }

  private static ObjectNode readYamlAsObject(String yaml) throws IOException {
    return (ObjectNode) YAML_MAPPER.readTree(yaml);
  }

  @BeforeEach
  void setUp() {
    Mockito.when(mockGitLabApi.getRepositoryFileApi()).thenReturn(mockRepositoryFileApi);
    Mockito.when(mockGitLabApi.getCommitsApi()).thenReturn(mockCommitsApi);
  }

  @Test
  void updateRunningCommitStatus() throws GitLabApiException, IOException {
    Pipeline pipeline =
        ImmutablePipeline.of(
            UUID.fromString("1a57adba-2a36-40fd-8396-82f5bd17168e"), PROJECT_ID, "6789", "master");
    Workflow workflow =
        ImmutableWorkflow.of(
            UUID.fromString("56283657-2b1c-4135-a6b2-acd65311bf3d"), "my-workflow", State.RUNNING);
    CommitStatus commitStatus = loadYamlAsCommitStatus("running.yaml");

    gitLab.updateCommitStatus(pipeline, workflow);
    Mockito.verify(mockCommitsApi, Mockito.times(1))
        .addCommitStatus(
            ArgumentMatchers.eq(PROJECT_ID),
            ArgumentMatchers.eq("6789"),
            ArgumentMatchers.eq(CommitBuildState.RUNNING),
            ArgumentMatchers.refEq(commitStatus));
  }

  @Test
  void extendEmptyCircleCiConfig() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("empty.output.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(readYamlAsObject(""));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithoutParametersOrCommands() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-simple.output.yaml");
    String config = readCircleCIConfigAsString("valid-simple.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(readYamlAsObject(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithParameters() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-parameters.output.yaml");
    String config = readCircleCIConfigAsString("valid-parameters.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(readYamlAsObject(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithCommand() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-commands.output.yaml");
    String config = readCircleCIConfigAsString("valid-commands.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(readYamlAsObject(config));
    assertEquals(expected, result);
  }

  @Test
  void fetchCircleCiConfigSuccessfully() throws GitLabApiException, IOException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString())
        .thenReturn(readCircleCIConfigAsString("valid-simple.input.yaml"));

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
  void fetchInvalidYamlError() throws GitLabApiException, IOException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString())
        .thenReturn(readCircleCIConfigAsString("bad-yaml.input.yaml"));

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertTrue(result.isEmpty());
  }

  @Test
  void fetchNonObjectYamlError() throws GitLabApiException, IOException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString())
        .thenReturn(readCircleCIConfigAsString("non-object.input.yaml"));

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertTrue(result.isEmpty());
  }
}
