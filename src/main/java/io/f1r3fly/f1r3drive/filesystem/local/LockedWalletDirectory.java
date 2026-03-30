package io.f1r3fly.f1r3drive.filesystem.local;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.PrivateKeyValidator;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.InvalidSigningKeyException;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.common.ReadOnlyDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedFile;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

public class LockedWalletDirectory
    extends AbstractLocalPath
    implements ReadOnlyDirectory {

    private static Logger logger = LoggerFactory.getLogger(
        LockedWalletDirectory.class
    );

    public LockedWalletDirectory(
        BlockchainContext blockchainContext,
        RootDirectory parent
    ) {
        super(
            blockchainContext,
            "LOCKED-REMOTE-REV-" +
                blockchainContext.getWalletInfo().revAddress(),
            parent
        );
        this.lastUpdated = 0L;
    }

    @Override
    public Set<Path> getChildren() {
        return Set.of();
    }

    public UnlockedWalletDirectory unlock(
        String signingKeyRaw,
        DeployDispatcher deployDispatcher
    ) throws InvalidSigningKeyException {
        validateKeyAndUpdateContext(signingKeyRaw, deployDispatcher);

        // If validation passes, proceed with unlock
        try {
            Path root = fetchDirectoryFromShard(
                deployDispatcher.getBlockchainClient(),
                PathUtils.getPathDelimiterBasedOnOS() +
                    getBlockchainContext().getWalletInfo().revAddress(),
                getBlockchainContext().getWalletInfo().revAddress(),
                null
            );

            if (!(root instanceof FetchedDirectory)) {
                throw new IllegalStateException(
                    "Root directory is not a directory"
                );
            }

            // TODO: avoid converting RootDirectory to UnlockedWalletDirectory (rework
            // fetchDirectoryFromShard) and delete the next lines

            // convert RootDirectory to UnlockedWalletDirectory
            UnlockedWalletDirectory unlockedWalletDirectory =
                new UnlockedWalletDirectory(
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
                true
            ); // do deploy
        }
    }

    private void validateKeyAndUpdateContext(
        String signingKeyRaw,
        DeployDispatcher deployDispatcher
    ) {
        // Validate the signing key format and decode it
        byte[] signingKey;
        try {
            signingKey = Hex.decode(signingKeyRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidSigningKeyException(
                "Invalid signing key format: " + e.getMessage(),
                e
            );
        }

        // Validate that the private key corresponds to the REV address
        try {
            PrivateKeyValidator.validatePrivateKeyForRevAddressOrThrow(
                signingKey,
                getBlockchainContext().getWalletInfo().revAddress()
            );
        } catch (PrivateKeyValidator.InvalidPrivateKeyException e) {
            throw new InvalidSigningKeyException(
                "Private key validation failed: " + e.getMessage(),
                e
            );
        }

        this.blockchainContext = new BlockchainContext(
            new RevWalletInfo(
                getBlockchainContext().getWalletInfo().revAddress(),
                signingKey
            ),
            deployDispatcher
        );
    }

    public Path fetchDirectoryFromShard(
        F1r3flyBlockchainClient f1R3FlyBlockchainClient,
        String absolutePath,
        String name,
        BlockchainDirectory parent
    ) throws NoDataByPath {
        try {
            // Use exploratory deploy with rholang code to read from channel
            String rholangCode = RholangExpressionConstructor.readFromChannel(
                absolutePath
            );
            RhoTypes.Expr result = f1R3FlyBlockchainClient.exploratoryDeploy(
                rholangCode
            );

            RholangExpressionConstructor.ChannelData fileOrDir =
                RholangExpressionConstructor.parseExploratoryDeployResult(
                    result
                );

            if (fileOrDir.isDir()) {
                FetchedDirectory dir = new FetchedDirectory(
                    this.getBlockchainContext(),
                    name,
                    parent,
                    fileOrDir.lastUpdated()
                );

                Set<Path> children = fileOrDir
                    .children()
                    .stream()
                    .map(childName -> {
                        try {
                            return fetchDirectoryFromShard(
                                f1R3FlyBlockchainClient,
                                absolutePath +
                                    PathUtils.getPathDelimiterBasedOnOS() +
                                    childName,
                                childName,
                                dir
                            );
                        } catch (NoDataByPath e) {
                            logger.error(
                                "Error fetching child directory from shard for path: {}",
                                absolutePath +
                                    PathUtils.getPathDelimiterBasedOnOS() +
                                    childName,
                                e
                            );
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                dir.setChildren(children);
                return dir;
            } else {
                FetchedFile file = new FetchedFile(
                    this.getBlockchainContext(),
                    PathUtils.getFileName(absolutePath),
                    parent,
                    fileOrDir.lastUpdated()
                );
                long offset = 0;
                offset = file.initFromBytes(fileOrDir.firstChunk(), offset);

                if (!fileOrDir.otherChunks().isEmpty()) {
                    Set<Integer> chunkNumbers = fileOrDir
                        .otherChunks()
                        .keySet();
                    Integer[] sortedChunkNumbers = chunkNumbers
                        .stream()
                        .sorted()
                        .toArray(Integer[]::new);

                    for (Integer chunkNumber : sortedChunkNumbers) {
                        String subChannel = fileOrDir
                            .otherChunks()
                            .get(chunkNumber);
                        String subChannelRholangCode =
                            RholangExpressionConstructor.readFromChannel(
                                subChannel
                            );
                        RhoTypes.Expr subChannelResult =
                            f1R3FlyBlockchainClient.exploratoryDeploy(
                                subChannelRholangCode
                            );
                        byte[] data =
                            RholangExpressionConstructor.parseExploratoryDeployBytes(
                                subChannelResult
                            );

                        offset = offset + file.initFromBytes(data, offset);
                    }
                }

                file.initSubChannels(fileOrDir.otherChunks());
                return file;
            }
        } catch (NoDataByPath e) {
            logger.info("No data found for path: {}", absolutePath);
            throw e;
        } catch (io.f1r3fly.f1r3drive.errors.F1r3DriveError e) {
            logger.info("No data found for path: {}", absolutePath);
            throw new NoDataByPath(absolutePath, "", e);
        } catch (Throwable e) {
            logger.error(
                "Error fetching directory from shard for path: {}",
                absolutePath,
                e
            );
            throw new RuntimeException(
                "Failed to fetch directory data for " + absolutePath,
                e
            );
        }
    }

    @Override
    public void addChild(Path child) throws OperationNotPermitted {
        logger.info(
            "WALLET_ACTION: Adding child to locked wallet directory: {} -> {}",
            getAbsolutePath(),
            child.getName()
        );

        if (child instanceof TokenFile tokenFile) {
            long amount = tokenFile.getValue();

            RevWalletInfo walletInfoFrom = tokenFile
                .getBlockchainContext()
                .getWalletInfo();
            RevWalletInfo walletInfoTo =
                this.getBlockchainContext().getWalletInfo();

            logger.info(
                "WALLET_TOKEN_TRANSFER: Transferring {} tokens from {} to {}",
                amount,
                walletInfoFrom.revAddress(),
                walletInfoTo.revAddress()
            );

            String rholang = RholangExpressionConstructor.transfer(
                walletInfoFrom.revAddress(),
                walletInfoTo.revAddress(),
                amount
            );

            getBlockchainContext()
                .getDeployDispatcher()
                .enqueueDeploy(
                    new DeployDispatcher.Deployment(
                        rholang,
                        true,
                        F1r3flyBlockchainClient.RHOLANG,
                        walletInfoFrom.revAddress(),
                        walletInfoFrom.signingKey(),
                        System.currentTimeMillis()
                    )
                );

            logger.info(
                "WALLET_DEPLOY_QUEUED: Transfer deployment queued for locked wallet {}",
                getAbsolutePath()
            );
        } else {
            logger.warn(
                "WALLET_ERROR: Attempted to add non-TokenFile to locked wallet: {} (type: {})",
                child.getName(),
                child.getClass().getSimpleName()
            );
            throw OperationNotPermitted.instance;
        }
    }
}
