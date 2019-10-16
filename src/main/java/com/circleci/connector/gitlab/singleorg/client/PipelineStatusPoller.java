package com.circleci.connector.gitlab.singleorg.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.circleci.client.v2.model.PipelineLight;
import com.circleci.client.v2.model.PipelineWithWorkflows;
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import org.gitlab4j.api.Constants.CommitBuildState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineStatusPoller {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStatusPoller.class);

  /** The amount to delay before polling the CircleCI API for the first time. */
  private static final long INITIAL_DELAY_MILLIS = 1000;

  /** The GitLab ID for the GitLab project we're updating. */
  private final int projectId;

  /** The pipeline we're polling for status. */
  private final PipelineLight pipeline;

  /** The CircleCI client for calling the CircleCI API. */
  private final CircleCi circleCi;

  /** The GitLab client for calling the the GitLab API. */
  private final GitLab gitLab;

  /** State to manage the sleep and retry policy of this poller instance. */
  private final RetryPolicy retryPolicy;

  /** We get passed a reference to this in order to allow us to re-schedule a job to run later. */
  private final ScheduledExecutorService jobRunner;

  public PipelineStatusPoller(
      int projectId,
      PipelineLight pipeline,
      CircleCi circleCi,
      GitLab gitLab,
      ScheduledExecutorService jobRunner) {
    this.projectId = projectId;
    this.pipeline = pipeline;
    this.circleCi = circleCi;
    this.gitLab = gitLab;
    this.jobRunner = jobRunner;
    retryPolicy = new RetryPolicy();
  }

  /** Start polling the CircleCI API and continue polling until we get to a terminal state. */
  public void start() {
    schedule(INITIAL_DELAY_MILLIS);
  }

  /**
   * Poll the CircleCI API and update GitLab with the status.
   *
   * @return The number of milliseconds to delay before polling again, or a negative number if we
   *     wish to stop polling.
   */
  @VisibleForTesting
  long poll() {
    LOGGER.info("Polling for the status of CircleCI pipeline {}", pipeline.getId());
    PipelineWithWorkflows p;
    try {
      p = circleCi.getPipelineById(pipeline.getId());
    } catch (RuntimeException e) {
      LOGGER.error(
          "Caught error while polling for the status of CircleCI pipeline {}", pipeline.getId(), e);
      return retryPolicy.delayFor(null);
    }

    return retryPolicy.delayFor(gitLab.updateCommitStatus(projectId, p));
  }

  /**
   * Schedule the polling on the jobRunner.
   *
   * @param delayMillis The number of milliseconds to delay before running the job once.
   */
  private void schedule(long delayMillis) {
    LOGGER.info(
        "Scheduling a poll of CircleCI pipeline {} in {}ms from now",
        pipeline.getId(),
        delayMillis);
    jobRunner.schedule(
        () -> {
          long rescheduleAfter = poll();
          if (rescheduleAfter >= 0) {
            schedule(rescheduleAfter);
          }
        },
        delayMillis,
        MILLISECONDS);
  }

  /** Parcel up the retry delays and policy in a single, testable place. */
  static class RetryPolicy {
    private long consecutiveErrors;

    @Nullable private CommitBuildState lastState;

    private long lastDelay;

    private static final long MAX_CONSECUTIVE_ERRORS = 10;

    private static final long MAX_SLEEP_INTERVAL_MS = 10000;

    private static final long INITIAL_SLEEP_MS = 100;

    RetryPolicy() {
      consecutiveErrors = 0;
      lastState = null;
      lastDelay = INITIAL_SLEEP_MS;
    }

    /**
     * Sleep longer than the previous sleep. Back off exponentially, but cap at
     * MAX_SLEEP_INTERVAL_MS.
     *
     * @return The number of milliseconds to sleep before the next poll.
     */
    long sleepLonger() {
      lastDelay *= 2;
      if (lastDelay > MAX_SLEEP_INTERVAL_MS) {
        lastDelay = MAX_SLEEP_INTERVAL_MS;
      }
      return lastDelay;
    }

    /**
     * Compute the delay until the next poll.
     *
     * @param gitLabState The state we set on GitLab. Null if there was an error.
     * @return The number of milliseconds we should sleep for or a negative number if we should stop
     *     polling.
     */
    long delayFor(CommitBuildState gitLabState) {
      // If there was an error we want to retry
      if (gitLabState == null) {
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
          return -1; // Give up due to too many errors
        }
        return sleepLonger();
      }

      // If we get this far, there was no error so reset the consecutive error count.
      consecutiveErrors = 0;

      switch (gitLabState) {
        case CANCELED:
        case FAILED:
        case SUCCESS:
          return -1; // Terminal state, stop polling
        case PENDING:
        case RUNNING:
          if (gitLabState.equals(lastState)) {
            return sleepLonger();
          }
          lastState = gitLabState;
          lastDelay = 1000;
          return lastDelay;
        default:
          throw new IllegalArgumentException(
              String.format("Unknown GitLab state: %s", gitLabState));
      }
    }
  }
}
