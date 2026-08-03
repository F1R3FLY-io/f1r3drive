package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.common.AbstractPath;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import java.util.Set;

public abstract class AbstractDeployablePath extends AbstractPath {

  public AbstractDeployablePath(
      BlockchainContext blockchainContext, String name, Directory parent) {
    super(blockchainContext, name, parent);
  }

  protected void enqueueMutation(String rholangExpression) {

    RevWalletInfo revWalletInfo = getBlockchainContext().getWalletInfo();
    DeployDispatcher.Deployment deployment =
        new DeployDispatcher.Deployment(
            rholangExpression,
            F1r3flyBlockchainClient.RHOLANG,
            Set.of(revWalletInfo.revAddress()),
            revWalletInfo.signingKey(),
            System.currentTimeMillis());

    getBlockchainContext().getDeployDispatcher().enqueueDeploy(deployment);
  }

  @Override
  public synchronized void delete() {
    refreshLastUpdated();
    String rholangExpression = RholangExpressionConstructor.forgetChanel(getAbsolutePath());
    enqueueMutation(rholangExpression);
  }

  @Override
  public void rename(String newName, Directory newParent) throws OperationNotPermitted {
    String oldPath = getAbsolutePath();
    super.rename(newName, newParent);
    String newPath = getAbsolutePath();
    enqueueMutation(RholangExpressionConstructor.renameChanel(oldPath, newPath, getLastUpdated()));
  }
}
