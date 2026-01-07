package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple usage example showing how the wallet lock/unlock system works.
 * This demonstrates the core functionality where:
 * - Locked wallets are READ-ONLY (can read files, cannot create/modify/delete)
 * - Unlocked wallets have FULL ACCESS (can do all operations)
 */
public class WalletUsageExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletUsageExample.class);

    /**
     * Main example showing wallet operations
     */
    public static void runExample(
        F1r3flyBlockchainClient blockchainClient,
        DeployDispatcher deployDispatcher
    ) {
        LOGGER.info("=== WALLET LOCK/UNLOCK EXAMPLE ===");

        try {
            // Setup
            String baseDir = System.getProperty("java.io.tmpdir") + "/f1r3drive_example";
            Files.createDirectories(Paths.get(baseDir));

            PhysicalWalletManager walletManager = new PhysicalWalletManager(
                blockchainClient,
                deployDispatcher,
                baseDir
            );

            String testWallet = "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA";
            String privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

            // === PHASE 1: LOCKED WALLET ===
            LOGGER.info("--- Creating LOCKED wallet ---");

            var lockedFuture = walletManager.createLockedWallet(testWallet);
            LockedPhysicalWallet lockedWallet = lockedFuture.get();

            LOGGER.info("✓ Wallet created: {}", lockedWallet.getWalletStatus());

            // What works on LOCKED wallets:
            LOGGER.info("--- Testing READ operations (should work) ---");

            boolean exists = lockedWallet.fileExists("README.md");
            LOGGER.info("✓ Check file exists: {}", exists);

            String[] files = lockedWallet.listDirectory("");
            LOGGER.info("✓ List directory: {} files found", files.length);

            if (exists) {
                byte[] content = lockedWallet.readFile("README.md");
                LOGGER.info("✓ Read file: {} bytes", content.length);
            }

            // What doesn't work on LOCKED wallets:
            LOGGER.info("--- Testing WRITE operations (should be blocked) ---");

            try {
                lockedWallet.createFile("test.txt", "Hello".getBytes());
                LOGGER.error("✗ ERROR: File creation should have been blocked!");
            } catch (IllegalStateException e) {
                LOGGER.info("✓ File creation blocked: {}", e.getMessage());
            }

            try {
                lockedWallet.createDirectory("test_dir");
                LOGGER.error("✗ ERROR: Directory creation should have been blocked!");
            } catch (IllegalStateException e) {
                LOGGER.info("✓ Directory creation blocked: {}", e.getMessage());
            }

            try {
                lockedWallet.updateTokenBalance("REV", 1000);
                LOGGER.error("✗ ERROR: Token update should have been blocked!");
            } catch (IllegalStateException e) {
                LOGGER.info("✓ Token update blocked: {}", e.getMessage());
            }

            // === PHASE 2: UNLOCK WALLET ===
            LOGGER.info("--- Unlocking wallet ---");

            var unlockedFuture = walletManager.unlockPhysicalWallet(testWallet, privateKey);
            UnlockedPhysicalWallet unlockedWallet = unlockedFuture.get();

            LOGGER.info("✓ Wallet unlocked: {}", unlockedWallet.getWalletStatus());

            // What works on UNLOCKED wallets (everything):
            LOGGER.info("--- Testing operations on UNLOCKED wallet ---");

            // Read operations still work
            exists = unlockedWallet.fileExists("README.md");
            LOGGER.info("✓ Check file exists: {}", exists);

            // Write operations now work too
            unlockedWallet.createFile("test.txt", "Hello World!".getBytes());
            LOGGER.info("✓ File created successfully");

            unlockedWallet.createDirectory("test_dir");
            LOGGER.info("✓ Directory created successfully");

            unlockedWallet.updateTokenBalance("REV", 1000);
            LOGGER.info("✓ Token balance updated successfully");

            // === PHASE 3: MANAGER VALIDATION ===
            LOGGER.info("--- Testing manager-level validation ---");

            // This should work (wallet is unlocked)
            try {
                walletManager.validateWalletOperation(testWallet, "create_file");
                LOGGER.info("✓ Write operation validation passed");
            } catch (IllegalStateException e) {
                LOGGER.error("✗ Unexpected: validation should pass for unlocked wallet");
            }

            // This always works (read operations)
            try {
                walletManager.validateWalletOperation(testWallet, "read_file");
                LOGGER.info("✓ Read operation validation passed");
            } catch (IllegalStateException e) {
                LOGGER.error("✗ Unexpected: read operations should always work");
            }

            // Cleanup
            walletManager.shutdown();
            LOGGER.info("=== EXAMPLE COMPLETED SUCCESSFULLY ===");

        } catch (Exception e) {
            LOGGER.error("Example failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Demonstration of validation using BlockchainFolderIntegration
     */
    public static void runIntegrationExample(BlockchainFolderIntegration integration) {
        LOGGER.info("=== INTEGRATION VALIDATION EXAMPLE ===");

        String testWallet = "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA";

        try {
            // Test validation through integration layer
            LOGGER.info("--- Testing integration validation ---");

            // Try write operation (might be blocked if wallet is locked)
            try {
                integration.validateWalletOperation(testWallet, "create_file");
                LOGGER.info("✓ Write operation allowed (wallet is unlocked)");
            } catch (IllegalStateException e) {
                LOGGER.info("⚠ Write operation blocked (wallet is locked): {}", e.getMessage());
            }

            // Try read operation (should always work)
            try {
                integration.validateWalletOperation(testWallet, "read_file");
                LOGGER.info("✓ Read operation allowed");
            } catch (IllegalStateException e) {
                LOGGER.error("✗ Unexpected: read operations should always work");
            }

            // Example of executing operations through integration
            LOGGER.info("--- Testing actual file operations ---");

            var readFuture = integration.executeWalletReadOperation(
                testWallet,
                "file_exists",
                "README.md"
            );

            readFuture.thenAccept(result -> {
                LOGGER.info("✓ File exists check completed: {}", result);
            }).exceptionally(throwable -> {
                LOGGER.warn("File operation failed: {}", throwable.getMessage());
                return null;
            });

            // Example of write operation (will fail if wallet is locked)
            var writeFuture = integration.executeWalletFileOperation(
                testWallet,
                "create_file",
                "example.txt",
                "Example content".getBytes()
            );

            writeFuture.thenAccept(result -> {
                LOGGER.info("✓ File creation completed successfully");
            }).exceptionally(throwable -> {
                LOGGER.info("⚠ File creation failed (wallet might be locked): {}", throwable.getMessage());
                return null;
            });

            LOGGER.info("=== INTEGRATION EXAMPLE COMPLETED ===");

        } catch (Exception e) {
            LOGGER.error("Integration example failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Quick demonstration of the key concepts
     */
    public static void showConcepts() {
        LOGGER.info("=== WALLET SYSTEM CONCEPTS ===");
        LOGGER.info("");
        LOGGER.info("LOCKED WALLETS:");
        LOGGER.info("  ✓ Can read files and directories");
        LOGGER.info("  ✓ Can check if files exist");
        LOGGER.info("  ✓ Can list directory contents");
        LOGGER.info("  ✗ Cannot create/modify/delete files");
        LOGGER.info("  ✗ Cannot create/delete directories");
        LOGGER.info("  ✗ Cannot update token balances");
        LOGGER.info("  ✗ Cannot execute blockchain transactions");
        LOGGER.info("");
        LOGGER.info("UNLOCKED WALLETS:");
        LOGGER.info("  ✓ Can do EVERYTHING locked wallets can do");
        LOGGER.info("  ✓ PLUS can create/modify/delete files");
        LOGGER.info("  ✓ PLUS can create/delete directories");
        LOGGER.info("  ✓ PLUS can update token balances");
        LOGGER.info("  ✓ PLUS can execute blockchain transactions");
        LOGGER.info("");
        LOGGER.info("USAGE:");
        LOGGER.info("  1. Create locked wallet: walletManager.createLockedWallet(address)");
        LOGGER.info("  2. Unlock wallet: walletManager.unlockPhysicalWallet(address, privateKey)");
        LOGGER.info("  3. All operations check wallet state automatically");
        LOGGER.info("  4. IllegalStateException thrown for blocked operations");
        LOGGER.info("");
        LOGGER.info("================================");
    }

    /**
     * Entry point for standalone testing
     */
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

        LOGGER.info("Starting Wallet Usage Example...");

        // Show concepts first
        showConcepts();

        // For a real example, you would initialize blockchain client and deploy dispatcher
        // runExample(realBlockchainClient, realDeployDispatcher);

        LOGGER.info("Example requires real blockchain client and deploy dispatcher to run");
        LOGGER.info("Use runExample() method with properly initialized instances");
    }
}
