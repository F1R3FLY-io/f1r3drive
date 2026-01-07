package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an unlocked physical wallet folder on disk.
 * Unlocked wallets have full access to tokens, files, and blockchain operations.
 */
public class UnlockedPhysicalWallet extends PhysicalWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        UnlockedPhysicalWallet.class
    );

    private static final String UNLOCKED_STATUS_FILE = ".unlocked";
    private static final String PRIVATE_KEY_HASH_FILE = ".key_hash";

    public UnlockedPhysicalWallet(
        BlockchainContext blockchainContext,
        Path walletPath,
        String baseDirectory
    ) {
        super(blockchainContext, walletPath, baseDirectory);
    }

    @Override
    public String getWalletStatus() {
        return "UNLOCKED";
    }

    @Override
    public void createFolderStructure() throws IOException {
        LOGGER.info(
            "Creating unlocked wallet folder structure for: {}",
            getRevAddress()
        );

        // Create basic folder structure
        createBasicFolderStructure();

        // Create unlocked-specific structure
        createUnlockedStructure();

        LOGGER.info("Unlocked wallet folder structure created: {}", walletPath);
    }

    /**
     * Creates the unlocked wallet structure with full access
     */
    public void createUnlockedStructure() throws IOException {
        LOGGER.info(
            "Transforming to unlocked wallet structure: {}",
            getRevAddress()
        );

        // Remove locked status file if it exists
        removeLockStatusFiles();

        // Create unlocked status indicator
        createUnlockedStatusFile();

        // Create key hash for security validation
        createPrivateKeyHashFile();

        // Initialize token directory with access
        initializeTokensDirectory();

        // Initialize folders directory with access
        initializeFoldersDirectory();

        // Initialize blockchain files directory
        initializeBlockchainFilesDirectory();

        LOGGER.info("Wallet unlocked successfully: {}", walletPath);
    }

    /**
     * Removes any locked status files from previous state
     */
    private void removeLockStatusFiles() throws IOException {
        Path lockedFile = walletPath.resolve(".locked");
        if (Files.exists(lockedFile)) {
            Files.delete(lockedFile);
            LOGGER.debug("Removed locked status file: {}", lockedFile);
        }

        // Remove locked README files from subdirectories
        removeLockedReadmeFiles();
    }

    /**
     * Removes locked README files from subdirectories
     */
    private void removeLockedReadmeFiles() throws IOException {
        Path tokensReadme = getTokensDirectory().resolve("README.txt");
        Path foldersReadme = getFoldersDirectory().resolve("README.txt");

        if (Files.exists(tokensReadme)) {
            Files.delete(tokensReadme);
            LOGGER.debug("Removed locked tokens README: {}", tokensReadme);
        }

        if (Files.exists(foldersReadme)) {
            Files.delete(foldersReadme);
            LOGGER.debug("Removed locked folders README: {}", foldersReadme);
        }
    }

    /**
     * Creates unlocked status indicator file
     */
    private void createUnlockedStatusFile() throws IOException {
        Path unlockedPath = walletPath.resolve(UNLOCKED_STATUS_FILE);

        String unlockedContent = String.format(
            """
            WALLET STATUS: UNLOCKED

            This wallet is unlocked and has full access to blockchain operations.

            Wallet Address: %s
            Access Level: FULL ACCESS

            Available operations:
            - View and update token balances
            - Read/write blockchain files
            - Create/modify/delete folders
            - Execute blockchain transactions
            - Access private wallet data

            Security:
            - Private key validated and authenticated
            - Cryptographic signatures enabled
            - Full blockchain interaction available

            Warning: Keep this wallet secure. Anyone with access to this folder
            can perform operations on behalf of this wallet address.
            """,
            getRevAddress()
        );

        Files.write(
            unlockedPath,
            unlockedContent.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Created unlocked status file: {}", unlockedPath);
    }

    /**
     * Creates a hash of the private key for security validation
     */
    private void createPrivateKeyHashFile() throws IOException {
        Path keyHashPath = walletPath.resolve(PRIVATE_KEY_HASH_FILE);

        byte[] signingKey = blockchainContext.getWalletInfo().signingKey();
        String keyHash = generateKeyHash(signingKey);

        String hashContent = String.format(
            """
            # Private Key Hash
            # This file contains a hash of the private key used to unlock this wallet
            # It is used for validation and security purposes

            wallet.address=%s
            key.hash=%s
            unlock.timestamp=%s
            """,
            getRevAddress(),
            keyHash,
            java.time.Instant.now().toString()
        );

        Files.write(
            keyHashPath,
            hashContent.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Created private key hash file: {}", keyHashPath);
    }

    /**
     * Generates a secure hash of the private key
     */
    private String generateKeyHash(byte[] signingKey) {
        try {
            java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signingKey);
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            LOGGER.warn("Failed to generate key hash, using fallback", e);
            return "hash_generation_failed_" + System.currentTimeMillis();
        }
    }

    /**
     * Initializes tokens directory with full access
     */
    private void initializeTokensDirectory() throws IOException {
        Path tokensDir = getTokensDirectory();

        // Create tokens access file
        Path tokensAccessPath = tokensDir.resolve("ACCESS_ENABLED.txt");
        String tokensContent = String.format(
            """
            TOKENS DIRECTORY - UNLOCKED WALLET

            This directory contains token files with real blockchain balances.

            Wallet Address: %s
            Status: UNLOCKED
            Access: FULL

            Contents:
            - Token balance files (JSON format)
            - Real-time balance updates from blockchain
            - Transaction history files

            Note: Token files are automatically updated when blockchain state changes.
            """,
            getRevAddress()
        );

        Files.write(
            tokensAccessPath,
            tokensContent.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Initialized unlocked tokens directory: {}", tokensDir);
    }

    /**
     * Initializes folders directory with full access
     */
    private void initializeFoldersDirectory() throws IOException {
        Path foldersDir = getFoldersDirectory();

        // Create folders access file
        Path foldersAccessPath = foldersDir.resolve("ACCESS_ENABLED.txt");
        String foldersContent = String.format(
            """
            FOLDERS DIRECTORY - UNLOCKED WALLET

            This directory contains blockchain-based folder structure.

            Wallet Address: %s
            Status: UNLOCKED
            Access: FULL

            Contents:
            - Sub-folders from blockchain folder tokens
            - Files and documents stored in folders
            - Folder permissions and metadata
            - Real-time synchronization with blockchain

            Note: Folder structure is synchronized with blockchain state.
            """,
            getRevAddress()
        );

        Files.write(
            foldersAccessPath,
            foldersContent.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Initialized unlocked folders directory: {}", foldersDir);
    }

    /**
     * Initializes blockchain files directory
     */
    private void initializeBlockchainFilesDirectory() throws IOException {
        Path blockchainDir = getBlockchainFilesDirectory();

        // Create blockchain files access file
        Path bcFilesAccessPath = blockchainDir.resolve("ACCESS_ENABLED.txt");
        String bcFilesContent = String.format(
            """
            BLOCKCHAIN FILES DIRECTORY - UNLOCKED WALLET

            This directory contains files stored directly on the blockchain.

            Wallet Address: %s
            Status: UNLOCKED
            Access: FULL

            Contents:
            - Files downloaded from blockchain storage
            - File metadata and version history
            - Synchronization status information

            Note: Files are downloaded from blockchain and kept in sync.
            """,
            getRevAddress()
        );

        Files.write(
            bcFilesAccessPath,
            bcFilesContent.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        // Create metadata subdirectory
        createSubDirectory("metadata").resolve("blockchain_files");

        LOGGER.debug(
            "Initialized blockchain files directory: {}",
            blockchainDir
        );
    }

    /**
     * Gets the private key for this unlocked wallet
     */
    public byte[] getPrivateKey() {
        return blockchainContext.getWalletInfo().signingKey();
    }

    /**
     * Checks if the wallet can perform privileged operations
     */
    public boolean hasFullAccess() {
        return getPrivateKey() != null && getPrivateKey().length > 0;
    }

    /**
     * Validates that this wallet is properly unlocked
     */
    public boolean validateUnlockState() {
        try {
            // Check that unlocked status file exists
            if (!Files.exists(walletPath.resolve(UNLOCKED_STATUS_FILE))) {
                return false;
            }

            // Check that private key is available
            if (!hasFullAccess()) {
                return false;
            }

            // Verify key hash matches
            return validateKeyHash();
        } catch (Exception e) {
            LOGGER.warn(
                "Error validating unlock state for wallet: {}",
                getRevAddress(),
                e
            );
            return false;
        }
    }

    /**
     * Validates the private key hash for security
     */
    private boolean validateKeyHash() {
        try {
            Path keyHashPath = walletPath.resolve(PRIVATE_KEY_HASH_FILE);
            if (!Files.exists(keyHashPath)) {
                return false;
            }

            String expectedHash = generateKeyHash(getPrivateKey());
            String fileContent = Files.readString(keyHashPath);

            return fileContent.contains("key.hash=" + expectedHash);
        } catch (Exception e) {
            LOGGER.warn(
                "Error validating key hash for wallet: {}",
                getRevAddress(),
                e
            );
            return false;
        }
    }

    /**
     * Full access file operations for unlocked wallets - can create files
     */
    public void createFile(String relativePath, byte[] content)
        throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path filePath = walletPath.resolve(relativePath);

        // Create parent directories if they don't exist
        java.nio.file.Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.write(
            filePath,
            content,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Created file in unlocked wallet: {}", relativePath);
    }

    /**
     * Full access file operations for unlocked wallets - can write to files
     */
    public void writeFile(String relativePath, byte[] content)
        throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path filePath = walletPath.resolve(relativePath);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + relativePath);
        }

        Files.write(
            filePath,
            content,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Modified file in unlocked wallet: {}", relativePath);
    }

    /**
     * Full access file operations for unlocked wallets - can delete files
     */
    public void deleteFile(String relativePath) throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path filePath = walletPath.resolve(relativePath);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + relativePath);
        }

        if (Files.isDirectory(filePath)) {
            throw new IOException(
                "Path is a directory, not a file: " + relativePath
            );
        }

        Files.delete(filePath);
        LOGGER.debug("Deleted file from unlocked wallet: {}", relativePath);
    }

    /**
     * Full access directory operations for unlocked wallets - can create directories
     */
    public void createDirectory(String relativePath)
        throws IOException, IllegalStateException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path dirPath = walletPath.resolve(relativePath);

        Files.createDirectories(dirPath);
        LOGGER.debug("Created directory in unlocked wallet: {}", relativePath);
    }

    /**
     * Full access directory operations for unlocked wallets - can delete directories
     */
    public void deleteDirectory(String relativePath) throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path dirPath = walletPath.resolve(relativePath);

        if (!Files.exists(dirPath)) {
            throw new IOException("Directory not found: " + relativePath);
        }

        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + relativePath);
        }

        // Delete directory and all contents recursively
        try (var stream = Files.walk(dirPath)) {
            stream
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("Could not delete path: {}", path, e);
                    }
                });
        }

        LOGGER.debug(
            "Deleted directory from unlocked wallet: {}",
            relativePath
        );
    }

    /**
     * Full access operations for unlocked wallets - can rename files
     */
    public void renameFile(String oldPath, String newPath) throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path oldFilePath = walletPath.resolve(oldPath);
        java.nio.file.Path newFilePath = walletPath.resolve(newPath);

        if (!Files.exists(oldFilePath)) {
            throw new IOException("Source file not found: " + oldPath);
        }

        // Create parent directory for new path if needed
        java.nio.file.Path newParentDir = newFilePath.getParent();
        if (newParentDir != null && !Files.exists(newParentDir)) {
            Files.createDirectories(newParentDir);
        }

        Files.move(oldFilePath, newFilePath);
        LOGGER.debug(
            "Renamed file in unlocked wallet: {} -> {}",
            oldPath,
            newPath
        );
    }

    /**
     * Full access operations for unlocked wallets - can rename directories
     */
    public void renameDirectory(String oldPath, String newPath)
        throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path oldDirPath = walletPath.resolve(oldPath);
        java.nio.file.Path newDirPath = walletPath.resolve(newPath);

        if (!Files.exists(oldDirPath)) {
            throw new IOException("Source directory not found: " + oldPath);
        }

        if (!Files.isDirectory(oldDirPath)) {
            throw new IOException("Source path is not a directory: " + oldPath);
        }

        // Create parent directory for new path if needed
        java.nio.file.Path newParentDir = newDirPath.getParent();
        if (newParentDir != null && !Files.exists(newParentDir)) {
            Files.createDirectories(newParentDir);
        }

        Files.move(oldDirPath, newDirPath);
        LOGGER.debug(
            "Renamed directory in unlocked wallet: {} -> {}",
            oldPath,
            newPath
        );
    }

    /**
     * Read operations are allowed on unlocked wallets - can read file content
     */
    public byte[] readFile(String relativePath) throws IOException {
        requireReadAccess();
        java.nio.file.Path filePath = walletPath.resolve(relativePath);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + relativePath);
        }

        if (Files.isDirectory(filePath)) {
            throw new IOException(
                "Path is a directory, not a file: " + relativePath
            );
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Read operations are allowed on unlocked wallets - can list directory contents
     */
    public String[] listDirectory(String relativePath) throws IOException {
        requireReadAccess();
        java.nio.file.Path dirPath = walletPath.resolve(
            relativePath.isEmpty() ? "." : relativePath
        );

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
     * Read operations are allowed on unlocked wallets - can check if file exists
     */
    public boolean fileExists(String relativePath) {
        requireReadAccess();
        return Files.exists(walletPath.resolve(relativePath));
    }

    /**
     * Read operations are allowed on unlocked wallets - can check if path is directory
     */
    public boolean isDirectory(String relativePath) {
        requireReadAccess();
        return Files.isDirectory(walletPath.resolve(relativePath));
    }

    /**
     * Full access token operations for unlocked wallets
     */
    public void updateTokenBalance(String tokenName, long balance)
        throws IOException {
        requireReadAccess(); // Unlocked wallets have full access
        java.nio.file.Path tokenFile = getTokensDirectory().resolve(
            tokenName + ".token"
        );

        String tokenContent = String.format(
            """
            {
                "tokenName": "%s",
                "balance": %d,
                "walletAddress": "%s",
                "lastUpdated": "%s"
            }
            """,
            tokenName,
            balance,
            getRevAddress(),
            java.time.Instant.now()
        );

        Files.write(
            tokenFile,
            tokenContent.getBytes(),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        );

        LOGGER.debug("Updated token balance for {}: {}", tokenName, balance);
    }

    /**
     * Full access blockchain operations for unlocked wallets
     */
    public void executeBlockchainTransaction(String transactionData) {
        requireReadAccess(); // Unlocked wallets have full access

        try {
            // This would integrate with the blockchain context to execute transactions
            // For now, just log the transaction attempt
            LOGGER.info(
                "Executing blockchain transaction for wallet {}: {}",
                getRevAddress(),
                transactionData
            );

            // TODO: Implement actual blockchain transaction execution
            // using this.blockchainContext.getDeployDispatcher()
        } catch (Exception e) {
            LOGGER.error(
                "Failed to execute blockchain transaction for wallet {}: {}",
                getRevAddress(),
                transactionData,
                e
            );
            throw new RuntimeException(
                "Blockchain transaction failed: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public void cleanup() {
        LOGGER.debug("Cleaning up unlocked wallet: {}", getRevAddress());

        try {
            // Clear private key from memory (if possible)
            // Note: Java byte arrays can be cleared
            byte[] privateKey = getPrivateKey();
            if (privateKey != null) {
                java.util.Arrays.fill(privateKey, (byte) 0);
                // Log that cleanup occurred
                LOGGER.debug(
                    "Cleared private key from memory for wallet: {}",
                    getRevAddress()
                );
            }

            // Remove sensitive temporary files if any exist
            cleanupTemporaryFiles();
        } catch (Exception e) {
            LOGGER.warn(
                "Error during cleanup for unlocked wallet: {}",
                getRevAddress(),
                e
            );
        }
    }

    /**
     * Removes temporary files that may contain sensitive data
     */
    private void cleanupTemporaryFiles() {
        try {
            // Clean up any temporary transaction files
            Path tempDir = walletPath.resolve("temp");
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            LOGGER.debug(
                                "Could not delete temp file: {}",
                                path,
                                e
                            );
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.debug(
                "Error cleaning up temporary files for wallet: {}",
                getRevAddress(),
                e
            );
        }
    }
}
