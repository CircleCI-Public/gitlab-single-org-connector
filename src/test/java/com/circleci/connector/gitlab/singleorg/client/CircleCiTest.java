package com.circleci.connector.gitlab.singleorg.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleci.client.v2.ApiException;
import com.circleci.client.v2.api.DefaultApi;
import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.TriggerPipelineParameters;
import com.circleci.connector.gitlab.singleorg.ConnectorConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.util.Optional;
import javax.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;

class CircleCiTest {

  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final DefaultApi CIRCLECI_HAPPY;
  private static final DefaultApi CIRCLECI_404;
  private static final DefaultApi CIRCLECI_404_JSON;
  private static final DefaultApi CIRCLECI_500;

  static {
    CIRCLECI_HAPPY = mock(DefaultApi.class);
    CIRCLECI_404 = mock(DefaultApi.class);
    CIRCLECI_404_JSON = mock(DefaultApi.class);
    CIRCLECI_500 = mock(DefaultApi.class);
    try {
      when(CIRCLECI_HAPPY.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenReturn(new PipelineLight());
      when(CIRCLECI_404.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "No such project"));
      when(CIRCLECI_404_JSON.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(404, "{\"message\":\"No such project\"}"));
      when(CIRCLECI_500.triggerPipeline(anyString(), any(TriggerPipelineParameters.class)))
          .thenThrow(new ApiException(500, "CircleCI is broken"));
    } catch (ApiException e) {
      // Ignore when mocking
    }
  }

  private static ConnectorConfiguration configFromString(String config) {
    try {
      return MAPPER.readValue(config, ConnectorConfiguration.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void ifCircleCiReturns4xxWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(Optional.of(""), "", "", "", "", ""));
  }

  @Test
  void ifCircleCiReturns4xxWithAJsonMessageWePassItOn() {
    CircleCi circleCi = new CircleCi(CIRCLECI_404_JSON);
    assertThrows(
        ClientErrorException.class,
        () -> circleCi.triggerPipeline(Optional.of(""), "", "", "", "", ""));
  }

  @Test
  void ifCircleCiReturns500WeThrowRuntimeException() {
    CircleCi circleCi = new CircleCi(CIRCLECI_500);
    assertThrows(
        RuntimeException.class,
        () -> circleCi.triggerPipeline(Optional.of(""), "", "", "", "", ""));
  }

  @Test
  void ifCircleCiReturnsPipelineSuccess() {
    CircleCi circleCi = new CircleCi(CIRCLECI_HAPPY);
    PipelineLight pipelineLight = circleCi.triggerPipeline(Optional.of(""), "", "", "", "", "");
    assertNotNull(pipelineLight);
  }
}
