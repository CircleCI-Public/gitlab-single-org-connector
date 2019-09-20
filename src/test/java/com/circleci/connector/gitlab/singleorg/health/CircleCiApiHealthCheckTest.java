package com.circleci.connector.gitlab.singleorg.health;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.User;
import org.junit.jupiter.api.Test;

class CircleCiApiHealthCheckTest {
  @Test
  void healthCheckPassesWhenWeHaveAPIAccess() throws Exception {
    DefaultApi circleCiApi = mock(DefaultApi.class);
    when(circleCiApi.getCurrentUser()).thenReturn(new User());
    CircleCiApiHealthCheck healthCheck = new CircleCiApiHealthCheck(circleCiApi);
    assertTrue(healthCheck.check().isHealthy());
  }

  @Test
  void healthCheckFailsWhenTheAPIBehavesUnexpectedly() throws Exception {
    DefaultApi circleCiApi = mock(DefaultApi.class);
    when(circleCiApi.getCurrentUser()).thenReturn(null);
    CircleCiApiHealthCheck healthCheck = new CircleCiApiHealthCheck(circleCiApi);
    assertFalse(healthCheck.check().isHealthy());
  }

  @Test
  void healthCheckFailsWhenTheAPIThrows() throws Exception {
    DefaultApi circleCiApi = mock(DefaultApi.class);
    when(circleCiApi.getCurrentUser()).thenThrow(new ApiException("Fail"));
    CircleCiApiHealthCheck healthCheck = new CircleCiApiHealthCheck(circleCiApi);
    assertFalse(healthCheck.check().isHealthy());
  }
}
