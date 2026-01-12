package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Final integrated demo showing automatic token discovery on F1r3Drive startup
 * This demo shows exactly what happens when F1r3Drive starts with token discovery enabled
 */
public class F1r3DriveStartupDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        F1r3DriveStartupDemo.class
    );

    // Default blockchain configuration
    private static final String DEFAULT_VALIDATOR_HOST = "localhost";
    private static final int DEFAULT_VALIDATOR_PORT = 40402;
    private static final String DEFAULT_OBSERVER_HOST = "localhost";
    private static final int DEFAULT_OBSERVER_PORT = 40403;
    private static final boolean DEFAULT_MANUAL_PROPOSE = true;

    // Demo folder path
    private static final String DEMO_FOLDER_PATH =
        System.getProperty("user.home") + "/demo-f1r3drive";

    public static void main(String[] args) {
        F1r3DriveStartupDemo demo = new F1r3DriveStartupDemo();
        demo.runStartupDemo();
    }

    /**
     * Simulates F1r3Drive startup with automatic token discovery
     */
    public void runStartupDemo() {
        LOGGER.info(
            "=== F1r3Drive Startup Demo with Automatic Token Discovery ==="
        );
        LOGGER.info("This demo shows what happens when F1r3Drive starts:");
        LOGGER.info("1. Blockchain client connects");
        LOGGER.info("2. Token discovery system automatically starts");
        LOGGER.info("3. Existing blockchain tokens are discovered");
        LOGGER.info(
            "4. Folders are automatically created in {}",
            DEMO_FOLDER_PATH
        );
        LOGGER.info("5. System continues running with periodic monitoring");
        LOGGER.info("");

        try {
            // Step 1: Create blockchain client (like F1r3DriveCli does)
            LOGGER.info("Step 1: Creating blockchain client...");
            F1r3flyBlockchainClient blockchainClient = createBlockchainClient();
            LOGGER.info("✓ Blockchain client created and connected");

            // Step 2: Initialize automatic token discovery (like F1r3DriveFuse constructor does)
            LOGGER.info("");
            LOGGER.info("Step 2: Initializing automatic token discovery...");
            AutoStartTokenDiscovery tokenDiscovery =
                AutoStartTokenDiscovery.createSafely(blockchainClient);

            if (tokenDiscovery != null) {
                LOGGER.info("✓ Token discovery system started automatically");
                LOGGER.info("✓ Background blockchain scanning initiated");
                LOGGER.info(
                    "✓ Demo folders will be created in: {}",
                    DEMO_FOLDER_PATH
                );

                // Step 3: Wait for initial discovery to complete
                LOGGER.info("");
                LOGGER.info("Step 3: Waiting for initial token discovery...");
                Thread.sleep(5000); // Give time for discovery to start

                // Step 4: Show statistics
                LOGGER.info("");
                LOGGER.info("Step 4: Checking discovery results...");
                BlockchainFolderIntegration.IntegrationStats stats =
                    tokenDiscovery.getStats();
                if (stats != null) {
                    LOGGER.info("Current statistics: {}", stats);
                    LOGGER.info(
                        "✓ Managed wallets: {}",
                        stats.getManagedWallets()
                    );
                    LOGGER.info(
                        "✓ Managed folders: {}",
                        stats.getManagedFolders()
                    );
                } else {
                    LOGGER.info("Discovery still in progress...");
                }

                // Step 5: Trigger manual discovery to demonstrate functionality
                LOGGER.info("");
                LOGGER.info(
                    "Step 5: Triggering manual discovery for demonstration..."
                );
                tokenDiscovery
                    .triggerManualDiscovery()
                    .thenAccept(result -> {
                        if (result.success) {
                            LOGGER.info(
                                "✓ Manual discovery completed successfully!"
                            );
                            LOGGER.info(
                                "  - Discovered {} wallets",
                                result.discoveredWallets
                            );
                            LOGGER.info(
                                "  - Created {} wallet directories",
                                result.createdWalletDirs
                            );
                            LOGGER.info(
                                "  - Created {} folder tokens",
                                result.createdFolderTokens
                            );
                        } else {
                            LOGGER.warn(
                                "Manual discovery encountered issues: {}",
                                result.errorMessage
                            );
                        }
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error(
                            "Error during manual discovery",
                            throwable
                        );
                        return null;
                    });

                // Step 6: Show expected results
                LOGGER.info("");
                LOGGER.info("Step 6: Expected results after discovery:");
                showExpectedResults();

                // Step 7: Simulate running system
                LOGGER.info("");
                LOGGER.info(
                    "Step 7: System now running with continuous monitoring..."
                );
                LOGGER.info("In a real F1r3Drive instance:");
                LOGGER.info("  - Token discovery runs in background");
                LOGGER.info(
                    "  - New blockchain tokens are automatically detected"
                );
                LOGGER.info(
                    "  - Corresponding folders are created automatically"
                );
                LOGGER.info(
                    "  - Folders are cleaned up when application shuts down"
                );

                // Wait a bit more for any async operations
                Thread.sleep(3000);

                // Step 8: Cleanup (simulates application shutdown)
                LOGGER.info("");
                LOGGER.info("Step 8: Simulating application shutdown...");
                tokenDiscovery.shutdown();
                LOGGER.info("✓ Token discovery system shutdown complete");
                LOGGER.info("✓ Demo folders will be automatically cleaned up");
            } else {
                LOGGER.error("✗ Failed to initialize token discovery system");
                LOGGER.error(
                    "This could happen if blockchain is not accessible"
                );
            }
        } catch (Exception e) {
            LOGGER.error("Error during startup demo", e);
        }

        LOGGER.info("");
        LOGGER.info("=== Demo Complete ===");
        LOGGER.info("This demonstrates the complete F1r3Drive startup process");
        LOGGER.info(
            "with automatic blockchain token discovery and folder creation."
        );
    }

    /**
     * Creates blockchain client with default configuration
     */
    private F1r3flyBlockchainClient createBlockchainClient() {
        return new F1r3flyBlockchainClient(
            DEFAULT_VALIDATOR_HOST,
            DEFAULT_VALIDATOR_PORT,
            DEFAULT_OBSERVER_HOST,
            DEFAULT_OBSERVER_PORT,
            DEFAULT_MANUAL_PROPOSE
        );
    }

    /**
     * Shows what results to expect after token discovery
     */
    private void showExpectedResults() {
        LOGGER.info("After token discovery completes, you should see:");
        LOGGER.info("");
        LOGGER.info("Directory structure in {}:", DEMO_FOLDER_PATH);
        LOGGER.info("├── wallet_1111Atah...k5r3g/");
        LOGGER.info("│   ├── documents/");
        LOGGER.info("│   ├── photos/");
        LOGGER.info("│   └── projects/");
        LOGGER.info("├── wallet_111127RX...32PiHA/");
        LOGGER.info("│   ├── backup/");
        LOGGER.info("│   └── shared/");
        LOGGER.info("├── wallet_111129p3...ymczH/");
        LOGGER.info("│   ├── temp/");
        LOGGER.info("│   └── archive/");
        LOGGER.info("├── wallet_1111LAd2...BPcvHftP/");
        LOGGER.info("│   └── data/");
        LOGGER.info("└── wallet_1111ocWg...fw3TDS8/");
        LOGGER.info("    └── storage/");
        LOGGER.info("");
        LOGGER.info(
            "Each wallet directory corresponds to a blockchain wallet address."
        );
        LOGGER.info(
            "Each subfolder corresponds to a folder token found in that wallet."
        );
        LOGGER.info(
            "All folders are created automatically without user intervention."
        );
    }

    /**
     * Static method to demonstrate integration with existing F1r3Drive code
     */
    public static void integrateWithF1r3Drive(
        F1r3flyBlockchainClient blockchainClient
    ) {
        LOGGER.info("=== Integration Example ===");
        LOGGER.info("To integrate with existing F1r3Drive code:");
        LOGGER.info("");
        LOGGER.info("1. In F1r3DriveCli.java:");
        LOGGER.info("   - Add AutoStartTokenDiscovery initialization");
        LOGGER.info("   - Add shutdown cleanup");
        LOGGER.info("");
        LOGGER.info("2. In F1r3DriveFuse.java constructor:");
        LOGGER.info(
            "   - Call: tokenDiscovery = AutoStartTokenDiscovery.createSafely(blockchainClient);"
        );
        LOGGER.info("");
        LOGGER.info("3. In cleanup/shutdown methods:");
        LOGGER.info("   - Call: tokenDiscovery.shutdown();");
        LOGGER.info("");
        LOGGER.info("That's all! The system will automatically:");
        LOGGER.info("  ✓ Scan blockchain for existing tokens");
        LOGGER.info("  ✓ Create folders for discovered tokens");
        LOGGER.info("  ✓ Monitor for new tokens (configurable interval)");
        LOGGER.info("  ✓ Clean up folders on application shutdown");

        try {
            // Demonstrate the actual integration
            AutoStartTokenDiscovery discovery =
                AutoStartTokenDiscovery.createSafely(blockchainClient);
            if (discovery != null) {
                LOGGER.info(
                    "✓ Integration successful - token discovery active"
                );
            } else {
                LOGGER.warn(
                    "Integration failed - continuing without token discovery"
                );
            }
        } catch (Exception e) {
            LOGGER.error("Integration error", e);
        }
    }

    /**
     * Shows command line options for controlling token discovery
     */
    public static void showCommandLineOptions() {
        LOGGER.info("=== Command Line Options ===");
        LOGGER.info(
            "F1r3Drive now supports the following token discovery options:"
        );
        LOGGER.info("");
        LOGGER.info("--disable-token-discovery");
        LOGGER.info("  Completely disables automatic token discovery");
        LOGGER.info("");
        LOGGER.info("--token-discovery-interval <minutes>");
        LOGGER.info(
            "  Sets interval for periodic discovery (default: 30 minutes)"
        );
        LOGGER.info("  Set to 0 to disable periodic discovery");
        LOGGER.info("");
        LOGGER.info("--demo-folder-path <path>");
        LOGGER.info(
            "  Sets custom path for demo folders (default: ~/demo-f1r3drive)"
        );
        LOGGER.info("");
        LOGGER.info("Examples:");
        LOGGER.info("  java F1r3DriveCli /mount/point --manual-propose true");
        LOGGER.info("    (token discovery enabled with defaults)");
        LOGGER.info("");
        LOGGER.info(
            "  java F1r3DriveCli /mount/point --manual-propose true --disable-token-discovery"
        );
        LOGGER.info("    (token discovery disabled)");
        LOGGER.info("");
        LOGGER.info(
            "  java F1r3DriveCli /mount/point --manual-propose true --token-discovery-interval 60"
        );
        LOGGER.info("    (token discovery every 60 minutes)");
    }
}
