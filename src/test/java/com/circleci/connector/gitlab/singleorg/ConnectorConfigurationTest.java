package com.circleci.connector.gitlab.singleorg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.UndefinedEnvironmentVariableException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.util.Resources;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorConfigurationTest {
  @Test
  void weDoNotNPEWhenSomePartsOfTheConfigAreMissing() {
    ConnectorConfiguration cfg = new ConnectorConfiguration();
    assertNotNull(cfg.getCircleCi());
    assertNotNull(cfg.getGitlab());
    assertNotNull(cfg.getStatsd());
    assertNotNull(cfg.getDomainMapping());
    assertNotNull(cfg.getDomainMapping().getRepositories());
    assertNotNull(cfg.getDomainMapping().getSshFingerprints());
  }

  @Test
  void weCanSetEverythingInAConfigFileAndItStillWorks() throws Exception {
    ConnectorConfiguration cfg = loadFromResources("complete-config.yml");
    assertEquals("super-secret", cfg.getGitlab().getSharedSecretForHooks());
    assertEquals("not-really-a-token", cfg.getCircleCi().getApiToken());
    assertEquals(Map.of(123, "gh/ghorg/ghrepo"), cfg.getDomainMapping().getRepositories());
    assertEquals(
        Map.of(123, "aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa"),
        cfg.getDomainMapping().getSshFingerprints());
  }

  @Test
  void theDefaultContainerConfigIsMissingEnvVariables() throws Exception {
    assertThrows(
        UndefinedEnvironmentVariableException.class,
        () -> loadFromResources("default-container-config.yml"));
  }

  /**
   * Helper to parse YAML files from the test resources path and turn them into
   * ConnectorConfiguration objects for testing.
   */
  private ConnectorConfiguration loadFromResources(String pathToYaml) throws Exception {
    return (new YamlConfigurationFactory<>(
            ConnectorConfiguration.class,
            Validators.newValidator(),
            Jackson.newObjectMapper(),
            "dw"))
        .build(
            ConnectorApplication.wrapConfigSourceProvider(new FileConfigurationSourceProvider()),
            Resources.getResource(pathToYaml).getFile());
  }
}
