package com.circleci.connector.gitlab.singleorg;

import com.circleci.client.v2.ApiClient;
import com.circleci.client.v2.Configuration;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.connector.gitlab.singleorg.client.GitLab;
import com.circleci.connector.gitlab.singleorg.health.CircleCiApiHealthCheck;
import com.circleci.connector.gitlab.singleorg.health.GitLabApiHealthCheck;
import com.circleci.connector.gitlab.singleorg.resources.HookResource;
import com.codahale.metrics.MetricRegistry;
import com.readytalk.metrics.StatsDReporter;
import io.dropwizard.Application;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.concurrent.TimeUnit;
import org.gitlab4j.api.GitLabApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the GitLab Single Org Connector. See Dropwizard documentation for details of
 * how any of this works.
 */
class ConnectorApplication extends Application<ConnectorConfiguration> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorApplication.class);

  /** Start a periodic statsd reporter if we have configuration for statsd. */
  void maybeConfigureStatsdMetrics(ConnectorConfiguration config, MetricRegistry registry) {
    ConnectorConfiguration.Statsd statsd = config.getStatsd();
    if (statsd.getHost() != null) {
      LOGGER.info(
          "Configuring statsd metrics, sending to {}:{} every {} seconds",
          statsd.getHost(),
          statsd.getPort(),
          statsd.getRefreshPeriodSeconds());
      StatsDReporter.forRegistry(registry)
          .build(statsd.getHost(), statsd.getPort())
          .start(statsd.getRefreshPeriodSeconds(), TimeUnit.SECONDS);
    }
  }

  /** Create and configure a CircleCI client, but don't execute any connections. */
  private DefaultApi circleCiClient(ConnectorConfiguration config) {
    ApiClient apiClient = Configuration.getDefaultApiClient();
    apiClient.setApiKey(config.getCircleCi().getApiToken());
    return new DefaultApi(apiClient);
  }

  /**
   * Wrap any configuration source provider such that it will do env var substitution. This is
   * hoisted out of {@link #initialize(Bootstrap)} in order to make it available for testing.
   */
  static ConfigurationSourceProvider wrapConfigSourceProvider(ConfigurationSourceProvider csp) {
    return new SubstitutingSourceProvider(csp, new EnvironmentVariableSubstitutor());
  }

  @Override
  public void initialize(Bootstrap<ConnectorConfiguration> bootstrap) {
    bootstrap.setConfigurationSourceProvider(
        wrapConfigSourceProvider(bootstrap.getConfigurationSourceProvider()));
  }

  /**
   * Main entry point for the GitLab Single Org Connector. See Dropwizard documentation for details
   * of how any of this works.
   */
  public void run(ConnectorConfiguration config, Environment environment) {
    DefaultApi circleCiApi = circleCiClient(config);
    GitLabApi gitLabApi =
        new GitLabApi(config.getGitlab().getHost(), config.getGitlab().getAuthToken());
    GitLab gitLab = new GitLab(gitLabApi);

    environment.healthChecks().register("CircleCI API", new CircleCiApiHealthCheck(circleCiApi));
    environment.healthChecks().register("GitLab API", new GitLabApiHealthCheck(gitLabApi));
    environment
        .jersey()
        .register(new HookResource(gitLab, config.getGitlab().getSharedSecretForHooks()));

    maybeConfigureStatsdMetrics(config, environment.metrics());
  }

  public static void main(String[] args) throws Exception {
    new ConnectorApplication().run(args);
  }
}
