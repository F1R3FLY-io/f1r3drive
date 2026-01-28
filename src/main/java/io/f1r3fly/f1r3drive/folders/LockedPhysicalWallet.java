package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a locked physical wallet folder on disk.
 * Locked wallets have limited access and require a private key to unlock.
 */
public class LockedPhysicalWallet extends PhysicalWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LockedPhysicalWallet.class);

    private static final String LOCKED_STATUS_FILE = ".locked";

    public LockedPhysicalWallet(
            BlockchainContext blockchainContext,
            Path walletPath,
            String baseDirectory) {
        super(blockchainContext, walletPath, baseDirectory);
    }

    @Override
    public String getWalletStatus() {
        return "LOCKED";
    }

    @Override
    public void createFolderStructure() throws IOException {
        LOGGER.info(
                "Creating locked wallet folder structure for: {}",
                getRevAddress());

        // Create basic folder structure
        createBasicFolderStructure();

        // Create locked status indicator file
        createLockedStatusFile();

        // Create limited content for locked wallet
        // createLockedTokensDirectory();
        // createLockedFoldersDirectory();

        LOGGER.info("Locked wallet folder structure created: {}", walletPath);
    }

    /**
     * Creates a status file indicating this wallet is locked
     */
    private void createLockedStatusFile() throws IOException {
        Path lockedPath = walletPath.resolve(LOCKED_STATUS_FILE);

        String lockedContent = String.format(
                """
                        WALLET STATUS: LOCKED

                        This wallet is currently locked and requires a private key to access.

                        Wallet Address: %s
                        Access Level: READ-ONLY

                        To unlock this wallet, provide the private key using:
                        --private-key <private_key>

                        Available operations:
                        - View wallet address
                        - View basic folder structure
                        - View README information

                        Restricted operations (require unlock):
                        - Access token balances
                        - Read/write blockchain files
                        - Create/modify folders
                        - Execute transactions
                        """,
                getRevAddress());

        Files.write(
                lockedPath,
                lockedContent.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        LOGGER.debug("Created locked status file: {}", lockedPath);
    }

    /**
     * Creates a limited tokens directory with placeholder information
     */
    private void createLockedTokensDirectory() throws IOException {
        Path tokensDir = getTokensDirectory();

        // Create info file explaining locked state
        Path tokenInfoPath = tokensDir.resolve("README.txt");
        String tokenInfo = String.format(
                """
                        TOKENS DIRECTORY - LOCKED WALLET

                        This directory will contain token files when the wallet is unlocked.

                        Wallet Address: %s
                        Status: LOCKED

                        To view actual token balances, unlock the wallet with:
                        --private-key <your_private_key>

                        Expected contents after unlock:
                        - REV.token (REV balance file)
                        - Additional token files for owned tokens
                        """,
                getRevAddress());

        Files.write(
                tokenInfoPath,
                tokenInfo.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        LOGGER.debug("Created locked tokens directory info: {}", tokenInfoPath);
    }

    /**
     * Creates a limited folders directory with placeholder information
     */
    private void createLockedFoldersDirectory() throws IOException {
        Path foldersDir = getFoldersDirectory();

        // Create info file explaining locked state
        Path folderInfoPath = foldersDir.resolve("README.txt");
        String folderInfo = String.format(
                """
                        FOLDERS DIRECTORY - LOCKED WALLET

                        This directory will contain sub-folders when the wallet is unlocked.

                        Wallet Address: %s
                        Status: LOCKED

                        To access actual folders and their contents, unlock the wallet with:
                        --private-key <your_private_key>

                        Expected contents after unlock:
                        - Blockchain-based folder structure
                        - Files and documents stored in folders
                        - Folder permissions and metadata
                        """,
                getRevAddress());

        Files.write(
                folderInfoPath,
                folderInfo.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        LOGGER.debug(
                "Created locked folders directory info: {}",
                folderInfoPath);
    }

    /**
     * Checks if this wallet can be unlocked with the provided private key
     */
    public boolean canUnlockWith(String privateKey) {
        try {
            // TODO: Implement actual cryptographic validation
            // This should verify that the private key can generate signatures
            // that correspond to the wallet address
            return privateKey != null && !privateKey.trim().isEmpty();
        } catch (Exception e) {
            LOGGER.warn(
                    "Error validating private key for wallet: {}",
                    getRevAddress(),
                    e);
            return false;
        }
    }

    /**
     * Gets limited wallet information available to locked wallets
     */
    public WalletInfo getPublicInfo() {
        return new WalletInfo(
                getRevAddress(),
                getWalletStatus(),
                walletPath.toString(),
                exists());
    }

    @Override
    public void cleanup() {
        LOGGER.debug("Cleaning up locked wallet: {}", getRevAddress());
        // No special cleanup needed for locked wallets
        // File system cleanup is handled by OS
    }

    /**
     * Prevents any write operations on locked wallets - files cannot be created
     */
    public void createFile(String relativePath, byte[] content)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - files cannot be modified
     */
    public void writeFile(String relativePath, byte[] content)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - files cannot be deleted
     */
    public void deleteFile(String relativePath) throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - directories cannot be
     * created
     */
    @Override
    public void createDirectory(String relativePath)
            throws IOException, IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - directories cannot be
     * deleted
     */
    public void deleteDirectory(String relativePath)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - files cannot be renamed
     */
    public void renameFile(String oldPath, String newPath)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents any write operations on locked wallets - directories cannot be
     * renamed
     */
    public void renameDirectory(String oldPath, String newPath)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Read operations are allowed on locked wallets - can read file content
     */
    public byte[] readFile(String relativePath) throws IOException {
        requireReadAccess();
        java.nio.file.Path filePath = walletPath.resolve(relativePath);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + relativePath);
        }

        if (Files.isDirectory(filePath)) {
            throw new IOException(
                    "Path is a directory, not a file: " + relativePath);
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Read operations are allowed on locked wallets - can list directory contents
     */
    public String[] listDirectory(String relativePath) throws IOException {
        requireReadAccess();
        java.nio.file.Path dirPath = walletPath.resolve(
                relativePath.isEmpty() ? "." : relativePath);

        if (!Files.exists(dirPath)) {
            throw new IOException("Directory not found: " + relativePath);
        }

        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + relativePath);
        }

        try (var stream = Files.list(dirPath)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .toArray(String[]::new);
        }
    }

    /**
     * Read operations are allowed on locked wallets - can check if file exists
     */
    public boolean fileExists(String relativePath) {
        requireReadAccess();
        return Files.exists(walletPath.resolve(relativePath));
    }

    /**
     * Read operations are allowed on locked wallets - can check if path is
     * directory
     */
    public boolean isDirectory(String relativePath) {
        requireReadAccess();
        return Files.isDirectory(walletPath.resolve(relativePath));
    }

    /**
     * Prevents token operations on locked wallets
     */
    public void updateTokenBalance(String tokenName, long balance)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Prevents blockchain operations on locked wallets
     */
    public void executeBlockchainTransaction(String transactionData)
            throws IllegalStateException {
        requireUnlocked();
    }

    /**
     * Simple data class for public wallet information
     */
    public static class WalletInfo {

        private final String address;
        private final String status;
        private final String path;
        private final boolean exists;

        public WalletInfo(
                String address,
                String status,
                String path,
                boolean exists) {
            this.address = address;
            this.status = status;
            this.path = path;
            this.exists = exists;
        }

        public String getAddress() {
            return address;
        }

        public String getStatus() {
            return status;
        }

        public String getPath() {
            return path;
        }

        public boolean exists() {
            return exists;
        }

        @Override
        public String toString() {
            return String.format(
                    "WalletInfo{address=%s, status=%s, path=%s, exists=%s}",
                    address,
                    status,
                    path,
                    exists);
        }
    }
}
