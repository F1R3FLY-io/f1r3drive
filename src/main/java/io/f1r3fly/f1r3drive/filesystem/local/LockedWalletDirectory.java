package io.f1r3fly.f1r3drive.filesystem.local;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.wallet.PrivateKeyValidator;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.InvalidSigningKeyException;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.common.ReadOnlyDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedFile;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LockedWalletDirectory extends AbstractLocalPath implements ReadOnlyDirectory {

    private static Logger logger = LoggerFactory.getLogger(LockedWalletDirectory.class);

    public LockedWalletDirectory(BlockchainContext blockchainContext, RootDirectory parent) {
        super(blockchainContext, "LOCKED-REMOTE-REV-" + blockchainContext.getWalletInfo().revAddress(), parent);
        this.lastUpdated = 0L;
    }

    @Override
    public Set<Path> getChildren() {
        return Set.of();
    }

    public UnlockedWalletDirectory unlock(String signingKeyRaw, DeployDispatcher deployDispatcher)
            throws InvalidSigningKeyException {
        validateKeyAndUpdateContext(signingKeyRaw, deployDispatcher);

        // If validation passes, proceed with unlock
        try {
            Path root = BlockchainShardFetcher.fetchDirectoryFromShard(
                    deployDispatcher.getBlockchainClient(),
                    this.getBlockchainContext(),
                    PathUtils.getPathDelimiterBasedOnOS() + getBlockchainContext().getWalletInfo().revAddress(),
                    getBlockchainContext().getWalletInfo().revAddress(),
                    null);

            if (!(root instanceof FetchedDirectory)) {
                throw new IllegalStateException("Root directory is not a directory");
            }

            // TODO: avoid converting RootDirectory to UnlockedWalletDirectory (rework
            // fetchDirectoryFromShard) and delete the next lines

            // convert RootDirectory to UnlockedWalletDirectory
            UnlockedWalletDirectory unlockedWalletDirectory = new UnlockedWalletDirectory(
                    blockchainContext,
                    ((FetchedDirectory) root).getChildren(),
                    getParent() == null ? null : (RootDirectory) getParent(),
                    false // skip deploy
            );

            // fix links to a new parent instance
            for (Path child : unlockedWalletDirectory.getChildren()) {
                if (child instanceof FetchedFile fetchedFile) {
                    fetchedFile.updateParent(unlockedWalletDirectory);
                }
                if (child instanceof FetchedDirectory fetchedDirectory) {
                    fetchedDirectory.updateParent(unlockedWalletDirectory);
                }
            }

            return unlockedWalletDirectory;

        } catch (NoDataByPath e) {
            // no previous mount: need to create a new root and deploy to the shard
            return new UnlockedWalletDirectory(
                    blockchainContext,
                    new HashSet<>(),
                    getParent() == null ? null : (RootDirectory) getParent(),
                    true); // do deploy
        }
    }

    private void validateKeyAndUpdateContext(String signingKeyRaw, DeployDispatcher deployDispatcher) {
        // Validate the signing key format and decode it
        byte[] signingKey;
        try {
            signingKey = Hex.decode(signingKeyRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidSigningKeyException("Invalid signing key format: " + e.getMessage(), e);
        }

        // Validate that the private key corresponds to the REV address
        try {
            PrivateKeyValidator.validatePrivateKeyForRevAddressOrThrow(signingKey,
                    getBlockchainContext().getWalletInfo().revAddress());
        } catch (PrivateKeyValidator.InvalidPrivateKeyException e) {
            throw new InvalidSigningKeyException("Private key validation failed: " + e.getMessage(), e);
        }

        this.blockchainContext = new BlockchainContext(
                new RevWalletInfo(getBlockchainContext().getWalletInfo().revAddress(), signingKey),
                deployDispatcher);
    }



    @Override
    public void addChild(Path child) throws OperationNotPermitted {
        if (child instanceof TokenFile tokenFile) {
            long amount = tokenFile.getValue();

            RevWalletInfo walletInfoFrom = tokenFile.getBlockchainContext().getWalletInfo();
            RevWalletInfo walletInfoTo = this.getBlockchainContext().getWalletInfo();

            String rholang = RholangExpressionConstructor.transfer(walletInfoFrom.revAddress(),
                    walletInfoTo.revAddress(), amount);

            getBlockchainContext().getDeployDispatcher().enqueueDeploy(new DeployDispatcher.Deployment(
                    rholang, true, F1r3flyBlockchainClient.RHOLANG, walletInfoFrom.revAddress(),
                    walletInfoFrom.signingKey(),
                    System.currentTimeMillis()));
        } else {
            throw OperationNotPermitted.instance;
        }
    }

}