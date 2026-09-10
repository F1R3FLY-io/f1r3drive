package io.f1r3fly.f1r3drive.filesystem.local;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.EmptyExploratoryResult;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import org.junit.jupiter.api.Test;

class LockedWalletDirectoryTest {
  private final F1r3flyBlockchainClient client = mock(F1r3flyBlockchainClient.class);
  private final LockedWalletDirectory directory =
      new LockedWalletDirectory(
          new BlockchainContext(new RevWalletInfo("wallet", null), null), null);

  @Test
  void readFailuresCannotBeMistakenForAnAbsentFilesystem() {
    var failure = new F1r3DriveError("Exploratory deploy can only be executed on read-only node");
    when(client.exploratoryDeploy(anyString())).thenThrow(failure);
    assertSame(
        failure,
        assertThrows(
            F1r3DriveError.class,
            () -> directory.fetchDirectoryFromShard(client, "/wallet", "wallet", null)));
  }

  @Test
  void successfulEmptyReadsStillAllowNewFilesystemInitialization() {
    when(client.exploratoryDeploy(anyString())).thenThrow(new EmptyExploratoryResult());
    assertThrows(
        NoDataByPath.class,
        () -> directory.fetchDirectoryFromShard(client, "/wallet", "wallet", null));
  }
}
