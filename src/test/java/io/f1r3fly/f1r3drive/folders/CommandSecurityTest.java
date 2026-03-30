package io.f1r3fly.f1r3drive.folders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test that exactly simulates the command scenario:
 * java -jar build/libs/f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
 *   --cipher-key-path ~/cipher.key \
 *   --validator-host localhost --validator-port 40402 \
 *   --observer-host localhost --observer-port 40442 \
 *   --manual-propose=true \
 *   --rev-address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
 *   --private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9
 *
 * EXPECTED SECURITY BEHAVIOR:
 * - ONLY the specified REV address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA can modify files
 * - ALL OTHER wallet addresses discovered from genesis block remain LOCKED (read-only)
 */
class CommandSecurityTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        CommandSecurityTest.class
    );

    @TempDir
    Path tempDir;

    @Mock
    private F1r3flyBlockchainClient mockBlockchainClient;

    @Mock
    private DeployDispatcher mockDeployDispatcher;

    private PhysicalWalletManager walletManager;
    private BlockchainFolderIntegration folderIntegration;

    // EXACT VALUES FROM THE COMMAND
    private final String TARGET_REV_ADDRESS =
        "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private final String PRIVATE_KEY =
        "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";
    private final String MOUNT_POINT = tempDir != null
        ? tempDir.toString()
        : "/tmp/demo-f1r3drive";

    // Simulated other wallets that would be discovered from genesis block
    private final String[] OTHER_GENESIS_WALLETS = {
        "11112QbUHKQGxQNNfzHN6oJ9JJLMnJJHgLCzjQhmqiR32PiHA",
        "11113XyZaBcDeFgHiJkLmNoPqRsTuVwXyZaBcDeFgHiJkLmNoPq",
        "11114AbCdEfGhIjKlMnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrSt",
        "11115MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWxYzAbCdE",
    };

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        walletManager = new PhysicalWalletManager(
            mockBlockchainClient,
            mockDeployDispatcher,
            tempDir.toString()
        );

        folderIntegration = new BlockchainFolderIntegration(
            mockBlockchainClient,
            tempDir.toString()
        );

        LOGGER.info("=== SIMULATING EXACT COMMAND SCENARIO ===");
        LOGGER.info("Target REV Address: {}", TARGET_REV_ADDRESS);
        LOGGER.info(
            "Private Key: {}...{}",
            PRIVATE_KEY.substring(0, 8),
            PRIVATE_KEY.substring(PRIVATE_KEY.length() - 8)
        );
        LOGGER.info("Mount Point: {}", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (walletManager != null) {
            walletManager.shutdown();
        }
    }

    @Test
    void testExactCommandSecurityBehavior() throws Exception {
        LOGGER.info("=== TESTING EXACT COMMAND SECURITY BEHAVIOR ===");

        // STEP 1: Simulate discovery and creation for the specific REV address
        LOGGER.info(
            "Step 1: Creating wallet for specified REV address (starts LOCKED)"
        );

        CompletableFuture<LockedPhysicalWallet> lockedFuture =
            walletManager.createLockedWallet(TARGET_REV_ADDRESS);
        LockedPhysicalWallet targetLocked = lockedFuture.get();

        assertTrue(targetLocked.isLocked());
        assertFalse(targetLocked.isUnlocked());
        LOGGER.info(
            "✓ Target wallet {} created in LOCKED state",
            TARGET_REV_ADDRESS
        );

        // Verify file structure exists with locked_ prefix
        Path targetWalletDir = targetLocked.getWalletPath();
        assertTrue(Files.exists(targetWalletDir));
        assertTrue(Files.exists(targetWalletDir.resolve(".locked")));
        assertTrue(Files.exists(targetWalletDir.resolve("README.md")));
        assertTrue(
            targetWalletDir.getFileName().toString().startsWith("locked_")
        );
        LOGGER.info(
            "✓ Target wallet folder structure created at: {}",
            targetWalletDir
        );
        LOGGER.info(
            "✓ Folder name has 'locked_' prefix: {}",
            targetWalletDir.getFileName()
        );

        // STEP 2: Verify target wallet is initially locked (blocks write operations)
        LOGGER.info(
            "Step 2: Verifying initial LOCKED state blocks write operations"
        );

        assertThrows(IllegalStateException.class, () -> {
            targetLocked.createFile("test.txt", "Should fail".getBytes());
        });

        assertThrows(IllegalStateException.class, () -> {
            targetLocked.updateTokenBalance("REV", 1000L);
        });

        LOGGER.info(
            "✓ Target wallet properly blocks write operations when locked"
        );

        // But read operations work
        assertDoesNotThrow(() -> {
            targetLocked.fileExists("README.md");
        });
        LOGGER.info("✓ Target wallet allows read operations when locked");

        // STEP 3: Unlock the target wallet with the provided private key
        LOGGER.info("Step 3: Unlocking target wallet with private key");

        CompletableFuture<UnlockedPhysicalWallet> unlockedFuture =
            walletManager.unlockPhysicalWallet(TARGET_REV_ADDRESS, PRIVATE_KEY);
        UnlockedPhysicalWallet targetUnlocked = unlockedFuture.get();

        assertFalse(targetUnlocked.isLocked());
        assertTrue(targetUnlocked.isUnlocked());
        LOGGER.info(
            "✓ Target wallet {} successfully UNLOCKED",
            TARGET_REV_ADDRESS
        );

        // Verify unlock changes file structure
        Path newTargetWalletDir = targetUnlocked.getWalletPath();
        assertTrue(Files.exists(newTargetWalletDir.resolve(".unlocked")));
        assertFalse(Files.exists(newTargetWalletDir.resolve(".locked")));
        assertTrue(Files.exists(newTargetWalletDir.resolve(".key_hash")));
        assertFalse(
            newTargetWalletDir.getFileName().toString().startsWith("locked_")
        );
        LOGGER.info(
            "✓ Target wallet folder structure updated for UNLOCKED state"
        );
        LOGGER.info(
            "✓ Folder name NO LONGER has 'locked_' prefix: {}",
            newTargetWalletDir.getFileName()
        );

        // Verify old locked folder no longer exists (if path changed)
        if (!targetWalletDir.equals(newTargetWalletDir)) {
            assertFalse(Files.exists(targetWalletDir));
            LOGGER.info(
                "✓ Old locked folder removed: {}",
                targetWalletDir.getFileName()
            );
        }

        targetWalletDir = newTargetWalletDir; // Update reference for further tests

        // STEP 4: Verify target wallet now has FULL ACCESS
        LOGGER.info("Step 4: Verifying target wallet has FULL ACCESS");

        assertDoesNotThrow(() -> {
            targetUnlocked.createFile(
                "authorized.txt",
                "Target wallet can write".getBytes()
            );
        });

        assertDoesNotThrow(() -> {
            targetUnlocked.createDirectory("authorized_folder");
        });

        assertDoesNotThrow(() -> {
            targetUnlocked.updateTokenBalance("REV", 1000L);
        });

        assertDoesNotThrow(() -> {
            targetUnlocked.executeBlockchainTransaction("test_transaction");
        });

        LOGGER.info("✓ Target wallet has FULL ACCESS - all operations work");

        // Verify files were actually created
        assertTrue(Files.exists(targetWalletDir.resolve("authorized.txt")));
        assertTrue(Files.exists(targetWalletDir.resolve("authorized_folder")));
        LOGGER.info(
            "✓ Target wallet successfully created files in file system"
        );

        // STEP 5: Simulate other wallets from genesis block (all should be LOCKED)
        LOGGER.info(
            "Step 5: Creating other wallets from genesis block (all LOCKED)"
        );

        for (String otherAddress : OTHER_GENESIS_WALLETS) {
            CompletableFuture<LockedPhysicalWallet> otherFuture =
                walletManager.createLockedWallet(otherAddress);
            LockedPhysicalWallet otherWallet = otherFuture.get();

            assertTrue(otherWallet.isLocked());
            assertFalse(otherWallet.isUnlocked());

            // Verify locked folder has locked_ prefix
            Path otherWalletDir = otherWallet.getWalletPath();
            assertTrue(
                otherWalletDir.getFileName().toString().startsWith("locked_")
            );
            LOGGER.info(
                "✓ Other wallet {} is LOCKED with 'locked_' prefix: {}",
                otherAddress,
                otherWalletDir.getFileName()
            );

            // Verify they cannot write
            assertThrows(IllegalStateException.class, () -> {
                otherWallet.createFile(
                    "blocked.txt",
                    "Should not work".getBytes()
                );
            });

            assertThrows(IllegalStateException.class, () -> {
                otherWallet.updateTokenBalance("REV", 999999L);
            });

            LOGGER.info(
                "✓ Other wallet {} blocks write operations",
                otherAddress
            );

            // But can read
            assertDoesNotThrow(() -> {
                otherWallet.fileExists("README.md");
            });
            LOGGER.info(
                "✓ Other wallet {} allows read operations",
                otherAddress
            );
        }

        // STEP 6: Verify manager-level security enforcement
        LOGGER.info("Step 6: Verifying manager-level security enforcement");

        // Target wallet should pass validation
        assertDoesNotThrow(() -> {
            walletManager.validateWalletOperation(
                TARGET_REV_ADDRESS,
                "create_file"
            );
        });
        assertTrue(walletManager.isWalletUnlocked(TARGET_REV_ADDRESS));
        LOGGER.info(
            "✓ Target wallet passes validation and is marked as unlocked"
        );

        // Other wallets should fail validation
        for (String otherAddress : OTHER_GENESIS_WALLETS) {
            assertThrows(IllegalStateException.class, () -> {
                walletManager.validateWalletOperation(
                    otherAddress,
                    "create_file"
                );
            });
            assertTrue(walletManager.isWalletLocked(otherAddress));
            LOGGER.info(
                "✓ Other wallet {} fails validation and is marked as locked",
                otherAddress
            );
        }

        // STEP 7: Final security verification
        LOGGER.info("Step 7: Final security verification");

        LOGGER.info("=== SECURITY SUMMARY ===");
        LOGGER.info(
            "✅ ONLY wallet {} can modify files (NO 'locked_' prefix)",
            TARGET_REV_ADDRESS
        );
        LOGGER.info(
            "✅ ALL other wallets ({}) are read-only (WITH 'locked_' prefix)",
            OTHER_GENESIS_WALLETS.length
        );
        LOGGER.info("✅ System properly enforces access control");
        LOGGER.info(
            "✅ File system reflects correct wallet states with proper prefixes"
        );
        LOGGER.info("✅ Manager validates operations correctly");

        // Verify final state
        assertEquals(
            1,
            walletManager
                .getManagedWalletAddresses()
                .stream()
                .mapToInt(addr -> walletManager.isWalletUnlocked(addr) ? 1 : 0)
                .sum(),
            "Only one wallet should be unlocked"
        );

        long lockedCount = walletManager
            .getManagedWalletAddresses()
            .stream()
            .mapToLong(addr -> walletManager.isWalletLocked(addr) ? 1 : 0)
            .sum();

        assertEquals(
            OTHER_GENESIS_WALLETS.length,
            lockedCount,
            "All other wallets should be locked"
        );

        LOGGER.info("✅ COMMAND SECURITY TEST PASSED");
        LOGGER.info("The command will work securely as expected:");
        LOGGER.info(
            "- Target REV address has full access (folder WITHOUT 'locked_' prefix)"
        );
        LOGGER.info(
            "- All other addresses remain read-only (folders WITH 'locked_' prefix)"
        );
        LOGGER.info("- Folder names clearly indicate wallet access level");
    }

    @Test
    void testWithoutPrivateKey() throws Exception {
        LOGGER.info("=== TESTING SCENARIO WITHOUT PRIVATE KEY ===");
        LOGGER.info(
            "Simulating: same command but WITHOUT --private-key parameter"
        );

        // Create wallet without unlocking
        CompletableFuture<LockedPhysicalWallet> lockedFuture =
            walletManager.createLockedWallet(TARGET_REV_ADDRESS);
        LockedPhysicalWallet targetWallet = lockedFuture.get();

        // Should remain locked
        assertTrue(targetWallet.isLocked());
        assertFalse(targetWallet.isUnlocked());
        LOGGER.info(
            "✓ Target wallet remains LOCKED without private key (keeps 'locked_' prefix)"
        );

        // Verify locked prefix exists
        Path walletDir = targetWallet.getWalletPath();
        assertTrue(walletDir.getFileName().toString().startsWith("locked_"));
        LOGGER.info(
            "✓ Wallet folder has 'locked_' prefix: {}",
            walletDir.getFileName()
        );

        // Should block write operations
        assertThrows(IllegalStateException.class, () -> {
            targetWallet.createFile(
                "blocked.txt",
                "Should not work".getBytes()
            );
        });
        LOGGER.info("✓ Target wallet blocks write operations when locked");

        // Create other wallets (also locked)
        for (String otherAddress : OTHER_GENESIS_WALLETS) {
            LockedPhysicalWallet otherWallet = walletManager
                .createLockedWallet(otherAddress)
                .get();
            assertTrue(otherWallet.isLocked());
        }

        LOGGER.info(
            "✅ WITHOUT PRIVATE KEY: ALL wallets have 'locked_' prefix (read-only)"
        );
        LOGGER.info("✅ No wallet can modify files");
        LOGGER.info(
            "✅ System is secure by default with clear visual indicators"
        );
    }
}
