package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.util.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
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

  private GitLabApi mockGitLabApi = Mockito.mock(GitLabApi.class);
  private RepositoryFileApi mockRepositoryFileApi = Mockito.mock(RepositoryFileApi.class);
  private GitLab gitLab = new GitLab(mockGitLabApi);

  private static String readCircleCIConfigAsString(String filename) throws IOException {
    URL resource = Resources.getResource(String.format("circleci-config/%s", filename));
    return Resources.toString(resource, Charset.defaultCharset());
  }

  private static ObjectNode readCircleCIConfigAsObjectNode(String filename) throws IOException {
    return (ObjectNode) YAML_MAPPER.readTree(readCircleCIConfigAsString(filename));
  }

  @BeforeEach
  void setUp() {
    Mockito.when(mockGitLabApi.getRepositoryFileApi()).thenReturn(mockRepositoryFileApi);
  }

  @Test
  void extendEmptyCircleCiConfig() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("empty.output.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(""));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithoutParametersOrCommands() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-simple.output.yaml");
    String config = readCircleCIConfigAsString("valid-simple.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithParameters() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-parameters.output.yaml");
    String config = readCircleCIConfigAsString("valid-parameters.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendValidCircleCiConfigWithCommand() throws IOException {
    ObjectNode expected = readCircleCIConfigAsObjectNode("valid-commands.output.yaml");
    String config = readCircleCIConfigAsString("valid-commands.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertEquals(expected, result);
  }

  @Test
  void extendInvalidCircleCiConfigFails() throws IOException {
    String config = readCircleCIConfigAsString("invalid.input.yaml");
    ObjectNode result = gitLab.extendCircleCiConfig(YAML_MAPPER.readTree(config));
    assertNull(result);
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
}
