package com.circleci.connector.gitlab.singleorg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.util.Resources;
import org.junit.jupiter.api.Test;

class ConnectorConfigurationTest {
  @Test
  void weDoNotNPEWhenSomePartsOfTheConfigAreMissing() {
    ConnectorConfiguration cfg = new ConnectorConfiguration();
    assertNotNull(cfg.getGitlab());
    assertNotNull(cfg.getStatsd());
  }

  @Test
  void weCanSetEverythingInAConfigFileAndItStillWorks() throws Exception {
    ConnectorConfiguration cfg = loadFromResources("complete-config.yml");
    assertEquals("super-secret", cfg.getGitlab().getSharedSecretForHooks());
  }

  @Test
  void theDefaultContainerConfigWorks() throws Exception {
    ConnectorConfiguration cfg = loadFromResources("default-container-config.yml");
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
