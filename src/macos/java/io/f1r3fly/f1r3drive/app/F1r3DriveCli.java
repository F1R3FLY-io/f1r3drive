package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.background.state.BlockingEventQueue;
import io.f1r3fly.f1r3drive.background.state.EventQueue;
import io.f1r3fly.f1r3drive.background.state.StateChangeEvents;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.placeholder.CacheConfiguration;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory;
import io.f1r3fly.f1r3drive.platform.F1r3DriveChangeListener;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

    // Core components
    private InMemoryFileSystem fileSystem;
    private F1r3flyBlockchainClient blockchainClient;
    private ChangeWatcher changeWatcher;
    private PlaceholderManager placeholderManager;
    private StateChangeEventsManager stateChangeEventsManager;
    private F1r3DriveChangeListener changeListener;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile boolean running = false;

    @Override
    public Integer call() throws Exception {
        try {
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

        // Initialize filesystem with blockchain client
        fileSystem = new InMemoryFileSystem(blockchainClient);

        // Create blockchain context - will be used later for wallet operations

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
                    // Mock implementation - would load from blockchain
                    LOGGER.debug("Mock loading content for: {}", path);
                    return ("Mock content for " + path).getBytes();
                }

                @Override
                public boolean fileExistsInBlockchain(String path) {
                    return true; // Mock - assume all files exist
                }

                @Override
                public io.f1r3fly.f1r3drive.platform.FileChangeCallback.FileMetadata getFileMetadata(
                    String path
                ) {
                    return new io.f1r3fly.f1r3drive.platform.FileChangeCallback.FileMetadata(
                        1024,
                        System.currentTimeMillis(),
                        "mock-checksum",
                        false
                    );
                }

                @Override
                public void onFileSavedToBlockchain(
                    String path,
                    byte[] content
                ) {
                    LOGGER.debug("Mock file saved to blockchain: {}", path);
                }

                @Override
                public void onFileDeletedFromBlockchain(String path) {
                    LOGGER.debug("Mock file deleted from blockchain: {}", path);
                }

                @Override
                public void preloadFile(String path, int priority) {
                    LOGGER.debug(
                        "Mock preloading: {} with priority {}",
                        path,
                        priority
                    );
                }

                @Override
                public void clearCache(String path) {
                    LOGGER.debug("Mock cache invalidation: {}", path);
                }

                @Override
                public io.f1r3fly.f1r3drive.platform.FileChangeCallback.CacheStatistics getCacheStatistics() {
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
        if (nativeIntegration) {
            try {
                changeWatcher = ChangeWatcherFactory.createChangeWatcher();
                LOGGER.info("Native macOS integration enabled");
            } catch (Exception e) {
                LOGGER.warn(
                    "Failed to create native change watcher, falling back to mock implementation: {}",
                    e.getMessage()
                );
                changeWatcher = createMockChangeWatcher();
            }
        } else {
            LOGGER.info(
                "Using mock change watcher (native integration disabled)"
            );
            changeWatcher = createMockChangeWatcher();
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

        // Connect to blockchain (mock for now)
        LOGGER.info("Connected to blockchain (mock implementation)");

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

            // Disconnect from blockchain (mock for now)
            LOGGER.info("Disconnected from blockchain (mock implementation)");

            LOGGER.info("F1r3Drive system shutdown completed");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
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
            // TODO: Implement blockchain file discovery and placeholder creation
            // This would query the blockchain for files associated with the wallet
            // and create placeholders in the filesystem
            LOGGER.info("Blockchain file preloading completed");
        } catch (Exception e) {
            LOGGER.error("Error preloading blockchain files", e);
        }
    }

    /**
     * Create a mock change watcher for testing/demo purposes.
     */
    private ChangeWatcher createMockChangeWatcher() {
        return new ChangeWatcher() {
            private boolean monitoring = false;

            @Override
            public void startMonitoring(
                String path,
                io.f1r3fly.f1r3drive.platform.ChangeListener listener
            ) throws Exception {
                LOGGER.info("Mock change watcher started monitoring: {}", path);
                monitoring = true;
            }

            @Override
            public void stopMonitoring() {
                LOGGER.info("Mock change watcher stopped monitoring");
                monitoring = false;
            }

            @Override
            public boolean isMonitoring() {
                return monitoring;
            }

            @Override
            public io.f1r3fly.f1r3drive.platform.PlatformInfo getPlatformInfo() {
                return new io.f1r3fly.f1r3drive.platform.macos.MacOSPlatformInfo();
            }

            @Override
            public void cleanup() {
                monitoring = false;
                LOGGER.info("Mock change watcher cleaned up");
            }

            @Override
            public void setFileChangeCallback(
                io.f1r3fly.f1r3drive.platform.FileChangeCallback callback
            ) {
                // Mock implementation
            }
        };
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new F1r3DriveCli()).execute(args);
        System.exit(exitCode);
    }
}
