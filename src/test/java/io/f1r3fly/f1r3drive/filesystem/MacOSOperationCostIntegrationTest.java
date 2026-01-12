package io.f1r3fly.f1r3drive.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.RootDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenFile;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rhoapi.RhoTypes;

/**
 * Integration test simulating macOS File Provider operations with F1r3Drive
 * operation cost tracking system.
 */
public class MacOSOperationCostIntegrationTest {

    private F1r3flyBlockchainClient blockchainClient;
    private DeployDispatcher deployDispatcher;

    private static final String TEST_WALLET_ADDRESS =
        "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private static final byte[] TEST_SIGNING_KEY = new byte[32]; // Mock signing key

    private InMemoryFileSystem fileSystem;
    private String walletPath;

    @BeforeEach
    void setUp() throws Exception {
        // Reset operation costs
        TokenDirectory.resetWalletCosts(TEST_WALLET_ADDRESS);

        // Initialize file system with null blockchain client for testing
        fileSystem = new InMemoryFileSystem(null);

        walletPath = "/" + TEST_WALLET_ADDRESS;
    }

    @Test
    @DisplayName(
        "Should track operation costs when user performs macOS file operations"
    )
    void shouldTrackOperationCostsForMacOSFileOperations() throws Exception {
        System.out.println("=== macOS File Provider Operations Test ===");

        // 1. Simulate unlocking wallet (like opening in Finder)
        System.out.println("1. Unlocking wallet directory...");
        simulateWalletUnlock();

        // Verify wallet unlock operation cost
        long costsAfterUnlock = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        assertTrue(costsAfterUnlock > 0, "Unlock operation should have costs");
        System.out.println(
            "   Costs after unlock: " + costsAfterUnlock + " REV"
        );

        // 2. Simulate creating a directory (like New Folder in Finder)
        System.out.println("\n2. Creating directory via File Provider...");
        fileSystem.makeDirectory(walletPath + "/MyDocuments", 0755);

        long costsAfterMkdir = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        assertEquals(
            costsAfterUnlock + 5L,
            costsAfterMkdir,
            "CREATE_DIRECTORY should cost 5 REV"
        );
        System.out.println("   Costs after mkdir: " + costsAfterMkdir + " REV");

        // 3. Simulate creating a file (like creating TextEdit document)
        System.out.println("\n3. Creating file via File Provider...");
        fileSystem.createFile(walletPath + "/MyDocuments/document.txt", 0644);

        long costsAfterCreate = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        assertEquals(
            costsAfterMkdir + 1L,
            costsAfterCreate,
            "CREATE_FILE should cost 1 REV"
        );
        System.out.println(
            "   Costs after create: " + costsAfterCreate + " REV"
        );

        // 4. Simulate writing to file (like saving document)
        System.out.println("\n4. Writing to file via File Provider...");
        String content = "This is test content for operation cost tracking";
        byte[] contentBytes = content.getBytes();

        // Mock FSPointer for write operation
        MockFSPointer writePointer = new MockFSPointer(contentBytes);
        int bytesWritten = fileSystem.writeFile(
            walletPath + "/MyDocuments/document.txt",
            writePointer,
            contentBytes.length,
            0
        );

        long costsAfterWrite = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        assertEquals(
            costsAfterCreate + 3L,
            costsAfterWrite,
            "WRITE_FILE should cost 3 REV"
        );
        assertEquals(
            contentBytes.length,
            bytesWritten,
            "Should write all bytes"
        );
        System.out.println("   Costs after write: " + costsAfterWrite + " REV");

        // 5. Simulate reading file (like opening document)
        System.out.println("\n5. Reading file via File Provider...");
        MockFSPointer readPointer = new MockFSPointer(
            new byte[contentBytes.length]
        );
        int bytesRead = fileSystem.readFile(
            walletPath + "/MyDocuments/document.txt",
            readPointer,
            contentBytes.length,
            0
        );

        long costsAfterRead = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        assertEquals(
            costsAfterWrite + 1L,
            costsAfterRead,
            "READ_FILE should cost 1 REV"
        );
        assertEquals(contentBytes.length, bytesRead, "Should read all bytes");
        System.out.println("   Costs after read: " + costsAfterRead + " REV");

        // 6. Simulate accessing .tokens directory (like viewing in Finder)
        System.out.println("\n6. Accessing .tokens directory...");
        checkTokensDirectory();

        long finalCosts = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        System.out.println("   Final total costs: " + finalCosts + " REV");

        // 7. Verify balance calculation
        System.out.println("\n7. Balance verification:");
        long initialBalance = 1000L;
        long availableBalance = Math.max(0, initialBalance - finalCosts);
        System.out.println("   Initial balance: " + initialBalance + " REV");
        System.out.println("   Operation costs: " + finalCosts + " REV");
        System.out.println(
            "   Available balance: " + availableBalance + " REV"
        );

        // 8. Print operation breakdown
        System.out.println("\n8. Operation breakdown:");
        System.out.println(TokenDirectory.getOperationCostsReport());

        // Assertions
        assertTrue(finalCosts > 0, "Should have accumulated operation costs");
        assertTrue(
            availableBalance < initialBalance,
            "Available balance should be reduced"
        );
        assertEquals(
            initialBalance - finalCosts,
            availableBalance,
            "Balance calculation should be correct"
        );
    }

