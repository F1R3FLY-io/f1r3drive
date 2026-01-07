package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Working example showing how locked wallets prevent operations while unlocked wallets allow them.
 * This demonstrates the core functionality where locked wallets are read-only.
 */
public class WalletLockExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        WalletLockExample.class
    );

    /**
     * Tests wallet operations with a real wallet manager
     */
    public static void testWalletOperations(
        F1r3flyBlockchainClient blockchainClient,
        DeployDispatcher deployDispatcher,
        String testWalletAddress,
        String privateKey
    ) {
        LOGGER.info("=== Testing Wallet Lock/Unlock Operations ===");

        try {
            // Create wallet manager
            String baseDir =
                System.getProperty("java.io.tmpdir") + "/f1r3drive_test";
            Files.createDirectories(Paths.get(baseDir));

            PhysicalWalletManager walletManager = new PhysicalWalletManager(
                blockchainClient,
                deployDispatcher,
                baseDir
            );

            // Create locked wallet and test restrictions
            LOGGER.info("--- Testing Locked Wallet ---");
            var lockedFuture = walletManager.createLockedWallet(
                testWalletAddress
            );
            LockedPhysicalWallet lockedWallet = lockedFuture.get();

            LOGGER.info(
                "Created locked wallet: {}",
                lockedWallet.getWalletStatus()
            );

            // Test read operations (should work)
            testReadOperations(lockedWallet);

            // Test write operations (should be blocked)
            testWriteOperations(lockedWallet);

            // Unlock wallet and test full access
            LOGGER.info("--- Testing Unlocked Wallet ---");
            var unlockedFuture = walletManager.unlockPhysicalWallet(
                testWalletAddress,
                privateKey
            );
            UnlockedPhysicalWallet unlockedWallet = unlockedFuture.get();

            LOGGER.info(
                "Unlocked wallet: {}",
                unlockedWallet.getWalletStatus()
            );

            // Test read operations (should work)
            testReadOperations(unlockedWallet);

            // Test write operations (should work)
            testWriteOperations(unlockedWallet);

            // Test manager validation
            LOGGER.info("--- Testing Manager Validation ---");
            testManagerValidation(walletManager, testWalletAddress);

            walletManager.shutdown();
            LOGGER.info("=== Test Complete ===");
        } catch (Exception e) {
            LOGGER.error("Test failed: {}", e.getMessage(), e);
        }
    }

    private static void testReadOperations(PhysicalWallet wallet) {
        LOGGER.info(
            "Testing read operations on {} wallet...",
            wallet.getWalletStatus()
        );

        try {
            // Test file exists
            boolean exists = wallet.fileExists("README.md");
            LOGGER.info("✓ File exists check: {}", exists);

            // Test directory listing
            String[] contents = wallet.listDirectory("");
            LOGGER.info("✓ Directory listing: {} items", contents.length);

            // Test read file if it exists
            if (exists) {
                byte[] content = wallet.readFile("README.md");
                LOGGER.info("✓ File read: {} bytes", content.length);
            }
        } catch (Exception e) {
            LOGGER.error("✗ Read operation failed: {}", e.getMessage());
        }
    }

    private static void testWriteOperations(PhysicalWallet wallet) {
        LOGGER.info(
            "Testing write operations on {} wallet...",
            wallet.getWalletStatus()
        );

        // Test file creation
        try {
            wallet.createFile("test.txt", "Hello World".getBytes());
            LOGGER.info("✓ File creation: SUCCESS (wallet is unlocked)");
        } catch (IllegalStateException e) {
            LOGGER.info(
                "✗ File creation: BLOCKED (wallet is locked) - {}",
                e.getMessage()
            );
        } catch (Exception e) {
            LOGGER.error("✗ File creation: ERROR - {}", e.getMessage());
        }

        // Test directory creation
        try {
            wallet.createDirectory("test_dir");
            LOGGER.info("✓ Directory creation: SUCCESS (wallet is unlocked)");
        } catch (IllegalStateException e) {
            LOGGER.info(
                "✗ Directory creation: BLOCKED (wallet is locked) - {}",
                e.getMessage()
            );
        } catch (Exception e) {
            LOGGER.error("✗ Directory creation: ERROR - {}", e.getMessage());
        }

        // Test token update
        try {
            wallet.updateTokenBalance("REV", 1000L);
            LOGGER.info("✓ Token update: SUCCESS (wallet is unlocked)");
        } catch (IllegalStateException e) {
            LOGGER.info(
                "✗ Token update: BLOCKED (wallet is locked) - {}",
                e.getMessage()
            );
        } catch (Exception e) {
            LOGGER.error("✗ Token update: ERROR - {}", e.getMessage());
        }
    }

    private static void testManagerValidation(
        PhysicalWalletManager manager,
        String walletAddress
    ) {
        LOGGER.info("Testing manager validation for wallet operations...");

        String[] writeOps = {
            "create_file",
            "write_file",
            "delete_file",
            "create_directory",
            "update_token",
        };
        String[] readOps = {
            "read_file",
            "list_directory",
            "file_exists",
            "is_directory",
        };

        // Test write operations validation
        for (String op : writeOps) {
            try {
                manager.validateWalletOperation(walletAddress, op);
                LOGGER.info(
                    "✓ {} - validation passed (wallet is unlocked)",
                    op
                );
            } catch (IllegalStateException e) {
                LOGGER.info("✗ {} - validation blocked (wallet is locked)", op);
            }
        }

        // Test read operations validation
        for (String op : readOps) {
            try {
                manager.validateWalletOperation(walletAddress, op);
                LOGGER.info("✓ {} - validation passed (always allowed)", op);
            } catch (IllegalStateException e) {
                LOGGER.warn(
                    "✗ {} - unexpected validation failure: {}",
                    op,
                    e.getMessage()
                );
            }
        }
    }

    /**
     * Convenience method for testing with dummy data
     */
    public static void quickTest() {
        LOGGER.info("Running quick test with mock data...");

        // This would need real blockchain client in production
        F1r3flyBlockchainClient mockClient = null;
        DeployDispatcher mockDispatcher = null;

        String testWallet = "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA";
        String testKey =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        if (mockClient != null && mockDispatcher != null) {
            testWalletOperations(
                mockClient,
                mockDispatcher,
                testWallet,
                testKey
            );
        } else {
            LOGGER.warn(
                "Mock test requires real blockchain client and dispatcher"
            );
            LOGGER.info(
                "Use testWalletOperations() with real instances for actual testing"
            );
        }
    }
}
