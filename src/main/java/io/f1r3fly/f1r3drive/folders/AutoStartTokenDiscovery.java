package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified auto-start token discovery system for integration with existing F1r3Drive components
 * Automatically starts when created and runs blockchain token discovery in background
 */
public class AutoStartTokenDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AutoStartTokenDiscovery.class
    );

    private final F1r3flyBlockchainClient blockchainClient;
    private final BlockchainFolderIntegration integration;
    private final ExecutorService executorService;
    private volatile boolean isRunning = false;

    // Default configuration
    private static final String DEFAULT_DEMO_PATH =
        System.getProperty("user.home") + "/demo-f1r3drive";
    private static final int DEFAULT_DISCOVERY_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MONITORING_INTERVAL_MINUTES = 30;

    /**
     * Creates and auto-starts token discovery system
     * @param blockchainClient The blockchain client to use
     */
    public AutoStartTokenDiscovery(F1r3flyBlockchainClient blockchainClient) {
        this(
            blockchainClient,
            DEFAULT_DEMO_PATH,
            DEFAULT_MONITORING_INTERVAL_MINUTES
        );
    }

    /**
     * Creates and auto-starts token discovery system with custom configuration
     * @param blockchainClient The blockchain client to use
     * @param demoPath Path where demo folders should be created
     * @param monitoringIntervalMinutes Interval for continuous monitoring (0 to disable)
     */
    public AutoStartTokenDiscovery(
        F1r3flyBlockchainClient blockchainClient,
        String demoPath,
        int monitoringIntervalMinutes
    ) {
        this.blockchainClient = blockchainClient;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "TokenDiscovery-Worker");
            thread.setDaemon(true); // Don't prevent JVM shutdown
            return thread;
        });

        LOGGER.info("Initializing AutoStartTokenDiscovery...");
        LOGGER.info("Demo path: {}", demoPath);
        LOGGER.info(
            "Monitoring interval: {} minutes",
            monitoringIntervalMinutes
        );

        try {
            // Create integration system
            this.integration = new BlockchainFolderIntegration(
                blockchainClient
            );

            // Start discovery automatically
            startDiscovery(monitoringIntervalMinutes);

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(
                new Thread(this::shutdown, "TokenDiscovery-Shutdown")
            );

            LOGGER.info("✓ AutoStartTokenDiscovery initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize AutoStartTokenDiscovery", e);
            throw new RuntimeException(
                "Failed to initialize token discovery system",
                e
            );
        }
    }

    /**
     * Starts the discovery process
     */
    private void startDiscovery(int monitoringIntervalMinutes) {
        if (isRunning) {
            LOGGER.warn("Token discovery is already running");
            return;
        }

        isRunning = true;
        LOGGER.info("Starting blockchain token discovery...");

        // Start initial discovery in background
        CompletableFuture.runAsync(
            () -> {
                try {
                    performInitialDiscovery();

                    // Start continuous monitoring if enabled
                    if (monitoringIntervalMinutes > 0) {
                        startContinuousMonitoring(monitoringIntervalMinutes);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in discovery process", e);
                    isRunning = false;
                }
            },
            executorService
        );
    }

    /**
     * Performs initial blockchain discovery
     */
    private void performInitialDiscovery() {
        try {
            LOGGER.info("Performing initial blockchain token discovery...");

            CompletableFuture<
                BlockchainFolderIntegration.IntegrationResult
            > discoveryFuture = integration.discoverAndCreateAllFolders();

            // Wait for completion with timeout
            BlockchainFolderIntegration.IntegrationResult result =
                discoveryFuture.get(
                    DEFAULT_DISCOVERY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            if (result.success) {
                LOGGER.info("✓ Initial discovery completed successfully!");
                logDiscoveryResults(result);

                // Show integration statistics
                BlockchainFolderIntegration.IntegrationStats stats =
                    integration.getStats();
                LOGGER.info("Integration statistics: {}", stats);
            } else {
                LOGGER.error(
                    "✗ Initial discovery failed: {}",
                    result.errorMessage
                );
            }
        } catch (Exception e) {
            LOGGER.error("Error during initial discovery", e);
        }
    }

    /**
     * Starts continuous monitoring
     */
    private void startContinuousMonitoring(int intervalMinutes) {
        LOGGER.info(
            "Starting continuous token monitoring every {} minutes",
            intervalMinutes
        );

        try {
            integration.startContinuousMonitoring(intervalMinutes);
            LOGGER.info("✓ Continuous monitoring started");
        } catch (Exception e) {
            LOGGER.error("Failed to start continuous monitoring", e);
        }
    }

    /**
     * Logs discovery results in a user-friendly format
     */
    private void logDiscoveryResults(
        BlockchainFolderIntegration.IntegrationResult result
    ) {
        LOGGER.info("=== Discovery Results ===");
        LOGGER.info("Discovered wallets: {}", result.discoveredWallets);
        LOGGER.info("Discovered folders: {}", result.discoveredFolders);
        LOGGER.info("Created wallet directories: {}", result.createdWalletDirs);
        LOGGER.info("Created folder tokens: {}", result.createdFolderTokens);

        if (result.failedOperations > 0) {
            LOGGER.warn("Failed operations: {}", result.failedOperations);
        }

        LOGGER.info("Demo folders created in: {}", DEFAULT_DEMO_PATH);
        LOGGER.info("========================");
    }

    /**
     * Gets current statistics
     */
    public BlockchainFolderIntegration.IntegrationStats getStats() {
        return integration != null ? integration.getStats() : null;
    }

    /**
     * Checks if discovery is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Triggers manual discovery
     */
    public CompletableFuture<
        BlockchainFolderIntegration.IntegrationResult
    > triggerManualDiscovery() {
        if (!isRunning) {
            return CompletableFuture.completedFuture(
                createFailedResult("Discovery system is not running")
            );
        }

        LOGGER.info("Triggering manual blockchain discovery...");
        return integration
            .discoverAndCreateAllFolders()
            .thenApply(result -> {
                LOGGER.info(
                    "Manual discovery completed: {}",
                    result.success ? "SUCCESS" : "FAILED"
                );
                if (result.success) {
                    logDiscoveryResults(result);
                }
                return result;
            });
    }

    /**
     * Creates a failed result
     */
    private BlockchainFolderIntegration.IntegrationResult createFailedResult(
        String errorMessage
    ) {
        BlockchainFolderIntegration.IntegrationResult result =
            new BlockchainFolderIntegration.IntegrationResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * Gracefully shuts down the discovery system
     */
    public void shutdown() {
        if (!isRunning) {
            return;
        }

        LOGGER.info("Shutting down AutoStartTokenDiscovery...");
        isRunning = false;

        try {
            if (integration != null) {
                integration.shutdown();
            }

            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }

            LOGGER.info("✓ AutoStartTokenDiscovery shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
            executorService.shutdownNow();
        }
    }

    /**
     * Static factory method for easy integration
     */
    public static AutoStartTokenDiscovery createAndStart(
        F1r3flyBlockchainClient blockchainClient
    ) {
        return new AutoStartTokenDiscovery(blockchainClient);
    }

    /**
     * Static factory method with custom configuration
     */
    public static AutoStartTokenDiscovery createAndStart(
        F1r3flyBlockchainClient blockchainClient,
        String demoPath,
        int monitoringIntervalMinutes
    ) {
        return new AutoStartTokenDiscovery(
            blockchainClient,
            demoPath,
            monitoringIntervalMinutes
        );
    }

    /**
     * Helper method to safely create discovery system
     */
    public static AutoStartTokenDiscovery createSafely(
        F1r3flyBlockchainClient blockchainClient
    ) {
        try {
            return createAndStart(blockchainClient);
        } catch (Exception e) {
            LOGGER.error(
                "Failed to create AutoStartTokenDiscovery, will continue without token discovery",
                e
            );
            return null; // Return null instead of failing the entire application
        }
    }

    @Override
    public String toString() {
        return String.format(
            "AutoStartTokenDiscovery{running=%s, stats=%s}",
            isRunning,
            getStats()
        );
    }
}
