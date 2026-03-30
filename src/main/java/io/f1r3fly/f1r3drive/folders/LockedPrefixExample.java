package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example showing the locked_ prefix functionality.
 *
 * This demonstrates how:
 * - Locked wallets get "locked_" prefix in folder names
 * - Unlocked wallets have no prefix (clean wallet address)
 * - Only unlocked wallets (no prefix) can modify files
 * - Locked wallets (with prefix) are read-only
 */
public class LockedPrefixExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockedPrefixExample.class);

    /**
     * Demonstrates the locked_ prefix system
     */
    public static void demonstrateLockedPrefix(
        F1r3flyBlockchainClient blockchainClient,
        DeployDispatcher deployDispatcher,
        String baseDirectory
    ) {
        LOGGER.info("=== LOCKED PREFIX DEMONSTRATION ===");

        try {
            PhysicalWalletManager walletManager = new PhysicalWalletManager(
                blockchainClient,
                deployDispatcher,
                baseDirectory
            );

            String testWallet = "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
            String privateKey = "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";

            // === PHASE 1: Create locked wallet (with prefix) ===
            LOGGER.info("--- Phase 1: Creating LOCKED wallet ---");

            LockedPhysicalWallet lockedWallet = walletManager.createLockedWallet(testWallet).get();
            Path lockedPath = lockedWallet.getWalletPath();

            LOGGER.info("✓ Locked wallet created at: {}", lockedPath);
            LOGGER.info("✓ Folder name: {}", lockedPath.getFileName());
            LOGGER.info("✓ Notice the 'locked_' prefix in the folder name");

            // Verify folder structure
            assertTrue(Files.exists(lockedPath), "Locked folder should exist");
            assertTrue(Files.exists(lockedPath.resolve(".locked")), "Lock indicator should exist");
            assertTrue(lockedPath.getFileName().toString().startsWith("locked_"),
                "Folder should have locked_ prefix");

            // === PHASE 2: Test locked wallet restrictions ===
            LOGGER.info("--- Phase 2: Testing locked wallet (READ-ONLY) ---");

            // Read operations work
            boolean canRead = lockedWallet.fileExists("README.md");
            LOGGER.info("✓ Read operation: {}", canRead ? "SUCCESS" : "FAILED");

            // Write operations blocked
            try {
                lockedWallet.createFile("test.txt", "Should fail".getBytes());
                LOGGER.error("✗ ERROR: Write operation should have been blocked!");
            } catch (IllegalStateException e) {
                LOGGER.info("✓ Write operation properly BLOCKED: {}", e.getMessage());
            }

            // === PHASE 3: Unlock wallet (remove prefix) ===
            LOGGER.info("--- Phase 3: Unlocking wallet (removing prefix) ---");

            UnlockedPhysicalWallet unlockedWallet = walletManager.unlockPhysicalWallet(testWallet, privateKey).get();
            Path unlockedPath = unlockedWallet.getWalletPath();

            LOGGER.info("✓ Wallet unlocked!");
            LOGGER.info("✓ Old path (locked): {}", lockedPath);
            LOGGER.info("✓ New path (unlocked): {}", unlockedPath);
            LOGGER.info("✓ Notice: 'locked_' prefix has been REMOVED");

            // Verify folder was renamed
            assertFalse(Files.exists(lockedPath), "Old locked folder should not exist");
            assertTrue(Files.exists(unlockedPath), "New unlocked folder should exist");
            assertTrue(Files.exists(unlockedPath.resolve(".unlocked")), "Unlock indicator should exist");
            assertFalse(unlockedPath.getFileName().toString().startsWith("locked_"),
                "Unlocked folder should NOT have locked_ prefix");

            // === PHASE 4: Test unlocked wallet full access ===
            LOGGER.info("--- Phase 4: Testing unlocked wallet (FULL ACCESS) ---");

            // Read operations still work
            canRead = unlockedWallet.fileExists("README.md");
            LOGGER.info("✓ Read operation: {}", canRead ? "SUCCESS" : "FAILED");

            // Write operations now work
            try {
                unlockedWallet.createFile("success.txt", "Unlocked wallet can write!".getBytes());
                LOGGER.info("✓ Write operation: SUCCESS");

                unlockedWallet.createDirectory("test_folder");
                LOGGER.info("✓ Directory creation: SUCCESS");

                unlockedWallet.updateTokenBalance("REV", 1000L);
                LOGGER.info("✓ Token update: SUCCESS");
            } catch (Exception e) {
                LOGGER.error("✗ Unexpected error in unlocked wallet: {}", e.getMessage());
            }

            // === PHASE 5: Demonstrate file system structure ===
            LOGGER.info("--- Phase 5: File system structure demonstration ---");

            LOGGER.info("=== FOLDER STRUCTURE COMPARISON ===");
            LOGGER.info("LOCKED wallet folder name: locked_{}...{}",
                testWallet.substring(0, 8), testWallet.substring(testWallet.length() - 8));
            LOGGER.info("UNLOCKED wallet folder name: {}...{}",
                testWallet.substring(0, 8), testWallet.substring(testWallet.length() - 8));
            LOGGER.info("");
            LOGGER.info("LOCKED wallet path: {}/locked_{}", baseDirectory, testWallet);
            LOGGER.info("UNLOCKED wallet path: {}/{}", baseDirectory, testWallet);

            walletManager.shutdown();

        } catch (Exception e) {
            LOGGER.error("Example failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Shows multiple wallets with different states
     */
    public static void demonstrateMultipleWallets(
        F1r3flyBlockchainClient blockchainClient,
        DeployDispatcher deployDispatcher,
        String baseDirectory
    ) {
        LOGGER.info("=== MULTIPLE WALLETS DEMONSTRATION ===");

        try {
            PhysicalWalletManager walletManager = new PhysicalWalletManager(
                blockchainClient,
                deployDispatcher,
                baseDirectory
            );

            String[] wallets = {
                "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
                "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA",
                "11113XyZaBcDeFgHiJkLmNoPqRsTuVwXyZaBcDeFgHiJkLmNoPq"
            };

            String privateKey = "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";

            // Create all wallets as locked first
            LOGGER.info("--- Creating all wallets in LOCKED state ---");
            for (String wallet : wallets) {
                LockedPhysicalWallet locked = walletManager.createLockedWallet(wallet).get();
                Path path = locked.getWalletPath();
                LOGGER.info("✓ Created: {} -> {}", wallet, path.getFileName());
                assertTrue(path.getFileName().toString().startsWith("locked_"),
                    "All wallets should start with locked_ prefix");
            }

            // Unlock only the first wallet
            LOGGER.info("--- Unlocking ONLY the first wallet ---");
            UnlockedPhysicalWallet unlocked = walletManager.unlockPhysicalWallet(wallets[0], privateKey).get();
            Path unlockedPath = unlocked.getWalletPath();
            LOGGER.info("✓ Unlocked: {} -> {}", wallets[0], unlockedPath.getFileName());
            assertFalse(unlockedPath.getFileName().toString().startsWith("locked_"),
                "Unlocked wallet should not have locked_ prefix");

            // Show final state
            LOGGER.info("--- Final file system state ---");
            LOGGER.info("UNLOCKED (can write): {}", unlockedPath.getFileName());

            for (int i = 1; i < wallets.length; i++) {
                PhysicalWallet stillLocked = walletManager.getWallet(wallets[i]);
                Path lockedPath = stillLocked.getWalletPath();
                LOGGER.info("LOCKED (read-only):   {}", lockedPath.getFileName());
                assertTrue(lockedPath.getFileName().toString().startsWith("locked_"),
                    "Locked wallets should keep locked_ prefix");
            }

            // Test access control
            LOGGER.info("--- Testing access control ---");

            // Unlocked wallet can write
            try {
                unlocked.createFile("authorized.txt", "Success!".getBytes());
                LOGGER.info("✓ Unlocked wallet (no prefix): Write SUCCESS");
            } catch (Exception e) {
                LOGGER.error("✗ Unlocked wallet should be able to write");
            }

            // Locked wallets cannot write
            for (int i = 1; i < wallets.length; i++) {
                PhysicalWallet locked = walletManager.getWallet(wallets[i]);
                try {
                    locked.createFile("blocked.txt", "Should fail".getBytes());
                    LOGGER.error("✗ Locked wallet should not be able to write");
                } catch (IllegalStateException e) {
                    LOGGER.info("✓ Locked wallet (locked_ prefix): Write BLOCKED");
                }
            }

            walletManager.shutdown();

        } catch (Exception e) {
            LOGGER.error("Multiple wallets example failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method for assertions
     */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Entry point for standalone testing
     */
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

        LOGGER.info("=== LOCKED PREFIX SYSTEM DEMONSTRATION ===");
        LOGGER.info("");
        LOGGER.info("This example shows how the system works:");
        LOGGER.info("1. Locked wallets get 'locked_' prefix in folder names");
        LOGGER.info("2. Unlocked wallets have no prefix (clean address)");
        LOGGER.info("3. Only unlocked folders (no prefix) can modify files");
        LOGGER.info("4. Locked folders (with prefix) are read-only");
        LOGGER.info("");

        // For actual demo, you need real blockchain client and deploy dispatcher
        LOGGER.info("To run this demo:");
        LOGGER.info("- Initialize F1r3flyBlockchainClient");
        LOGGER.info("- Initialize DeployDispatcher");
        LOGGER.info("- Call demonstrateLockedPrefix(client, dispatcher, \"/your/base/dir\")");
        LOGGER.info("");
        LOGGER.info("Example folder structure after running:");
        LOGGER.info("  /base/dir/");
        LOGGER.info("  ├── locked_111127RX...PiHA/     # Locked wallet (read-only)");
        LOGGER.info("  ├── locked_11112QbU...PiHA/     # Locked wallet (read-only)");
        LOGGER.info("  └── 111127RX...PiHA/            # Unlocked wallet (full access)");
        LOGGER.info("");
        LOGGER.info("Notice: Unlocked wallet has NO prefix!");
    }
}