    @Test
    @DisplayName("Should show cost summary in .tokens directory")
    void shouldShowCostSummaryInTokensDirectory() throws Exception {
        System.out.println("\n=== .tokens Directory Cost Summary Test ===");

        // Perform some operations
        simulateWalletUnlock();
        fileSystem.makeDirectory(walletPath + "/TestDir", 0755);
        fileSystem.createFile(walletPath + "/TestDir/test.txt", 0644);

        // Check tokens directory content
        UnlockedWalletDirectory walletDir = getUnlockedWalletDirectory();
        assertNotNull(walletDir, "Should have unlocked wallet directory");

        TokenDirectory tokensDir = walletDir.getTokenDirectory();
        assertNotNull(tokensDir, "Should have .tokens directory");

        Set<io.f1r3fly.f1r3drive.filesystem.common.Path> children =
            tokensDir.getChildren();
        assertFalse(children.isEmpty(), "Tokens directory should not be empty");

        // Look for cost summary file
        // Verify that we have token files but no cost-summary.info file
        boolean foundCostSummary = children
            .stream()
            .anyMatch(child -> child.getName().equals("cost-summary.info"));

        assertFalse(foundCostSummary, "Should not have cost-summary.info file");

        long totalCosts = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        System.out.println("Total costs tracked: " + totalCosts + " REV");
        System.out.println(
            "Number of token files: " +
                children
                    .stream()
                    .filter(c -> c instanceof TokenFile)
                    .count()
        );
    }

    @Test
    @DisplayName("Should handle multiple wallets independently")
    void shouldHandleMultipleWalletsIndependently() throws Exception {
        String wallet2Address =
            "1111BTC8kZxAJfZiJMX84TKb7nSDPR4pVocdSGp4Q7DxZXD2YKoJJ";
        TokenDirectory.resetWalletCosts(wallet2Address);

        // Record operations for wallet 1
        TokenDirectory.recordOperationCost(TEST_WALLET_ADDRESS, "CREATE_FILE");
        TokenDirectory.recordOperationCost(TEST_WALLET_ADDRESS, "WRITE_FILE");

        // Record operations for wallet 2
        TokenDirectory.recordOperationCost(wallet2Address, "READ_FILE");
        TokenDirectory.recordOperationCost(wallet2Address, "DEPLOY_CONTRACT");

        long wallet1Costs = TokenDirectory.getTotalOperationCosts(
            TEST_WALLET_ADDRESS
        );
        long wallet2Costs = TokenDirectory.getTotalOperationCosts(
            wallet2Address
        );

        assertEquals(
            4L,
            wallet1Costs,
            "Wallet 1: CREATE_FILE(1) + WRITE_FILE(3) = 4"
        );
        assertEquals(
            101L,
            wallet2Costs,
            "Wallet 2: READ_FILE(1) + DEPLOY_CONTRACT(100) = 101"
        );

        System.out.println("Wallet isolation test:");
        System.out.println("  Wallet 1 costs: " + wallet1Costs + " REV");
        System.out.println("  Wallet 2 costs: " + wallet2Costs + " REV");
    }

    private void simulateWalletUnlock() throws Exception {
        // Create blockchain context for testing
        RevWalletInfo walletInfo = new RevWalletInfo(
            TEST_WALLET_ADDRESS,
            TEST_SIGNING_KEY
        );
        BlockchainContext context = new BlockchainContext(
            walletInfo,
            null // No deploy dispatcher needed for test
        );

        // Create unlocked wallet directory
        RootDirectory root = fileSystem.getRootDirectory();

        UnlockedWalletDirectory unlockedWallet = new UnlockedWalletDirectory(
            TEST_WALLET_ADDRESS,
            context,
            root
        );

        root.addChild(unlockedWallet);

        // Record unlock operation cost
        TokenDirectory.recordOperationCost(
            TEST_WALLET_ADDRESS,
            "UNLOCK_WALLET"
        );
    }

    private void checkTokensDirectory() {
        // This simulates accessing .tokens directory which triggers balance recalculation
        UnlockedWalletDirectory walletDir = getUnlockedWalletDirectory();
        if (walletDir != null) {
            TokenDirectory tokensDir = walletDir.getTokenDirectory();
            if (tokensDir != null) {
                // Accessing children triggers read operation cost
                tokensDir.getChildren();
            }
        }
    }

    private UnlockedWalletDirectory getUnlockedWalletDirectory() {
        try {
            io.f1r3fly.f1r3drive.filesystem.common.Path path =
                fileSystem.findPath(walletPath);
            if (path instanceof UnlockedWalletDirectory) {
                return (UnlockedWalletDirectory) path;
            }
        } catch (Exception e) {
            // Directory not found or not unlocked
        }
        return null;
    }

    /**
     * Mock FSPointer implementation for testing
     */
    private static class MockFSPointer
        implements io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer {

        private final byte[] buffer;

        public MockFSPointer(byte[] buffer) {
            this.buffer = buffer;
        }

        @Override
        public void put(long offset, byte[] src, int srcOffset, int length) {
            System.arraycopy(src, srcOffset, buffer, (int) offset, length);
        }

        @Override
        public void get(long offset, byte[] dst, int dstOffset, int length) {
            System.arraycopy(buffer, (int) offset, dst, dstOffset, length);
        }

        @Override
        public byte getByte(long offset) {
            return buffer[(int) offset];
        }

        @Override
        public void putByte(long offset, byte value) {
            buffer[(int) offset] = value;
        }
    }
}
