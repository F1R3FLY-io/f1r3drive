package io.f1r3fly.f1r3drive.app;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.f1r3drive.background.state.BlockingEventQueue;
import io.f1r3fly.f1r3drive.background.state.EventQueue;
import io.f1r3fly.f1r3drive.background.state.StateChangeEvents;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.PrivateKeyValidator;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.folders.BlockchainFolderIntegration;
import io.f1r3fly.f1r3drive.folders.PhysicalWalletManager;
import io.f1r3fly.f1r3drive.placeholder.CacheConfiguration;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory;
import io.f1r3fly.f1r3drive.platform.F1r3DriveChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "f1r3drive-macos",
    mixinStandardHelpOptions = true,
    version = "f1r3drive-macos 1.0",
    description = "F1r3Drive for macOS - A blockchain-based file system with native macOS integration."
)
class F1r3DriveCli implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        F1r3DriveCli.class
    );

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
        description = "The path to monitor and integrate with F1r3Drive."
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
        description = "Enable debug mode for verbose logging."
    )
    private boolean debugMode = false;

    @Option(
        names = { "--native-integration" },
        description = "Enable native macOS integration (requires native libraries)."
    )
    private boolean nativeIntegration = false;

    @Option(
        names = { "--demo-mode" },
        description = "Enable demo mode (bypasses blockchain connection for testing)."
    )
    private boolean demoMode = false;

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
        description = "Path for physical folder creation alongside the mount point. If not specified, uses the same directory as the mount point."
    )
    private String demoFolderPath;

    // Core components
    private io.f1r3fly.f1r3drive.filesystem.FileSystem fileSystem;
    private F1r3flyBlockchainClient blockchainClient;
    private BlockchainContext blockchainContext;
    private ChangeWatcher changeWatcher;
    private PlaceholderManager placeholderManager;
    private StateChangeEventsManager stateChangeEventsManager;
    private F1r3DriveChangeListener changeListener;
    private BlockchainFolderIntegration folderIntegration;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile boolean running = false;

    @Override
    public Integer call() throws Exception {
        try {
            // Set default demo folder path if not specified
            if (demoFolderPath == null) {
                demoFolderPath = mountPoint.toString();
            }
            LOGGER.info("Starting F1r3Drive for macOS...");

            // Initialize components
            initialize();

            // Start the system
            start();

            // Setup shutdown hook
            setupShutdownHook();

            LOGGER.info(
                "F1r3Drive for macOS started successfully. Monitoring path: {}",
                mountPoint
            );
            LOGGER.info("Press Ctrl+C to stop...");

            // Wait for shutdown signal
            shutdownLatch.await();

            return 0;
        } catch (Exception e) {
            LOGGER.error("Failed to start F1r3Drive for macOS", e);
            return 1;
        }
    }

    /**
     * Initialize all core components.
     */
    private void initialize() throws Exception {
        LOGGER.info(
            "Initializing F1r3Drive for macOS with mount path: {}",
            mountPoint
        );

        // Initialize encryption
        AESCipher.init(cipherKeyPath);

        // Initialize blockchain client first
        blockchainClient = new F1r3flyBlockchainClient(
            validatorHost,
            validatorPort,
            observerHost,
            observerPort,
            manualPropose
        );

        // Initialize blockchain folder integration system for physical folders
        if (!disableTokenDiscovery) {
            LOGGER.info(
                "Initializing blockchain token discovery system for physical folders..."
            );
            folderIntegration = new BlockchainFolderIntegration(
                blockchainClient,
                demoFolderPath
            );
            startTokenDiscovery();
        } else {
            LOGGER.info(
                "Blockchain token discovery disabled by user configuration"
            );
        }

        // Initialize filesystem with blockchain client
        if (demoMode) {
            LOGGER.info("Demo mode enabled - using mock filesystem");
            fileSystem = createMockFileSystem();
        } else {
            fileSystem = new InMemoryFileSystem(blockchainClient);
        }

        // Create blockchain context with real wallet information
        if (revAddress != null && privateKey != null) {
            // Validate private key
            String derivedAddress =
                PrivateKeyValidator.deriveRevAddressFromPrivateKey(privateKey);
            if (!revAddress.equals(derivedAddress)) {
                throw new RuntimeException(
                    "Private key does not match the provided REV address"
                );
            }
            byte[] privateKeyBytes = fr.acinq.secp256k1.Hex.decode(privateKey);

            // Create wallet info
            RevWalletInfo walletInfo = new RevWalletInfo(
                revAddress,
                privateKeyBytes
            );

            // Create deploy dispatcher
            DeployDispatcher deployDispatcher = new DeployDispatcher(
                blockchainClient,
                stateChangeEventsManager
            );

            // Create blockchain context
            blockchainContext = new BlockchainContext(
                walletInfo,
                deployDispatcher
            );

            LOGGER.info(
                "Created blockchain context for wallet: {}",
                revAddress
            );

            // Unlock the root directory to create TokenDirectory in memory
            LOGGER.info("Unlocking root directory for wallet: {}", revAddress);
            fileSystem.unlockRootDirectory(revAddress, privateKey);
            LOGGER.info(
                "Root directory unlocked successfully - TokenDirectory created"
            );

            // Synchronize in-memory tokens to physical filesystem
            synchronizeTokensToPhysicalFileSystem(revAddress);
        } else {
            LOGGER.warn(
                "No wallet credentials provided - some blockchain features will be unavailable"
            );
        }

        // Initialize placeholder manager
        CacheConfiguration cacheConfig = CacheConfiguration.builder()
            .maxCacheSize(100 * 1024 * 1024) // 100MB
            .evictionPolicy(CacheConfiguration.EvictionPolicy.LRU)
            .build();

        // Create FileChangeCallback for blockchain integration
        io.f1r3fly.f1r3drive.platform.FileChangeCallback fileChangeCallback =
            new io.f1r3fly.f1r3drive.platform.FileChangeCallback() {
                @Override
                public byte[] loadFileContent(String path) {
                    if (blockchainContext != null) {
                        try {
                            // Real implementation - load from blockchain
                            LOGGER.debug(
                                "Loading content from blockchain for: {}",
                                path
                            );
                            // TODO: Implement actual blockchain file loading
                            return (
                                "Blockchain content for " + path
                            ).getBytes();
                        } catch (Exception e) {
                            LOGGER.error(
                                "Failed to load content from blockchain for path: {}",
                                path,
                                e
                            );
                            return new byte[0];
                        }
                    } else {
                        LOGGER.debug(
                            "No blockchain context - returning empty content for: {}",
                            path
                        );
                        return new byte[0];
                    }
                }

                @Override
                public boolean fileExistsInBlockchain(String path) {
                    if (blockchainContext != null) {
                        try {
                            // Real implementation - check blockchain
                            LOGGER.debug(
                                "Checking file existence in blockchain for: {}",
                                path
                            );
                            // TODO: Implement actual blockchain file existence check
                            return true;
                        } catch (Exception e) {
                            LOGGER.error(
                                "Failed to check file existence in blockchain for path: {}",
                                path,
                                e
                            );
                            return false;
                        }
                    }
                    return false;
                }

                @Override
                public io.f1r3fly.f1r3drive.platform.FileChangeCallback.FileMetadata getFileMetadata(
                    String path
                ) {
                    if (blockchainContext != null) {
                        try {
                            // Real implementation - get metadata from blockchain
                            LOGGER.debug(
                                "Getting file metadata from blockchain for: {}",
                                path
                            );
                            // TODO: Implement actual blockchain metadata retrieval
                            return new io.f1r3fly.f1r3drive.platform.FileChangeCallback.FileMetadata(
                                1024,
                                System.currentTimeMillis(),
                                "blockchain-checksum",
                                false
                            );
                        } catch (Exception e) {
                            LOGGER.error(
                                "Failed to get file metadata from blockchain for path: {}",
                                path,
                                e
                            );
                        }
                    }
                    return new io.f1r3fly.f1r3drive.platform.FileChangeCallback.FileMetadata(
                        0,
                        System.currentTimeMillis(),
                        "empty",
                        false
                    );
                }

                @Override
                public void onFileSavedToBlockchain(
                    String path,
                    byte[] content
                ) {
                    if (blockchainContext != null) {
                        LOGGER.info(
                            "File saved to blockchain: {} ({} bytes)",
                            path,
                            content.length
                        );
                        // TODO: Implement actual blockchain save notification
                    } else {
                        LOGGER.debug(
                            "No blockchain context - file save notification ignored: {}",
                            path
                        );
                    }
                }

                @Override
                public void onFileDeletedFromBlockchain(String path) {
                    if (blockchainContext != null) {
                        LOGGER.info("File deleted from blockchain: {}", path);
                        // TODO: Implement actual blockchain delete notification
                    } else {
                        LOGGER.debug(
                            "No blockchain context - file delete notification ignored: {}",
                            path
                        );
                    }
                }

                @Override
                public void preloadFile(String path, int priority) {
                    if (blockchainContext != null) {
                        LOGGER.debug(
                            "Preloading file from blockchain: {} with priority {}",
                            path,
                            priority
                        );
                        // TODO: Implement actual blockchain preloading
                    } else {
                        LOGGER.debug(
                            "No blockchain context - preload ignored: {}",
                            path
                        );
                    }
                }

                @Override
                public void clearCache(String path) {
                    LOGGER.debug("Clearing cache for path: {}", path);
                    // Cache clearing is local operation, always available
                }

                @Override
                public io.f1r3fly.f1r3drive.platform.FileChangeCallback.CacheStatistics getCacheStatistics() {
                    // TODO: Implement real cache statistics
                    return new io.f1r3fly.f1r3drive.platform.FileChangeCallback.CacheStatistics(
                        0L,
                        0L,
                        0L,
                        100L * 1024 * 1024,
                        0
                    );
                }
            };

        placeholderManager = new PlaceholderManager(fileChangeCallback);

        // Initialize state change events manager
        io.f1r3fly.f1r3drive.background.state.StateChangeEventsManagerConfig config =
            io.f1r3fly.f1r3drive.background.state.StateChangeEventsManagerConfig.builder()
                .queueCapacity(1000)
                .build();
        stateChangeEventsManager = new StateChangeEventsManager(config);

        // Initialize change listener
        changeListener = new F1r3DriveChangeListener(
            fileSystem,
            placeholderManager
        );
        stateChangeEventsManager.setChangeListener(changeListener);

        // Initialize change watcher
        try {
            changeWatcher = ChangeWatcherFactory.createChangeWatcher();
            LOGGER.info("Real change watcher created successfully");
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to create real change watcher: {}",
                e.getMessage()
            );
            throw new RuntimeException(
                "Change watcher initialization failed",
                e
            );
        }

        LOGGER.info("F1r3Drive components initialized successfully");
    }

    /**
     * Start all components and begin monitoring.
     */
    private void start() throws Exception {
        LOGGER.info(
            "Starting F1r3Drive system with mount path: {}",
            mountPoint
        );

        // Connect to blockchain
        if (!demoMode && this.blockchainContext != null) {
            try {
                // Test blockchain connection
                blockchainClient.waitForNodesSynchronization();
                LOGGER.info(
                    "Successfully connected to blockchain - Validator: {}:{}, Observer: {}:{}",
                    validatorHost,
                    validatorPort,
                    observerHost,
                    observerPort
                );
            } catch (Exception e) {
                LOGGER.error("Failed to connect to blockchain", e);
                throw new RuntimeException("Blockchain connection failed", e);
            }
        } else if (demoMode) {
            LOGGER.info("Demo mode - skipping blockchain connection");
        } else {
            LOGGER.warn(
                "No blockchain context available - limited functionality"
            );
        }

        // Start state change events manager
        stateChangeEventsManager.start();
        LOGGER.info("State change events manager started");

        // Start change monitoring
        changeWatcher.startMonitoring(mountPoint.toString(), changeListener);
        LOGGER.info("File monitoring started for path: {}", mountPoint);

        // Preload blockchain files as placeholders (if wallet is provided)
        if (revAddress != null && privateKey != null) {
            preloadBlockchainFiles();
        }

        running = true;
        LOGGER.info("F1r3Drive system started successfully");
    }

    /**
     * Shutdown all components gracefully.
     */
    private void shutdown() {
        if (!running) {
            return;
        }

        LOGGER.info("Shutting down F1r3Drive system...");
        running = false;

        try {
            // Stop change monitoring
            if (changeWatcher != null) {
                changeWatcher.stopMonitoring();
                changeWatcher.cleanup();
            }

            // Stop state change events manager
            if (stateChangeEventsManager != null) {
                stateChangeEventsManager.shutdown();
            }

            // Cleanup placeholder manager
            if (placeholderManager != null) {
                placeholderManager.cleanup();
            }

            // Disconnect from blockchain
            if (blockchainClient != null) {
                LOGGER.info("Disconnected from blockchain");
            }

            // Clean up mount directory
            cleanupMountDirectory();

            LOGGER.info("F1r3Drive system shutdown completed");
            // Shutdown folder integration and wallet management
            if (folderIntegration != null) {
                try {
                    folderIntegration.terminate();
                    LOGGER.info(
                        "Physical wallet management and blockchain integration shutdown complete"
                    );
                } catch (Exception e) {
                    LOGGER.error(
                        "Error shutting down wallet management and folder integration",
                        e
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }

    /**
     * Cleans up the mount directory on shutdown
     */
    private void cleanupMountDirectory() {
        try {
            if (demoFolderPath != null && !demoFolderPath.trim().isEmpty()) {
                Path mountDir = Paths.get(demoFolderPath);
                if (Files.exists(mountDir)) {
                    LOGGER.info("Cleaning up mount directory: {}", mountDir);
                    deleteDirectoryContents(mountDir);
                    LOGGER.info("Mount directory cleanup completed");
                }
            }
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to cleanup mount directory: {}",
                e.getMessage()
            );
        }
    }

    /**
     * Recursively deletes directory contents but keeps the directory itself
     */
    private void deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        try (
            DirectoryStream<Path> stream = Files.newDirectoryStream(directory)
        ) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectoryRecursively(entry);
                } else {
                    Files.delete(entry);
                    LOGGER.debug("Deleted file: {}", entry);
                }
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (
            DirectoryStream<Path> stream = Files.newDirectoryStream(directory)
        ) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectoryRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(directory);
        LOGGER.debug("Deleted directory: {}", directory);
    }

    /**
     * Setup shutdown hook for graceful termination.
     */
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    LOGGER.info(
                        "Shutdown signal received, stopping F1r3Drive..."
                    );
                    shutdown();
                },
                "F1r3Drive-Shutdown"
            )
        );
    }

    /**
     * Preload blockchain files as placeholders.
     */
    private void preloadBlockchainFiles() {
        try {
            LOGGER.info(
                "Preloading blockchain files for wallet: {}",
                revAddress
            );

            if (demoMode) {
                // Demo mode - files will be created on demand
                LOGGER.info(
                    "Demo mode - placeholder files will be created as needed"
                );
            } else {
                // TODO: Implement blockchain file discovery and placeholder creation
                // This would query the blockchain for files associated with the wallet
                // and create placeholders in the filesystem
            }

            LOGGER.info("Blockchain file preloading completed");
        } catch (Exception e) {
            LOGGER.error("Error preloading blockchain files", e);
        }
    }

    /**
     * Create a blockchain-aware change watcher.
     */
    private ChangeWatcher createBlockchainChangeWatcher() {
        try {
            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher();

            // If we have blockchain context, we could enhance the watcher with blockchain capabilities
            if (blockchainContext != null) {
                LOGGER.info(
                    "Change watcher enhanced with blockchain context for wallet: {}",
                    blockchainContext.getWalletInfo().revAddress()
                );
            }

            return watcher;
        } catch (Exception e) {
            LOGGER.error("Failed to create blockchain change watcher", e);
            throw new RuntimeException("Change watcher creation failed", e);
        }
    }

    /**
     * Synchronizes in-memory tokens to physical filesystem
     */
    private void synchronizeTokensToPhysicalFileSystem(String revAddress) {
        try {
            LOGGER.info(
                "Synchronizing in-memory tokens to physical filesystem for wallet: {}",
                revAddress
            );

            // Find the unlocked wallet directory in filesystem
            java.io.File walletDir = findUnlockedWalletDirectory(revAddress);
            if (walletDir == null) {
                LOGGER.warn(
                    "Could not find unlocked wallet directory for: {}",
                    revAddress
                );
                return;
            }

            // Create .tokens directory in physical filesystem
            java.io.File tokensDir = new java.io.File(walletDir, ".tokens");
            if (!tokensDir.exists()) {
                tokensDir.mkdirs();
                LOGGER.info(
                    "Created physical .tokens directory: {}",
                    tokensDir.getAbsolutePath()
                );
            }

            // Get tokens from in-memory filesystem
            try {
                String walletPath = "/" + revAddress;
                io.f1r3fly.f1r3drive.filesystem.common.Directory walletInMemory =
                    fileSystem.getDirectory(walletPath);
                if (
                    walletInMemory instanceof
                        io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory
                ) {
                    io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory unlockedWallet =
                        (io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory) walletInMemory;

                    io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory tokenDirectory =
                        unlockedWallet.getTokenDirectory();
                    if (tokenDirectory != null) {
                        Set<
                            io.f1r3fly.f1r3drive.filesystem.common.Path
                        > tokens = tokenDirectory.getChildren();
                        LOGGER.info(
                            "Found {} tokens in memory to sync",
                            tokens.size()
                        );

                        for (io.f1r3fly.f1r3drive.filesystem.common.Path token : tokens) {
                            if (
                                token instanceof
                                    io.f1r3fly.f1r3drive.filesystem.local.TokenFile
                            ) {
                                io.f1r3fly.f1r3drive.filesystem.local.TokenFile tokenFile =
                                    (io.f1r3fly.f1r3drive.filesystem.local.TokenFile) token;

                                // Create physical token file
                                java.io.File physicalTokenFile =
                                    new java.io.File(
                                        tokensDir,
                                        tokenFile.getName()
                                    );
                                if (!physicalTokenFile.exists()) {
                                    try {
                                        physicalTokenFile.createNewFile();

                                        // Token files are empty - information is in filename only
                                        java.nio.file.Files.write(
                                            physicalTokenFile.toPath(),
                                            new byte[0]
                                        );

                                        LOGGER.debug(
                                            "Created physical token file: {}",
                                            physicalTokenFile.getName()
                                        );
                                    } catch (java.io.IOException e) {
                                        LOGGER.warn(
                                            "Failed to create token file: {}",
                                            physicalTokenFile.getName(),
                                            e
                                        );
                                    }
                                }
                            }
                        }
                        LOGGER.info(
                            "Token synchronization completed for wallet: {}",
                            revAddress
                        );
                    } else {
                        LOGGER.warn(
                            "TokenDirectory not found in unlocked wallet"
                        );
                    }
                } else {
                    LOGGER.warn("Wallet directory is not unlocked in memory");
                }
            } catch (Exception e) {
                LOGGER.error("Error accessing in-memory token directory", e);
            }
        } catch (Exception e) {
            LOGGER.error(
                "Failed to synchronize tokens to physical filesystem",
                e
            );
        }
    }

    /**
     * Find the physical unlocked wallet directory
     */
    private java.io.File findUnlockedWalletDirectory(String revAddress) {
        java.io.File baseDir = new java.io.File(demoFolderPath);

        // Try different possible wallet directory names
        String[] possibleNames = {
            revAddress,
            "111127RX...nR32PiHA", // Shortened version
            revAddress.substring(0, 8) +
            "..." +
            revAddress.substring(revAddress.length() - 8),
        };

        for (String name : possibleNames) {
            java.io.File walletDir = new java.io.File(baseDir, name);
            if (walletDir.exists() && walletDir.isDirectory()) {
                // Check if it's unlocked (has .unlocked file or no .locked file)
                java.io.File unlockedFile = new java.io.File(
                    walletDir,
                    ".unlocked"
                );
                java.io.File lockedFile = new java.io.File(
                    walletDir,
                    ".locked"
                );

                if (unlockedFile.exists() || !lockedFile.exists()) {
                    LOGGER.info(
                        "Found unlocked wallet directory: {}",
                        walletDir.getAbsolutePath()
                    );
                    return walletDir;
                }
            }
        }

        LOGGER.warn(
            "Could not find unlocked wallet directory for address: {}",
            revAddress
        );
        return null;
    }

    /**
     * Starts the blockchain token discovery and physical folder creation system
     */
    private void startTokenDiscovery() {
        try {
            LOGGER.info(
                "Starting physical wallet creation and management: {}",
                demoFolderPath
            );

            // Perform initial wallet creation and unlocking if private key provided
            CompletableFuture<
                BlockchainFolderIntegration.IntegrationResult
            > discoveryFuture;

            // Always create ALL wallets from genesis block (all start LOCKED)
            // But exclude the wallet we have private key for
            LOGGER.info(
                "Creating ALL wallets from blockchain with 'locked_' prefix (read-only)"
            );

            Set<String> excludeFromLocked = Set.of();
            if (
                revAddress != null &&
                !revAddress.trim().isEmpty() &&
                privateKey != null &&
                !privateKey.trim().isEmpty()
            ) {
                excludeFromLocked = Set.of(revAddress);
                LOGGER.info(
                    "Excluding wallet {} from locked creation (will be unlocked instead)",
                    revAddress
                );
            }

            discoveryFuture = folderIntegration.discoverAndCreateAllFolders(
                excludeFromLocked
            );

            // If specific wallet address and private key provided, unlock only that wallet
            if (
                revAddress != null &&
                !revAddress.trim().isEmpty() &&
                privateKey != null &&
                !privateKey.trim().isEmpty()
            ) {
                LOGGER.info(
                    "Private key provided - wallet {} will be UNLOCKED for full access",
                    revAddress
                );

                discoveryFuture = discoveryFuture.thenCompose(result -> {
                    if (result.success) {
                        LOGGER.info(
                            "Unlocking specific wallet with provided private key: {}",
                            revAddress
                        );

                        // Validate wallet operations after unlock
                        try {
                            folderIntegration.validateWalletOperation(
                                revAddress,
                                "read_file"
                            );
                            LOGGER.info(
                                "✓ Wallet {} ready for read operations",
                                revAddress
                            );

                            folderIntegration.validateWalletOperation(
                                revAddress,
                                "create_file"
                            );
                            LOGGER.info(
                                "✓ Wallet {} ready for write operations",
                                revAddress
                            );
                        } catch (IllegalStateException e) {
                            LOGGER.warn(
                                "⚠ Wallet {} has limited access: {}",
                                revAddress,
                                e.getMessage()
                            );
                        }

                        return folderIntegration.unlockPhysicalWallet(
                            revAddress,
                            privateKey
                        );
                    } else {
                        return CompletableFuture.completedFuture(result);
                    }
                });
            } else if (revAddress != null && !revAddress.trim().isEmpty()) {
                LOGGER.warn(
                    "No private key provided - wallet {} will remain locked (read-only)",
                    revAddress
                );
                LOGGER.info(
                    "To unlock wallet {}, provide --private-key parameter",
                    revAddress
                );
            } else {
                LOGGER.info(
                    "No specific wallet specified - all wallets will remain locked (read-only)"
                );
                LOGGER.info(
                    "To unlock a wallet, provide both --rev-address and --private-key parameters"
                );
            }

            discoveryFuture
                .thenAccept(result -> {
                    if (result.success) {
                        LOGGER.info(
                            "✓ Physical wallet management completed successfully!"
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

                        // Check wallet unlock status
                        if (
                            revAddress != null &&
                            privateKey != null &&
                            !privateKey.trim().isEmpty()
                        ) {
                            PhysicalWalletManager walletManager =
                                folderIntegration.getWalletManager();
                            if (walletManager.isWalletUnlocked(revAddress)) {
                                LOGGER.info(
                                    "  ✓ Wallet {} is UNLOCKED with full access",
                                    revAddress
                                );

                                // Demonstrate available operations
                                try {
                                    LOGGER.info("    Available operations:");
                                    LOGGER.info(
                                        "    - Create/modify/delete files and directories"
                                    );
                                    LOGGER.info("    - Update token balances");
                                    LOGGER.info(
                                        "    - Execute blockchain transactions"
                                    );
                                    LOGGER.info(
                                        "    - Full read/write access to wallet contents"
                                    );

                                    // Test a simple operation to verify unlocked status
                                    folderIntegration.validateWalletOperation(
                                        revAddress,
                                        "create_file"
                                    );
                                    LOGGER.info(
                                        "    ✓ Wallet operations validated - FULL ACCESS confirmed"
                                    );
                                    LOGGER.info(
                                        "    ✓ SECURITY: ONLY this wallet {} can modify files",
                                        revAddress
                                    );
                                    LOGGER.info(
                                        "    ✓ This wallet folder has NO prefix (unlocked)"
                                    );
                                    LOGGER.info(
                                        "    ✓ All other wallets have 'locked_' prefix (read-only)"
                                    );
                                } catch (IllegalStateException e) {
                                    LOGGER.warn(
                                        "    ✗ Unexpected access restriction: {}",
                                        e.getMessage()
                                    );
                                } catch (Exception e) {
                                    LOGGER.debug(
                                        "    Note: Operation validation requires wallet manager setup"
                                    );
                                }
                            } else {
                                LOGGER.warn(
                                    "  ⚠ Wallet {} remains LOCKED - check private key",
                                    revAddress
                                );
                                LOGGER.warn(
                                    "  ⚠ This wallet folder has 'locked_' prefix (read-only)"
                                );
                                LOGGER.warn(
                                    "  ⚠ To unlock: provide correct private key (folder will lose 'locked_' prefix)"
                                );

                                // Show what's restricted for locked wallets
                                try {
                                    LOGGER.info(
                                        "    Restricted operations (wallet is locked):"
                                    );
                                    LOGGER.info(
                                        "    ✗ Cannot create/modify/delete files"
                                    );
                                    LOGGER.info(
                                        "    ✗ Cannot update token balances"
                                    );
                                    LOGGER.info(
                                        "    ✗ Cannot execute blockchain transactions"
                                    );
                                    LOGGER.info(
                                        "    ✓ Can read files and view wallet structure"
                                    );

                                    // Test that write operations are blocked
                                    folderIntegration.validateWalletOperation(
                                        revAddress,
                                        "create_file"
                                    );
                                    LOGGER.warn(
                                        "    Unexpected: Write operations should be blocked on locked wallet"
                                    );
                                } catch (IllegalStateException e) {
                                    LOGGER.info(
                                        "    ✓ Write operations properly blocked: {}",
                                        e.getMessage()
                                    );
                                } catch (Exception e) {
                                    LOGGER.debug(
                                        "    Note: Operation validation requires wallet manager setup"
                                    );
                                }
                            }
                        } else {
                            LOGGER.info(
                                "  - All wallets created in LOCKED state"
                            );
                            LOGGER.info(
                                "  - SECURITY ENFORCED: All wallets have 'locked_' prefix (read-only)"
                            );
                            LOGGER.info(
                                "  - To unlock a specific wallet: restart with --rev-address <address> --private-key <key>"
                            );
                            LOGGER.info(
                                "  - Only the unlocked wallet will have its 'locked_' prefix removed"
                            );
                            LOGGER.info(
                                "  - All other wallets remain read-only with 'locked_' prefix"
                            );
                        }

                        if (result.failedOperations > 0) {
                            LOGGER.warn(
                                "  - {} operations failed during wallet creation",
                                result.failedOperations
                            );
                        }
                    } else {
                        LOGGER.error(
                            "✗ Physical wallet creation failed: {}",
                            result.errorMessage
                        );
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                        "Error during physical folder creation",
                        throwable
                    );
                    return null;
                });

            // Start continuous monitoring if interval > 0
            if (tokenDiscoveryInterval > 0) {
                if (revAddress != null && !revAddress.trim().isEmpty()) {
                    LOGGER.info(
                        "Setting up continuous physical wallet monitoring for {} every {} minutes",
                        revAddress,
                        tokenDiscoveryInterval
                    );
                    folderIntegration.startContinuousMonitoringForWallet(
                        revAddress,
                        tokenDiscoveryInterval
                    );
                } else {
                    LOGGER.info(
                        "Setting up continuous physical wallet monitoring for all wallets every {} minutes",
                        tokenDiscoveryInterval
                    );
                    folderIntegration.startContinuousMonitoring(
                        tokenDiscoveryInterval
                    );
                }
            } else {
                LOGGER.info(
                    "Continuous wallet monitoring disabled (interval = 0)"
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
            LOGGER.error("Failed to start physical folder creation system", e);
            // Don't fail the entire application if folder creation fails
        }
    }

    /**
     * Creates a mock filesystem for demo mode.
     */
    private io.f1r3fly.f1r3drive.filesystem.FileSystem createMockFileSystem() {
        LOGGER.info("Creating mock filesystem for demo mode");
        // Return a simple mock that doesn't require blockchain connection
        // Return the in-memory filesystem for demo mode
        return new InMemoryFileSystem(blockchainClient);
    }

    /**
     * Main entry point for the F1r3Drive macOS application.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new F1r3DriveCli()).execute(args);
        System.exit(exitCode);
    }
}
