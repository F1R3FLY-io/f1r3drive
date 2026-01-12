package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.app.linux.fuse.F1r3DriveFuse;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.folders.BlockchainFolderIntegration;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "f1r3FUSE",
    mixinStandardHelpOptions = true,
    version = "f1r3FUSE 1.0",
    description = "A FUSE filesystem based on the F1r3fly blockchain with automatic token discovery."
)
class F1r3DriveCli implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        F1r3DriveCli.class
    );

    private static final String[] MOUNT_OPTIONS = {
        // Linux-compatible FUSE mount options
        "-o",
        "fsname=f1r3drive",
        "-o",
        "noatime",
        "-s", // Single-threaded mode for better FUSE2 compatibility and safety
    };

    @Option(
        names = { "-h", "--validator-host" },
        description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost."
    )
    private String validatorHost = "localhost";

    @Option(
        names = { "-p", "--validator-port" },
        description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402."
    )
    private int validatorPort = 40402;

    @Option(
        names = { "-oh", "--observer-host" },
        description = "Host of the F1r3fly blockchain observer gRPC API to connect to. Defaults to localhost."
    )
    private String observerHost = "localhost";

    @Option(
        names = { "-op", "--observer-port" },
        description = "Port of the F1r3fly blockchain observer gRPC API to connect to. Defaults to 40403."
    )
    private int observerPort = 40403;

    @Option(
        names = { "-ck", "--cipher-key-path" },
        required = true,
        description = "Cipher key path. If file not found, a new key will be generated."
    )
    private String cipherKeyPath;

    @Parameters(
        index = "0",
        description = "The path at which to mount the filesystem."
    )
    private Path mountPoint;

    @Option(
        names = { "-ra", "--rev-address" },
        description = "The rev address of the wallet to unlock."
    )
    private String revAddress;

    @Option(
        names = { "-pk", "--private-key" },
        description = "The private key of the wallet to unlock."
    )
    private String privateKey;

    @Option(
        names = { "-mp", "--manual-propose" },
        required = true,
        description = "Manual propose configuration. If true, will propose and wait for finalization. If false, will skip propose and finalization waiting."
    )
    private boolean manualPropose;

    @Option(
        names = { "-d", "--debug" },
        description = "Enable FUSE debug mode for verbose logging of filesystem operations."
    )
    private boolean fuseDebug = false;

    @Option(
        names = { "--disable-token-discovery" },
        description = "Disable automatic blockchain token discovery and folder creation."
    )
    private boolean disableTokenDiscovery = false;

    @Option(
        names = { "--token-discovery-interval" },
        description = "Interval in minutes for periodic token discovery. Default: 30 minutes. Set to 0 to disable periodic discovery."
    )
    private int tokenDiscoveryInterval = 30;

    @Option(
        names = { "--demo-folder-path" },
        description = "Path for demo folder creation. Defaults to ~/demo-f1r3drive."
    )
    private String demoFolderPath =
        System.getProperty("user.home") + "/demo-f1r3drive";

    private F1r3DriveFuse f1r3DriveFuse;
    private BlockchainFolderIntegration folderIntegration;

    @Override
    public Integer call() throws Exception {
        LOGGER.info("=== F1r3Drive Starting ===");

        AESCipher.init(cipherKeyPath); // init singleton instance

        F1r3flyBlockchainClient f1R3FlyBlockchainClient =
            new F1r3flyBlockchainClient(
                validatorHost,
                validatorPort,
                observerHost,
                observerPort,
                manualPropose
            );

        // Initialize blockchain folder integration system
        if (!disableTokenDiscovery) {
            LOGGER.info("Initializing blockchain token discovery system...");
            folderIntegration = new BlockchainFolderIntegration(
                f1R3FlyBlockchainClient
            );
            startTokenDiscovery();
        } else {
            LOGGER.info(
                "Blockchain token discovery disabled by user configuration"
            );
        }

        f1r3DriveFuse = new F1r3DriveFuse(f1R3FlyBlockchainClient);

        // Add shutdown hook to ensure proper cleanup
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    LOGGER.info("F1r3Drive shutting down...");

                    if (f1r3DriveFuse != null) {
                        try {
                            f1r3DriveFuse.umount();
                        } catch (Exception e) {
                            LOGGER.error("Error during FUSE unmount", e);
                        }
                    }

                    if (folderIntegration != null) {
                        try {
                            folderIntegration.shutdown();
                            LOGGER.info(
                                "Blockchain folder integration shutdown complete"
                            );
                        } catch (Exception e) {
                            LOGGER.error(
                                "Error shutting down folder integration",
                                e
                            );
                        }
                    }

                    LOGGER.info("F1r3Drive shutdown complete");
                },
                "F1r3Drive-Shutdown"
            )
        );

        // Mount filesystem - unmounting is handled by shutdown hook
        if (revAddress != null && privateKey != null) {
            f1r3DriveFuse.mountAndUnlockRootDirectory(
                mountPoint,
                true,
                fuseDebug,
                revAddress,
                privateKey,
                MOUNT_OPTIONS
            );
        } else {
            f1r3DriveFuse.mount(mountPoint, true, fuseDebug, MOUNT_OPTIONS);
        }
        return 0;
    }

    /**
     * Starts the blockchain token discovery and folder creation system
     */
    private void startTokenDiscovery() {
        try {
            LOGGER.info(
                "Starting blockchain token discovery for demo folder: {}",
                demoFolderPath
            );

            // Perform initial discovery and folder creation
            CompletableFuture<
                BlockchainFolderIntegration.IntegrationResult
            > discoveryFuture = folderIntegration.discoverAndCreateAllFolders();

            discoveryFuture
                .thenAccept(result -> {
                    if (result.success) {
                        LOGGER.info(
                            "✓ Initial token discovery completed successfully!"
                        );
                        LOGGER.info(
                            "  - Discovered {} wallets with {} folders",
                            result.discoveredWallets,
                            result.discoveredFolders
                        );
                        LOGGER.info(
                            "  - Created {} wallet directories in {}",
                            result.createdWalletDirs,
                            demoFolderPath
                        );
                        LOGGER.info(
                            "  - Created {} folder tokens",
                            result.createdFolderTokens
                        );

                        if (result.failedOperations > 0) {
                            LOGGER.warn(
                                "  - {} operations failed during discovery",
                                result.failedOperations
                            );
                        }

                        // Display integration statistics
                        BlockchainFolderIntegration.IntegrationStats stats =
                            folderIntegration.getStats();
                        LOGGER.info("Current integration stats: {}", stats);
                    } else {
                        LOGGER.error(
                            "✗ Initial token discovery failed: {}",
                            result.errorMessage
                        );
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                        "Error during initial token discovery",
                        throwable
                    );
                    return null;
                });

            // Start continuous monitoring if interval > 0
            if (tokenDiscoveryInterval > 0) {
                LOGGER.info(
                    "Setting up continuous token monitoring every {} minutes",
                    tokenDiscoveryInterval
                );
                folderIntegration.startContinuousMonitoring(
                    tokenDiscoveryInterval
                );
            } else {
                LOGGER.info(
                    "Continuous token monitoring disabled (interval = 0)"
                );
            }

            // Set timeout for initial discovery to avoid blocking startup
            try {
                discoveryFuture.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn(
                    "Initial token discovery taking longer than expected, continuing in background...",
                    e
                );
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start token discovery system", e);
            // Don't fail the entire application if token discovery fails
        }
    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        LOGGER.info(
            "F1r3Drive CLI starting with arguments: {}",
            java.util.Arrays.toString(args)
        );

        int exitCode = new CommandLine(new F1r3DriveCli()).execute(args);

        LOGGER.info("F1r3Drive CLI exiting with code: {}", exitCode);
        System.exit(exitCode);
    }
}
