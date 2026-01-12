package io.f1r3fly.f1r3drive.filesystem.local;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.local.RootDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rhoapi.RhoTypes;

/**
 * Unit tests for locked wallet directory creation functionality.
 * Tests the automatic creation of locked wallet directories when they don't exist.
 */
class LockedWalletDirectoryCreationTest {

    private static final String TEST_WALLET_ADDRESS =
        "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private static final String TEST_PRIVATE_KEY =
        "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";

    @Mock
    private F1r3flyBlockchainClient mockBlockchainClient;

    private InMemoryFileSystem fileSystem;
    private RootDirectory rootDirectory;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock blockchain client to avoid actual network calls
        // Just mock the blockchain client minimally - the test will fail at blockchain
        // operations but that's fine, we only care about directory creation logic

        fileSystem = new InMemoryFileSystem(mockBlockchainClient);
        rootDirectory = fileSystem.getRootDirectory();
    }

    @Test
    void testLockedWalletDirectoryCreation() {
        // Given: No locked wallet directory exists initially
        String expectedPath = "/LOCKED-REMOTE-REV-" + TEST_WALLET_ADDRESS;

        // Verify directory doesn't exist initially
        System.out.println(
            "DEBUG: Checking if directory exists initially: " + expectedPath
        );
        System.out.println(
            "DEBUG: Initial directory exists: " + directoryExists(expectedPath)
        );
        assertFalse(
            directoryExists(expectedPath),
            "Locked wallet directory should not exist initially"
        );

        // When: We try to unlock the wallet (which should create the locked directory)
        System.out.println("DEBUG: Attempting to unlock wallet...");
        Exception caughtException = null;
        try {
            fileSystem.unlockRootDirectory(
                TEST_WALLET_ADDRESS,
                TEST_PRIVATE_KEY
            );
        } catch (Exception e) {
            caughtException = e;
            System.out.println(
                "DEBUG: Caught exception: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
            );
            // Expected to fail due to blockchain operations, but directory should still be created
            assertTrue(
                e.getMessage().contains("Failed to unlock wallet") ||
                    e.getMessage().contains("blockchain") ||
                    e.getMessage().contains("exploratory") ||
                    e.getMessage().contains("deploy"),
                "Should fail due to blockchain operations, not missing directory"
            );
        }

        // Then: The locked wallet directory should now exist
        System.out.println(
            "DEBUG: Checking if directory exists after unlock attempt: " +
                expectedPath
        );
        System.out.println(
            "DEBUG: Directory exists after unlock: " +
                directoryExists(expectedPath)
        );
        System.out.println(
            "DEBUG: Root directory children count: " +
                rootDirectory.getChildren().size()
        );
        for (var child : rootDirectory.getChildren()) {
            System.out.println(
                "DEBUG: Child: " +
                    child.getName() +
                    " (type: " +
                    child.getClass().getSimpleName() +
                    ")"
            );
        }

        assertTrue(
            directoryExists(expectedPath),
            "Locked wallet directory should be created automatically"
        );

        // And: The directory should be of correct type
        Path lockedDir = findDirectory(expectedPath);
        assertNotNull(lockedDir, "Locked directory should not be null");
        assertInstanceOf(
            LockedWalletDirectory.class,
            lockedDir,
            "Should be LockedWalletDirectory instance"
        );

        // And: The directory should have correct name
        assertEquals(
            "LOCKED-REMOTE-REV-" + TEST_WALLET_ADDRESS,
            lockedDir.getName(),
            "Directory name should match expected format"
        );
    }

    @Test
    void testLockedWalletDirectoryWithMultipleAddresses() {
        String address1 =
            "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
        String address2 =
            "1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8";
        String key1 =
            "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";
        String key2 =
            "61e594124ca6af84a5468d98b34a4f3431ef39c54c6cf07fe6fbf8b079ef64f6";

        // Try to unlock both wallets - expect blockchain failures but directory creation should work
        try {
            fileSystem.unlockRootDirectory(address1, key1);
        } catch (Exception e) {
            // Expected blockchain failure
            assertTrue(
                e.getMessage().contains("Failed to unlock wallet") ||
                    e.getMessage().contains("blockchain")
            );
        }

        try {
            fileSystem.unlockRootDirectory(address2, key2);
        } catch (Exception e) {
            // Expected blockchain failure
            assertTrue(
                e.getMessage().contains("Failed to unlock wallet") ||
                    e.getMessage().contains("blockchain")
            );
        }

        // Both locked directories should exist despite blockchain failures
        assertTrue(
            directoryExists("/LOCKED-REMOTE-REV-" + address1),
            "First locked wallet directory should exist"
        );
        assertTrue(
            directoryExists("/LOCKED-REMOTE-REV-" + address2),
            "Second locked wallet directory should exist"
        );
    }

    @Test
    void testBlockchainContextCreation() {
        // When: Creating a locked wallet directory - expect blockchain failure
        try {
            fileSystem.unlockRootDirectory(
                TEST_WALLET_ADDRESS,
                TEST_PRIVATE_KEY
            );
        } catch (Exception e) {
            // Expected blockchain failure
            assertTrue(
                e.getMessage().contains("Failed to unlock wallet") ||
                    e.getMessage().contains("blockchain")
            );
        }

        // Then: The directory should have proper blockchain context despite failure
        Path lockedDir = findDirectory(
            "/LOCKED-REMOTE-REV-" + TEST_WALLET_ADDRESS
        );
        assertNotNull(lockedDir, "Locked directory should exist");

        if (lockedDir instanceof LockedWalletDirectory) {
            LockedWalletDirectory walletDir = (LockedWalletDirectory) lockedDir;
            BlockchainContext context = walletDir.getBlockchainContext();
            assertNotNull(context, "Blockchain context should not be null");

            RevWalletInfo walletInfo = context.getWalletInfo();
            assertNotNull(walletInfo, "Wallet info should not be null");
            assertEquals(
                TEST_WALLET_ADDRESS,
                walletInfo.revAddress(),
                "Wallet address should match"
            );
        }
    }

    /**
     * Helper method to check if a directory exists in the file system.
     */
    private boolean directoryExists(String path) {
        try {
            Path dir = fileSystem.getDirectory(path);
            System.out.println(
                "DEBUG: directoryExists(" +
                    path +
                    ") -> found: " +
                    (dir != null ? dir.getClass().getSimpleName() : "null")
            );
            return dir != null;
        } catch (Exception e) {
            System.out.println(
                "DEBUG: directoryExists(" +
                    path +
                    ") -> exception: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
            );
            return false;
        }
    }

    /**
     * Helper method to find a directory by path.
     */
    private Path findDirectory(String path) {
        try {
            Path dir = fileSystem.getDirectory(path);
            System.out.println(
                "DEBUG: findDirectory(" +
                    path +
                    ") -> found: " +
                    (dir != null ? dir.getClass().getSimpleName() : "null")
            );
            return dir;
        } catch (Exception e) {
            System.out.println(
                "DEBUG: findDirectory(" +
                    path +
                    ") -> exception: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
            );
            return null;
        }
    }
}
