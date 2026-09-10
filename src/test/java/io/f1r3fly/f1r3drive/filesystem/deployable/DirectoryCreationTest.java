package io.f1r3fly.f1r3drive.filesystem.deployable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.EmptyExploratoryResult;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import org.junit.jupiter.api.Test;
import rhoapi.RhoTypes;

class DirectoryCreationTest {
  private final F1r3flyBlockchainClient client = mock(F1r3flyBlockchainClient.class);
  private final DeployDispatcher dispatcher = mock(DeployDispatcher.class);
  private final FetchedDirectory parent =
      new FetchedDirectory(
          new BlockchainContext(new RevWalletInfo("wallet", new byte[32]), dispatcher),
          "parent",
          null,
          1L);

  @Test
  void orphanedDirectoriesAreNotRecreatedOverTheirExistingData() {
    when(dispatcher.getBlockchainClient()).thenReturn(client);
    when(client.exploratoryDeploy(anyString())).thenReturn(RhoTypes.Expr.getDefaultInstance());
    assertThrows(F1r3DriveError.class, () -> parent.mkdir("child"));
    verify(dispatcher, never()).enqueueDeploy(any());
  }

  @Test
  void unavailableReadsCannotCreateDirectories() {
    when(dispatcher.getBlockchainClient()).thenReturn(client);
    when(client.exploratoryDeploy(anyString())).thenThrow(new F1r3DriveError("read unavailable"));
    assertThrows(F1r3DriveError.class, () -> parent.mkdir("child"));
    verify(dispatcher, never()).enqueueDeploy(any());
  }

  @Test
  void newDirectoryAndParentReferenceAreCreatedAfterConfirmedAbsence() {
    when(dispatcher.getBlockchainClient()).thenReturn(client);
    when(client.exploratoryDeploy(anyString())).thenThrow(new EmptyExploratoryResult());
    parent.mkdir("child");
    verify(dispatcher, times(2)).enqueueDeploy(any());
  }
}
