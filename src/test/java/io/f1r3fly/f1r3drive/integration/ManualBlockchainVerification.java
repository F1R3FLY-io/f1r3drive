package io.f1r3fly.f1r3drive.integration;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual verification of blockchain integration.
 * This script connects to a local blockchain, performs file operations,
 * and prints token usage information.
 */
public class ManualBlockchainVerification {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualBlockchainVerification.class);

    // Use a pre-funded testnet address or a local genesis address
    private static final String REV_WALLET = "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private static final String PRIVATE_KEY = "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9";

    @Test
    public void runManualVerification() throws Exception {
        LOGGER.info("=== Starting Manual Blockchain Verification ===");

        // Initialize encryption
        String cipherKeyPath = System.getProperty("user.home") + "/cipher.key";
        AESCipher.init(cipherKeyPath);

        // Initialize blockchain client
        F1r3flyBlockchainClient client = new F1r3flyBlockchainClient(
                "localhost", 40402,
                "localhost", 40442,
                true // manual propose
        );

        try {
            client.waitForNodesSynchronization();
        } catch (Exception e) {
            assumeTrue(false, "Blockchain node not available");
        }

        // Initialize FileSystem
        InMemoryFileSystem fs = new InMemoryFileSystem(client);

        // Unlock wallet
        LOGGER.info("Unlocking wallet: {}", REV_WALLET);
        fs.unlockRootDirectory(REV_WALLET, PRIVATE_KEY);

        // Get initial tokens
        printTokenBalance(fs, "Initial");

        // Create file
        String filePath = "/" + REV_WALLET + "/test-block-verify-" + System.currentTimeMillis() + ".txt";
        LOGGER.info("Creating file: {}", filePath);
        fs.createFile(filePath, 0644);

        // Wait for processing
        LOGGER.info("Waiting for transaction processing...");
        LOGGER.info("Waiting for transaction processing (allow block creation)...");
        Thread.sleep(15000); // Increased wait time for block creation

        printTokenBalance(fs, "After Create");

        // Write to file
        LOGGER.info("Writing to file: {}", filePath);
        byte[] content = "Hello Blockchain World!".getBytes();
        fs.writeFile(filePath, createFSPointer(content), content.length, 0);

        LOGGER.info("Waiting for transaction processing...");
        LOGGER.info("Waiting for transaction processing...");
        Thread.sleep(15000);

        printTokenBalance(fs, "After Write");

        // Delete file
        LOGGER.info("Deleting file: {}", filePath);
        fs.unlinkFile(filePath);

        LOGGER.info("Waiting for transaction processing...");
        LOGGER.info("Waiting for transaction processing...");
        Thread.sleep(15000);

        printTokenBalance(fs, "After Delete");

        // --- Additional Operations ---

        // Create Directory
        String dirPath = "/" + REV_WALLET + "/test-dir-" + System.currentTimeMillis();
        LOGGER.info("Creating directory: {}", dirPath);
        fs.makeDirectory(dirPath, 0755);

        LOGGER.info("Waiting for transaction processing (directory)...");
        Thread.sleep(15000);

        printTokenBalance(fs, "After MkDir");

        // Move File (Rename)
        // First create a file to move
        String moveSrc = "/" + REV_WALLET + "/move-src-" + System.currentTimeMillis() + ".txt";
        String moveDst = dirPath + "/moved-file.txt";

        LOGGER.info("Creating file for move: {}", moveSrc);
        fs.createFile(moveSrc, 0644);
        Thread.sleep(15000); // Wait for create

        LOGGER.info("Moving file: {} -> {}", moveSrc, moveDst);
        fs.renameFile(moveSrc, moveDst);

        LOGGER.info("Waiting for transaction processing (move)...");
        Thread.sleep(15000);

        printTokenBalance(fs, "After Move");

        // Remove Directory (must be empty usually, but let's try removing)
        // Note: InMemoryFileSystem might require empty dir, but let's try removing the
        // moved file first
        LOGGER.info("Removing moved file: {}", moveDst);
        fs.unlinkFile(moveDst);
        Thread.sleep(15000);

        LOGGER.info("Removing directory: {}", dirPath);
        fs.removeDirectory(dirPath);

        LOGGER.info("Waiting for transaction processing (rmdir)...");
        Thread.sleep(15000);

        printTokenBalance(fs, "After RmDir");

        LOGGER.info("=== Verification Complete ===");
    }

    private FSPointer createFSPointer(byte[] data) {
        return new FSPointer() {
            private byte[] buffer = data;

            @Override
            public void put(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(bytes, start, buffer, (int) offset, length);
            }

            @Override
            public void get(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(buffer, (int) offset, bytes, start, length);
            }

            @Override
            public byte getByte(long offset) {
                return buffer[(int) offset];
            }

            @Override
            public void putByte(long offset, byte value) {
                buffer[(int) offset] = value;
            }
        };
    }

    private void printTokenBalance(InMemoryFileSystem fs, String stage) {
        try {
            Directory walletDir = fs.getDirectory("/" + REV_WALLET);
            if (walletDir instanceof UnlockedWalletDirectory) {
                TokenDirectory tokenDir = ((UnlockedWalletDirectory) walletDir).getTokenDirectory();
                if (tokenDir != null) {
                    Set<Path> tokens = tokenDir.getChildren();
                    LOGGER.info("[{}] Token count: {}", stage, tokens.size());
                    for (Path p : tokens) {
                        LOGGER.info(" - Token: {}", p.getName());
                    }
                } else {
                    LOGGER.warn("[{}] .tokens directory not found", stage);
                }
            } else {
                LOGGER.warn("[{}] Wallet directory not found or locked", stage);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check token balance", e);
        }
    }
}
