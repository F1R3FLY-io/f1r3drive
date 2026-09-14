package io.f1r3fly.f1r3drive.blockchain.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import casper.CasperMessage;
import io.f1r3fly.f1r3drive.background.state.StateChangeEvents;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeployDispatcherTest {

  @Test
  void durabilityWaitDoesNotReturnWhileDequeuedDeploymentIsInFlight() throws Exception {
    F1r3flyBlockchainClient client = mock(F1r3flyBlockchainClient.class);
    StateChangeEventsManager events = mock(StateChangeEventsManager.class);
    var prepared =
        new F1r3flyBlockchainClient.PreparedDeployment(
            "@0!(0)", CasperMessage.DeployDataProto.getDefaultInstance());
    when(client.prepareDeployment(any(), any(), any(), anyLong())).thenReturn(prepared);
    CountDownLatch deployStarted = new CountDownLatch(1);
    CountDownLatch allowDeployToFinish = new CountDownLatch(1);

    doAnswer(
            ignored -> {
              deployStarted.countDown();
              if (!allowDeployToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting to finish deployment");
              }
              return null;
            })
        .when(client)
        .deploy(prepared, false);

    DeployDispatcher dispatcher = new DeployDispatcher(client, events);
    dispatcher.enqueueDeploy(
        new DeployDispatcher.Deployment(
            "@0!(0)", "rholang", Set.of("sender-rev", "receiver-rev"), new byte[32], 1L));
    dispatcher.startBackgroundDeploy();

    try {
      if (!deployStarted.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("test timed out waiting for deployment to start");
      }

      CompletableFuture<Void> durabilityWait =
          CompletableFuture.runAsync(dispatcher::waitOnEmptyQueue);
      Thread.sleep(Duration.ofMillis(100).toMillis());
      assertFalse(durabilityWait.isDone());

      allowDeployToFinish.countDown();
      durabilityWait.get(5, TimeUnit.SECONDS);
      verify(events).addEvent(new StateChangeEvents.WalletBalanceChanged("sender-rev"));
      verify(events).addEvent(new StateChangeEvents.WalletBalanceChanged("receiver-rev"));
    } finally {
      allowDeployToFinish.countDown();
      dispatcher.destroy();
    }
  }

  @Test
  void retryKeepsTheSameSignedDeploymentInsteadOfCreatingAnotherMutation() throws Exception {
    F1r3flyBlockchainClient client = mock(F1r3flyBlockchainClient.class);
    StateChangeEventsManager events = mock(StateChangeEventsManager.class);
    var prepared =
        new F1r3flyBlockchainClient.PreparedDeployment(
            "@0!(0)", CasperMessage.DeployDataProto.getDefaultInstance());
    when(client.prepareDeployment(any(), any(), any(), anyLong())).thenReturn(prepared);
    doThrow(new IllegalStateException("connection closed after acceptance"))
        .doNothing()
        .when(client)
        .deploy(prepared, false);
    DeployDispatcher dispatcher = new DeployDispatcher(client, events);
    dispatcher.enqueueDeploy(
        new DeployDispatcher.Deployment(
            "@0!(0)", "rholang", Set.of("sender-rev"), new byte[32], 1L));
    dispatcher.startBackgroundDeploy();
    try {
      CompletableFuture.runAsync(dispatcher::waitOnEmptyQueue).get(20, TimeUnit.SECONDS);
      verify(client, times(1)).prepareDeployment(any(), any(), any(), anyLong());
      verify(client, times(1)).deploy(prepared, false);
      verify(client, times(1)).deploy(prepared, true);
      verify(events, times(1)).addEvent(new StateChangeEvents.WalletBalanceChanged("sender-rev"));
    } finally {
      dispatcher.destroy();
    }
  }
}
