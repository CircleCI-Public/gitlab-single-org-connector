package com.circleci.connector.gitlab.singleorg.model;

import java.util.UUID;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
public abstract class Workflow {
  public enum State {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
  }

  public abstract UUID id();

  public abstract String name();

  public abstract State state();
}
