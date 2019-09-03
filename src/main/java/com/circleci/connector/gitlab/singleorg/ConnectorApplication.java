package com.circleci.connector.gitlab.singleorg;

import com.circleci.connector.gitlab.singleorg.health.CircleCiApiHealthCheck;
import com.circleci.connector.gitlab.singleorg.resources.HookResource;
import com.codahale.metrics.MetricRegistry;
import com.readytalk.metrics.StatsDReporter;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import java.util.concurrent.TimeUnit;
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

  /**
   * Main entry point for the GitLab Single Org Connector. See Dropwizard documentation for details
   * of how any of this works.
   */
  public void run(ConnectorConfiguration config, Environment environment) {
    environment.healthChecks().register("CircleCI API", new CircleCiApiHealthCheck());
    environment.jersey().register(new HookResource(config.getGitlab().getSharedSecretForHooks()));

    maybeConfigureStatsdMetrics(config, environment.metrics());
  }

  public static void main(String[] args) throws Exception {
    new ConnectorApplication().run(args);
  }
}
