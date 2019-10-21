package com.circleci.connector.gitlab.singleorg.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.circleci.connector.gitlab.singleorg.model.Workflow.State;
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowPoller {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowPoller.class);

  private static final long INITIAL_DELAY_MILLIS = 1000;

  private final Pipeline pipeline;
  private final Workflow workflow;
  private final CircleCi circleCi;
  private final GitLab gitLab;
  private final ScheduledExecutorService jobRunner;
  private final RetryPolicy retryPolicy;

  public WorkflowPoller(
      Pipeline pipeline,
      Workflow workflow,
      CircleCi circleCi,
      GitLab gitLab,
      ScheduledExecutorService jobRunner) {
    this.pipeline = pipeline;
    this.workflow = workflow;
    this.circleCi = circleCi;
    this.gitLab = gitLab;
    this.jobRunner = jobRunner;
    retryPolicy = new RetryPolicy();
  }

  public void start() {
    schedule(INITIAL_DELAY_MILLIS);
  }

  /**
   * Schedule the polling on the jobRunner.
   *
   * @param delayMillis The number of milliseconds to delay before running the job once.
   */
  private void schedule(long delayMillis) {
    LOGGER.info(
        "Scheduling a poll of CircleCI workflow {} in {}ms from now", workflow.id(), delayMillis);
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

  /**
   * Poll the CircleCI API and update GitLab with the status of the workflow
   *
   * @return The number of milliseconds to delay before polling again, or a negative number if we
   *     wish to stop polling.
   */
  @VisibleForTesting
  long poll() {
    LOGGER.info("Polling for the status of CircleCI workflow {}", workflow.id());
    Workflow workflow;
    try {
      workflow = circleCi.refreshWorkflow(this.workflow);
    } catch (RuntimeException e) {
      LOGGER.error(
          "Caught error while polling for the status of CircleCI workflow {}",
          this.workflow.id(),
          e);
      return retryPolicy.delayFor(null);
    }

    return retryPolicy.delayFor(gitLab.updateCommitStatus(pipeline, this.workflow));
  }

  /** Parcel up the retry delays and policy in a single, testable place. */
  static class RetryPolicy {

    private static final long MAX_CONSECUTIVE_ERRORS = 10;
    private static final long MAX_SLEEP_INTERVAL_MS = 10000;
    private static final long INITIAL_SLEEP_MS = 100;
    private long consecutiveErrors;
    @Nullable private State lastState;
    private long lastDelay;

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
     * @param workflowState The state we set on GitLab. Null if there was an error.
     * @return The number of milliseconds we should sleep for or a negative number if we should stop
     *     polling.
     */
    long delayFor(State workflowState) {
      // If there was an error we want to retry
      if (workflowState == null) {
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
          return -1; // Give up due to too many errors
        }
        return sleepLonger();
      }

      // If we get this far, there was no error so reset the consecutive error count.
      consecutiveErrors = 0;

      switch (workflowState) {
        case CANCELED:
        case FAILED:
        case SUCCESS:
          return -1; // Terminal state, stop polling
        case PENDING:
        case RUNNING:
          if (workflowState.equals(lastState)) {
            return sleepLonger();
          }
          lastState = workflowState;
          lastDelay = 1000;
          return lastDelay;
        default:
          throw new IllegalArgumentException(
              String.format("Unknown GitLab state: %s", workflowState));
      }
    }
  }
}
