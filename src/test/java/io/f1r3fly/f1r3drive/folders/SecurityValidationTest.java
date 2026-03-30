package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security validation test that verifies the specific REV address scenario:
 * - Only the specified REV address with private key can modify files
 * - All other wallets (discovered from genesis) remain locked
 * - System enforces proper access control
 */
class SecurityValidationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityValidationTest.class);

    @TempDir
    Path tempDir;

    @Mock
    private F1r3flyBlockchainClient mockBlockchainClient;

    @Mock
    private DeployDispatcher mockDeployDispatcher;

    private PhysicalWalletManager walletManager;
    private BlockchainFolderIntegration folderIntegration;

    // Test scenario: Only this REV address should be unlocked
    private final String targetRevAddress = "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private final String privateKey = "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";

    // Other addresses that should remain locked
    private final String[] otherAddresses = {
        "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA",
        "11113XyZaBcDeFgHiJkLmNoPqRsTuVwXyZaBcDeFgHiJkLmNoPq",
        "11114AbCdEfGhIjKlMnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrSt"
    };

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create wallet manager and integration
        walletManager = new PhysicalWalletManager(
            mockBlockchainClient,
            mockDeployDispatcher,
            tempDir.toString()
        );

        folderIntegration = new BlockchainFolderIntegration(
            mockBlockchainClient,
            tempDir.toString()
        );
    }

    @AfterEach
    void tearDown() {
        if (walletManager != null) {
            walletManager.shutdown();
        }
    }

    @Test
    void testTargetWalletCanBeUnlocked() throws Exception {
        LOGGER.info("=== Testing Target Wallet Unlock Scenario ===");

        // Step 1: Create locked wallet for target address
        CompletableFuture<LockedPhysicalWallet> lockedFuture =
            walletManager.createLockedWallet(targetRevAddress);
        LockedPhysicalWallet lockedWallet = lockedFuture.get();

        // Verify it starts locked
        assertTrue(lockedWallet.isLocked());
        assertFalse(lockedWallet.isUnlocked());
        LOGGER.info("✓ Target wallet created in LOCKED state");

        // Step 2: Verify write operations are blocked when locked
        assertThrows(IllegalStateException.class, () -> {
            lockedWallet.createFile("test.txt", "content".getBytes());
        });
        LOGGER.info("✓ Target wallet blocks write operations when locked");

        // Step 3: Unlock the wallet with private key
        CompletableFuture<UnlockedPhysicalWallet> unlockedFuture =
            walletManager.unlockPhysicalWallet(targetRevAddress, privateKey);
        UnlockedPhysicalWallet unlockedWallet = unlockedFuture.get();

        // Verify it's now unlocked
        assertFalse(unlockedWallet.isLocked());
        assertTrue(unlockedWallet.isUnlocked());
        LOGGER.info("✓ Target wallet successfully unlocked");

        // Step 4: Verify write operations now work
        assertDoesNotThrow(() -> {
            unlockedWallet.createFile("test.txt", "Hello World".getBytes());
        });

        assertDoesNotThrow(() -> {
            unlockedWallet.createDirectory("test_folder");
        });

        assertDoesNotThrow(() -> {
            unlockedWallet.updateTokenBalance("REV", 1000L);
        });

        LOGGER.info("✓ Target wallet allows all operations when unlocked");
    }

    @Test
    void testOtherWalletsRemainLocked() throws Exception {
        LOGGER.info("=== Testing Other Wallets Remain Locked ===");

        // Create locked wallets for other addresses
        for (String address : otherAddresses) {
            CompletableFuture<LockedPhysicalWallet> future =
                walletManager.createLockedWallet(address);
            LockedPhysicalWallet wallet = future.get();

            // Verify they are locked
            assertTrue(wallet.isLocked());
            assertFalse(wallet.isUnlocked());
            LOGGER.info("✓ Wallet {} is LOCKED", address);

            // Verify write operations are blocked
            assertThrows(IllegalStateException.class, () -> {
                wallet.createFile("unauthorized.txt", "hack attempt".getBytes());
            });

            assertThrows(IllegalStateException.class, () -> {
                wallet.createDirectory("unauthorized_dir");
            });

            assertThrows(IllegalStateException.class, () -> {
                wallet.updateTokenBalance("REV", 999999L);
            });

            LOGGER.info("✓ Wallet {} blocks all write operations", address);

            // Verify read operations still work
            assertDoesNotThrow(() -> {
                wallet.fileExists("README.md");
            });

            assertDoesNotThrow(() -> {
                wallet.listDirectory("");
            });

            LOGGER.info("✓ Wallet {} allows read operations", address);
        }
    }

    @Test
    void testManagerValidationEnforcement() throws Exception {
        LOGGER.info("=== Testing Manager-Level Security Enforcement ===");

        // Create target wallet (unlocked) and other wallets (locked)
        CompletableFuture<LockedPhysicalWallet> targetLockedFuture =
            walletManager.createLockedWallet(targetRevAddress);
        targetLockedFuture.get();

        // Unlock target wallet
        CompletableFuture<UnlockedPhysicalWallet> targetUnlockedFuture =
            walletManager.unlockPhysicalWallet(targetRevAddress, privateKey);
        targetUnlockedFuture.get();

        // Create locked wallets for others
        for (String address : otherAddresses) {
            walletManager.createLockedWallet(address).get();
        }

        // Test validation for target wallet (should pass)
        assertDoesNotThrow(() -> {
            walletManager.validateWalletOperation(targetRevAddress, "create_file");
        });
        LOGGER.info("✓ Target wallet passes write operation validation");

        // Test validation for other wallets (should fail)
        for (String address : otherAddresses) {
            assertThrows(IllegalStateException.class, () -> {
                walletManager.validateWalletOperation(address, "create_file");
            });
            LOGGER.info("✓ Wallet {} fails write operation validation", address);

            // But read operations should pass
            assertDoesNotThrow(() -> {
                walletManager.validateWalletOperation(address, "read_file");
            });
            LOGGER.info("✓ Wallet {} passes read operation validation", address);
        }
    }

    @Test
    void testIntegrationLayerSecurity() throws Exception {
        LOGGER.info("=== Testing Integration Layer Security ===");

        // Setup target wallet (unlocked) and other wallets (locked)
        walletManager.createLockedWallet(targetRevAddress).get();
        walletManager.unlockPhysicalWallet(targetRevAddress, privateKey).get();

        for (String address : otherAddresses) {
            walletManager.createLockedWallet(address).get();
        }

        // Test through integration layer
        // Target wallet should allow write operations
        CompletableFuture<Void> targetWriteFuture = folderIntegration.executeWalletFileOperation(
            targetRevAddress,
            "create_file",
            "authorized.txt",
            "This should work".getBytes()
        );

        assertDoesNotThrow(() -> targetWriteFuture.get());
        LOGGER.info("✓ Target wallet allows operations through integration layer");

        // Other wallets should block write operations
        for (String address : otherAddresses) {
            CompletableFuture<Void> blockedWriteFuture = folderIntegration.executeWalletFileOperation(
                address,
                "create_file",
                "blocked.txt",
                "This should fail".getBytes()
            );

            assertThrows(Exception.class, () -> blockedWriteFuture.get());
            LOGGER.info("✓ Wallet {} blocks operations through integration layer", address);
        }
    }

    @Test
    void testFileSystemAccessControl() throws Exception {
        LOGGER.info("=== Testing File System Access Control ===");

        // Create and setup wallets
        walletManager.createLockedWallet(targetRevAddress).get();
        UnlockedPhysicalWallet targetWallet = walletManager.unlockPhysicalWallet(targetRevAddress, privateKey).get();

        LockedPhysicalWallet otherWallet = walletManager.createLockedWallet(otherAddresses[0]).get();

        // Test actual file system operations
        Path targetWalletDir = targetWallet.getWalletPath();
        Path otherWalletDir = otherWallet.getWalletPath();

        // Target wallet should be able to create files
        targetWallet.createFile("success.txt", "Target wallet file".getBytes());
        assertTrue(Files.exists(targetWalletDir.resolve("success.txt")));
        LOGGER.info("✓ Target wallet successfully created file in file system");

        // Other wallet should NOT be able to create files
        assertThrows(IllegalStateException.class, () -> {
            otherWallet.createFile("blocked.txt", "Should not work".getBytes());
        });
        assertFalse(Files.exists(otherWalletDir.resolve("blocked.txt")));
        LOGGER.info("✓ Other wallet properly blocked from file creation");

        // Verify folder structure shows proper lock status
        assertTrue(Files.exists(targetWalletDir.resolve(".unlocked")));
        assertFalse(Files.exists(targetWalletDir.resolve(".locked")));
        LOGGER.info("✓ Target wallet shows UNLOCKED status in file system");

        assertTrue(Files.exists(otherWalletDir.resolve(".locked")));
        assertFalse(Files.exists(otherWalletDir.resolve(".unlocked")));
        LOGGER.info("✓ Other wallet shows LOCKED status in file system");
    }

    @Test
    void testComprehensiveSecurityScenario() throws Exception {
        LOGGER.info("=== Testing Comprehensive Security Scenario ===");
        LOGGER.info("This test simulates the exact command scenario:");
        LOGGER.info("Target REV: {}", targetRevAddress);
        LOGGER.info("Private Key: {}...{}", privateKey.substring(0, 8), privateKey.substring(privateKey.length() - 8));

        // Step 1: Create locked wallet for target address
        LockedPhysicalWallet targetLocked = walletManager.createLockedWallet(targetRevAddress).get();
        assertTrue(targetLocked.isLocked());
        LOGGER.info("✓ Step 1: Target wallet created in LOCKED state");

        // Step 2: Create locked wallets for other discovered addresses
        for (String address : otherAddresses) {
            LockedPhysicalWallet otherLocked = walletManager.createLockedWallet(address).get();
            assertTrue(otherLocked.isLocked());
        }
        LOGGER.info("✓ Step 2: Other wallets created in LOCKED state");

        // Step 3: Unlock ONLY the target wallet
        UnlockedPhysicalWallet targetUnlocked = walletManager.unlockPhysicalWallet(targetRevAddress, privateKey).get();
        assertTrue(targetUnlocked.isUnlocked());
        LOGGER.info("✓ Step 3: Target wallet unlocked with private key");

        // Step 4: Verify security enforcement
        // Target wallet can do everything
        assertDoesNotThrow(() -> targetUnlocked.createFile("authorized_file.txt", "OK".getBytes()));
        assertDoesNotThrow(() -> targetUnlocked.createDirectory("authorized_dir"));
        assertDoesNotThrow(() -> targetUnlocked.updateTokenBalance("REV", 100L));
        LOGGER.info("✓ Step 4a: Target wallet has full access");

        // Other wallets cannot do write operations
        for (String address : otherAddresses) {
            PhysicalWallet otherWallet = walletManager.getWallet(address);
            assertTrue(otherWallet.isLocked());

            assertThrows(IllegalStateException.class, () ->
                otherWallet.createFile("unauthorized.txt", "BLOCKED".getBytes()));
            assertThrows(IllegalStateException.class, () ->
                otherWallet.createDirectory("unauthorized_dir"));
            assertThrows(IllegalStateException.class, () ->
                otherWallet.updateTokenBalance("REV", 999999L));

            // But can still read
            assertDoesNotThrow(() -> otherWallet.fileExists("README.md"));
        }
        LOGGER.info("✓ Step 4b: Other wallets properly restricted");

        // Step 5: Verify manager-level enforcement
        assertTrue(walletManager.isWalletUnlocked(targetRevAddress));
        for (String address : otherAddresses) {
            assertTrue(walletManager.isWalletLocked(address));
        }
        LOGGER.info("✓ Step 5: Manager correctly tracks wallet states");

        LOGGER.info("=== SECURITY VALIDATION PASSED ===");
        LOGGER.info("✓ Only target REV address {} can modify files", targetRevAddress);
        LOGGER.info("✓ All other addresses remain locked and read-only");
        LOGGER.info("✓ System enforces proper access control at all levels");
    }
}
