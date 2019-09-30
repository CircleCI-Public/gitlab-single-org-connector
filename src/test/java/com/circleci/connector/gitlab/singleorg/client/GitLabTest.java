package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryFileApi;
import org.gitlab4j.api.models.RepositoryFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GitLabTest {
  private static final String AUTH_TOKEN = "my-auth-token";
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
  void fetchCircleCiConfigSuccessfully() throws GitLabApiException {
    RepositoryFile mockRepositoryFile = Mockito.mock(RepositoryFile.class);

    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenReturn(mockRepositoryFile);
    Mockito.when(mockRepositoryFile.getDecodedContentAsString()).thenReturn(VALID_CONFIG_CONTENTS);

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertEquals(Optional.of(VALID_CONFIG_CONTENTS), result);
  }

  @Test
  void fetchCircleCiConfigError() throws GitLabApiException {
    Mockito.when(mockRepositoryFileApi.getFile(PROJECT_ID, GitLab.CIRCLECI_CONFIG_PATH, REF))
        .thenThrow(new GitLabApiException("Expected exception"));

    Optional<String> result = gitLab.fetchCircleCiConfig(PROJECT_ID, REF);
    assertTrue(result.isEmpty());
  }
}
