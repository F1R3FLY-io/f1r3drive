package io.f1r3fly.f1r3drive.blockchain.client;

import io.f1r3fly.f1r3drive.background.state.StateChangeEvents;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployDispatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeployDispatcher.class);

  private static final int MAX_EXPRESSION_LENGTH_IN_LOG = 1000;
  private static final int MAX_RETRIES = 10;
  private static final int RETRY_INTERVAL_MS = 15000;

  private final F1r3flyBlockchainClient f1R3FlyBlockchainClient;
  private final AtomicInteger outstandingDeployments = new AtomicInteger();
  private final AtomicReference<Throwable> lastDeployError = new AtomicReference<>();
  private final Object completionMonitor = new Object();
  private Thread backgroundThread;

  private final BlockingQueue<Deployment> queue;

  public record Deployment(
      String rhoOrMettaExpression,
      String language,
      Set<String> affectedRevAddresses,
      byte[] signingKey,
      long timestamp) {}

  private final StateChangeEventsManager stateChangeEventsManager;

  private class BackgroundDeployer extends Thread {
    @Override
    public void run() {
      try {
        while (!isInterrupted()) {
          Deployment deployment = queue.take();
          if (!deployWithRetries(deployment)) {
            return;
          }
        }
      } catch (InterruptedException error) {
        interrupt();
      } catch (Throwable error) {
        recordDeploymentFailure(error);
        if (error instanceof Error fatalError) {
          throw fatalError;
        }
      }
    }

    private boolean deployWithRetries(Deployment deployment) {
      for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        try {
          f1R3FlyBlockchainClient.deploy(
              deployment.rhoOrMettaExpression,
              deployment.language,
              deployment.signingKey,
              deployment.timestamp);
          deployment.affectedRevAddresses.stream()
              .map(StateChangeEvents.WalletBalanceChanged::new)
              .forEach(stateChangeEventsManager::addEvent);
          outstandingDeployments.decrementAndGet();
          signalCompletionStateChanged();
          return true;
        } catch (RuntimeException error) {
          if (attempt == MAX_RETRIES) {
            recordDeploymentFailure(error);
            return false;
          }
          LOGGER.warn("Deployment failed; retrying ({}/{})", attempt + 1, MAX_RETRIES, error);
          try {
            Thread.sleep(RETRY_INTERVAL_MS);
          } catch (InterruptedException interrupted) {
            interrupt();
            recordDeploymentFailure(interrupted);
            return false;
          }
        }
      }
      throw new IllegalStateException("unreachable deployment retry state");
    }
  }

  public DeployDispatcher(
      F1r3flyBlockchainClient f1R3FlyBlockchainClient,
      StateChangeEventsManager stateChangeEventsManager) {
    this.f1R3FlyBlockchainClient = f1R3FlyBlockchainClient;
    this.stateChangeEventsManager = stateChangeEventsManager;
    queue = new LinkedBlockingQueue<>();
  }

  public void enqueueDeploy(Deployment deployment) {
    Throwable deploymentError = lastDeployError.get();
    if (deploymentError != null) {
      throw new IllegalStateException("Deployment worker has failed", deploymentError);
    }

    // Avoid allocating the abbreviated expression unless debug logging is enabled.
    if (LOGGER.isDebugEnabled()) {
      String smaller =
          deployment.rhoOrMettaExpression.length() > MAX_EXPRESSION_LENGTH_IN_LOG
              ? deployment.rhoOrMettaExpression.substring(0, MAX_EXPRESSION_LENGTH_IN_LOG) + "..."
              : deployment.rhoOrMettaExpression;

      LOGGER.debug("Enqueueing deployment: {}", smaller);
    }

    outstandingDeployments.incrementAndGet();
    queue.add(deployment);
  }

  // Consume queued deployments serially so their blockchain mutations remain ordered.
  public synchronized void startBackgroundDeploy() {
    if (backgroundThread != null) {
      return;
    }
    backgroundThread = new BackgroundDeployer();
    backgroundThread.setName("f1r3drive-deploy-dispatcher");
    backgroundThread.start();
  }

  public void waitOnEmptyQueue() {
    LOGGER.info(
        "Waiting for deployments to complete. Outstanding deployments: "
            + outstandingDeployments.get());
    synchronized (completionMonitor) {
      while (outstandingDeployments.get() > 0 && lastDeployError.get() == null) {
        LOGGER.debug(
            "Waiting for deployments to complete. Outstanding deployments: {}",
            outstandingDeployments.get());
        try {
          completionMonitor.wait();
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for deployments", error);
        }
      }
    }

    if (lastDeployError.get() != null) {
      throw new RuntimeException("Error during deployment", lastDeployError.get());
    }
  }

  private void signalCompletionStateChanged() {
    synchronized (completionMonitor) {
      completionMonitor.notifyAll();
    }
  }

  private void recordDeploymentFailure(Throwable error) {
    lastDeployError.compareAndSet(null, error);
    LOGGER.error("Deployment worker stopped before all deployments completed", error);
    signalCompletionStateChanged();
  }

  // hard stop
  public synchronized void destroy() {
    LOGGER.info("Destroying DeployDispatcher");
    queue.clear();
    if (backgroundThread != null) {
      backgroundThread.interrupt();
      try {
        backgroundThread.join();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
      } finally {
        backgroundThread = null;
      }
    }
  }

  public F1r3flyBlockchainClient getBlockchainClient() {
    return f1R3FlyBlockchainClient;
  }
}
