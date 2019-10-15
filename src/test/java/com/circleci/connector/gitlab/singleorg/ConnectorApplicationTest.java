package com.circleci.connector.gitlab.singleorg;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleci.connector.gitlab.singleorg.resources.HookResource;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorApplicationTest {
  private final Environment environment = mock(Environment.class);
  private final JerseyEnvironment jersey = mock(JerseyEnvironment.class);
  private final HealthCheckRegistry healthCheckRegistry = mock(HealthCheckRegistry.class);
  private final MetricRegistry metricRegistry = mock(MetricRegistry.class);
  private final ConnectorApplication application = new ConnectorApplication();
  private final ConnectorConfiguration config = new ConnectorConfiguration();

  @BeforeEach
  void setup() {
    when(environment.jersey()).thenReturn(jersey);
    when(environment.healthChecks()).thenReturn(healthCheckRegistry);
    when(environment.lifecycle()).thenReturn(new LifecycleEnvironment(metricRegistry));
  }

  @Test
  void buildsAHookResource() throws Exception {
    application.run(config, environment);

    verify(jersey).register(isA(HookResource.class));
  }

  @Test
  void canSetUpStatsDReporting() throws Exception {
    ObjectMapper mapper = Jackson.newObjectMapper();
    ConnectorConfiguration cfg =
        mapper.readValue(
            "{\"statsd\":{\"host\":\"localhost\",\"port\":123,\"refreshPeriodSeconds\":3}}",
            ConnectorConfiguration.class);
    application.maybeConfigureStatsdMetrics(cfg, metricRegistry);
  }

  @Test
  void initializeDoesNotThrow() {
    application.initialize(new Bootstrap<>(application));
  }
}
