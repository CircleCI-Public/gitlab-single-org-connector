package com.circleci.connector.gitlab.singleorg.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Pipeline.State;
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineStatusPoller {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStatusPoller.class);

  /** The amount to delay before polling the CircleCI API for the first time. */
  private static final long INITIAL_DELAY_MILLIS = 1000;

  /** The pipeline we're polling for status. */
  private final Pipeline pipeline;

  /** The CircleCI client for calling the CircleCI API. */
  private final CircleCi circleCi;

  /** The GitLab client for calling the the GitLab API. */
  private final GitLab gitLab;

  /** State to manage the sleep and retry policy of this poller instance. */
  private final RetryPolicy retryPolicy;

  /** We get passed a reference to this in order to allow us to re-schedule a job to run later. */
  private final ScheduledExecutorService jobRunner;

  public PipelineStatusPoller(
      Pipeline pipeline, CircleCi circleCi, GitLab gitLab, ScheduledExecutorService jobRunner) {
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
    LOGGER.info("Polling for the status of CircleCI pipeline {}", pipeline.id());
    Pipeline p;
    try {
      p = circleCi.refreshPipeline(pipeline);
    } catch (RuntimeException e) {
      LOGGER.error(
          "Caught error while polling for the status of CircleCI pipeline {}", pipeline.id(), e);
      return retryPolicy.delayFor(null);
    }

    return retryPolicy.delayFor(gitLab.updateCommitStatus(p));
  }

  /**
   * Schedule the polling on the jobRunner.
   *
   * @param delayMillis The number of milliseconds to delay before running the job once.
   */
  private void schedule(long delayMillis) {
    LOGGER.info(
        "Scheduling a poll of CircleCI pipeline {} in {}ms from now", pipeline.id(), delayMillis);
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

    @Nullable private State lastState;

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
    long delayFor(State gitLabState) {
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
