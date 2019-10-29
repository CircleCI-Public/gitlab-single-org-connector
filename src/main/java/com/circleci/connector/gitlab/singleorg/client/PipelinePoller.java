package com.circleci.connector.gitlab.singleorg.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.circleci.connector.gitlab.singleorg.model.Pipeline;
import com.circleci.connector.gitlab.singleorg.model.Workflow;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelinePoller {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelinePoller.class);

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

  public HashMap<UUID, WorkflowPoller> getWorkflowPollers() {
    return workflowPollers;
  }

  /** WorkflowPollers by workflow ID */
  private HashMap<UUID, WorkflowPoller> workflowPollers;

  /** We get passed a reference to this in order to allow us to re-schedule a job to run later. */
  private final ScheduledExecutorService jobRunner;

  public PipelinePoller(
      Pipeline pipeline, CircleCi circleCi, GitLab gitLab, ScheduledExecutorService jobRunner) {
    this.pipeline = pipeline;
    this.circleCi = circleCi;
    this.gitLab = gitLab;
    this.jobRunner = jobRunner;
    retryPolicy = new RetryPolicy();
    workflowPollers = new HashMap<>();
  }

  /** Start polling the CircleCI API and continue polling until we get to a terminal state. */
  public void start() {
    schedule(INITIAL_DELAY_MILLIS);
  }

  /**
   * Poll the CircleCI API and create WorkflowPollers for any new workflows
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

    for (Workflow workflow : p.workflows()) {
      if (!workflowPollers.containsKey(workflow.id())) {
        WorkflowPoller workflowPoller =
            new WorkflowPoller(pipeline, workflow, circleCi, gitLab, jobRunner);
        workflowPoller.start();
        workflowPollers.put(workflow.id(), workflowPoller);
      }
    }

    return retryPolicy.delayFor(p.workflows());
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

  static class RetryPolicy {
    private static final int MAX_CONSECUTIVE_UNCHANGED_WORKFLOWS = 5;
    private static final int POLLING_INTERVAL_MS = 1000;

    private static final int MAX_CONSECTIVE_ERRORS_OR_MISSING_WORKFLOWS = 10;
    private static final int MAX_SLEEP_INTERVAL_MS = 10000;

    Set<UUID> lastWorkflowIds = new HashSet<>();
    private int consecutiveErrors = 0;
    private int lastDelay = 100;
    private int delaysSinceWorkflowsUpdated = 0;

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

    public long delayFor(Set<Workflow> workflows) {
      // For each polling error (expressed as null)
      // or result with empty workflows we back-off and eventually give up

      if (workflows == null || workflows.isEmpty()) {
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECTIVE_ERRORS_OR_MISSING_WORKFLOWS) {
          return -1; // Give up due to too many errors
        }
        return sleepLonger();
      }

      // If we get this far, there was no error so reset the consecutive error count.
      consecutiveErrors = 0;

      // If there were no errors we compare the set of workflow ids we got to the one we got
      // last time. If we reach MAX_CONSECUTIVE_UNCHANGED_WORKFLOWS iterations of unchanged
      // workflow ids we stop the polling by returning -1

      Set<UUID> workflowIds = new HashSet<>();
      for (Workflow workflow : workflows) {
        workflowIds.add(workflow.id());
      }

      delaysSinceWorkflowsUpdated++;

      if (workflowIds.equals(lastWorkflowIds)
          && delaysSinceWorkflowsUpdated > MAX_CONSECUTIVE_UNCHANGED_WORKFLOWS) {
        return -1;
      }

      if (!workflowIds.equals(lastWorkflowIds)) {
        lastWorkflowIds = workflowIds;
        delaysSinceWorkflowsUpdated = 0;
      }

      lastDelay = POLLING_INTERVAL_MS;
      return lastDelay;
    }
  }
}
