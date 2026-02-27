package io.f1r3fly.f1r3drive.filesystem.deployable;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.filesystem.common.AbstractPath;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;

public abstract class AbstractDeployablePath extends AbstractPath {

    public AbstractDeployablePath(BlockchainContext blockchainContext, String name, Directory parent) {
        super(blockchainContext, name, parent);
    }

    protected void enqueueMutation(String rholangExpression, byte[] signingKey) {
        RevWalletInfo walletInfo = getBlockchainContext().getWalletInfo();
        
        if (signingKey == null) {
            throw new IllegalStateException("Cannot sign blockchain mutation: private key missing for " + getAbsolutePath());
        }

        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
                rholangExpression,
                true,
                F1r3flyBlockchainClient.RHOLANG,
                walletInfo.revAddress(),
                signingKey,
                System.currentTimeMillis());

        getBlockchainContext().getDeployDispatcher().enqueueDeploy(deployment);
    }

    @Override
    public synchronized void delete() {
        byte[] key = getSigningKey();
        refreshLastUpdated();
        String rholangExpression = RholangExpressionConstructor.forgetChanel(getAbsolutePath());
        enqueueMutation(rholangExpression, key);
    }

    @Override
    public void rename(String newName, Directory newParent) throws OperationNotPermitted {
        byte[] key = getSigningKey();
        String oldPath = getAbsolutePath();
        super.rename(newName, newParent);
        String newPath = getAbsolutePath();
        enqueueMutation(RholangExpressionConstructor.renameChanel(oldPath, newPath, getLastUpdated() / 1000), key);
    }

}
