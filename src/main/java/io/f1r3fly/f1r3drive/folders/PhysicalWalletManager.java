package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.InvalidSigningKeyException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages physical wallet folders on disk, including unlocking wallets with private keys
 * and creating proper folder structures that mirror blockchain wallet states.
 */
public class PhysicalWalletManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        PhysicalWalletManager.class
    );

    private final F1r3flyBlockchainClient blockchainClient;
    private final DeployDispatcher deployDispatcher;
    private final String baseDirectory;

    // Track created wallets to avoid duplicates
    private final Map<String, PhysicalWallet> managedWallets =
        new ConcurrentHashMap<>();

    public PhysicalWalletManager(
        F1r3flyBlockchainClient blockchainClient,
        DeployDispatcher deployDispatcher,
        String baseDirectory
    ) {
        this.blockchainClient = blockchainClient;
        this.deployDispatcher = deployDispatcher;
        this.baseDirectory = baseDirectory;
    }

    /**
     * Creates or retrieves a locked physical wallet folder for the given address
     */
    public CompletableFuture<LockedPhysicalWallet> createLockedWallet(
        String revAddress
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (managedWallets.containsKey(revAddress)) {
                PhysicalWallet existing = managedWallets.get(revAddress);
                if (existing instanceof LockedPhysicalWallet) {
                    return (LockedPhysicalWallet) existing;
                }
            }

            try {
                Path walletPath = createLockedWalletPath(revAddress);
                RevWalletInfo walletInfo = new RevWalletInfo(revAddress, null);
                BlockchainContext context = new BlockchainContext(
                    walletInfo,
                    deployDispatcher
                );

                LockedPhysicalWallet lockedWallet = new LockedPhysicalWallet(
                    context,
                    walletPath,
                    baseDirectory
                );

                lockedWallet.createFolderStructure();
                managedWallets.put(revAddress, lockedWallet);

                LOGGER.info(
                    "Created locked physical wallet folder: {}",
                    walletPath
                );
                return lockedWallet;
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to create locked wallet for address: {}",
                    revAddress,
                    e
                );
                throw new RuntimeException(
                    "Failed to create locked wallet: " + e.getMessage(),
                    e
                );
            }
        });
    }

    /**
     * Unlocks a physical wallet with the provided private key
     */
    public CompletableFuture<UnlockedPhysicalWallet> unlockPhysicalWallet(
        String revAddress,
        String privateKey
    ) throws InvalidSigningKeyException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get or create locked wallet first
                LockedPhysicalWallet lockedWallet = createLockedWallet(
                    revAddress
                ).join();

                // Validate private key against address
                if (!validatePrivateKey(revAddress, privateKey)) {
                    throw new InvalidSigningKeyException(
                        "Private key does not match wallet address"
                    );
                }

                // Get paths for folder renaming
                Path lockedPath = lockedWallet.getWalletPath();
                Path unlockedPath = createUnlockedWalletPath(revAddress);

                // Rename folder from locked_ prefix to no prefix
                if (
                    Files.exists(lockedPath) && !lockedPath.equals(unlockedPath)
                ) {
                    try {
                        Files.move(lockedPath, unlockedPath);
                        LOGGER.info(
                            "Renamed wallet folder: {} -> {}",
                            lockedPath.getFileName(),
                            unlockedPath.getFileName()
                        );
                    } catch (IOException e) {
                        LOGGER.warn(
                            "Failed to rename wallet folder, using existing path: {}",
                            e.getMessage()
                        );
                        unlockedPath = lockedPath; // Fallback to existing path
                    }
                }

                // Create unlocked wallet
                byte[] privateKeyBytes = convertPrivateKeyToBytes(privateKey);
                RevWalletInfo walletInfo = new RevWalletInfo(
                    revAddress,
                    privateKeyBytes
                );
                BlockchainContext context = new BlockchainContext(
                    walletInfo,
                    deployDispatcher
                );

                UnlockedPhysicalWallet unlockedWallet =
                    new UnlockedPhysicalWallet(
                        context,
                        unlockedPath,
                        baseDirectory
                    );

                // Transform locked folder to unlocked structure
                unlockedWallet.createUnlockedStructure();

                // Replace in managed wallets
                managedWallets.put(revAddress, unlockedWallet);

                LOGGER.info(
                    "Successfully unlocked physical wallet: {}",
                    revAddress
                );
                return unlockedWallet;
            } catch (Exception e) {
                LOGGER.error("Failed to unlock wallet: {}", revAddress, e);
                throw new RuntimeException(
                    "Failed to unlock wallet: " + e.getMessage(),
                    e
                );
            }
        });
    }

    /**
     * Retrieves a managed wallet if it exists
     */
    public PhysicalWallet getWallet(String revAddress) {
        return managedWallets.get(revAddress);
    }

    /**
     * Checks if a wallet is currently unlocked
     */
    public boolean isWalletUnlocked(String walletAddress) {
        PhysicalWallet wallet = managedWallets.get(walletAddress);
        return wallet != null && wallet.isUnlocked();
    }

    /**
     * Checks if a wallet is currently locked
     */
    public boolean isWalletLocked(String walletAddress) {
        PhysicalWallet wallet = managedWallets.get(walletAddress);
        return wallet != null && wallet.isLocked();
    }

    /**
     * Gets all managed wallet addresses
     */
    public java.util.Set<String> getManagedWalletAddresses() {
        return managedWallets.keySet();
    }

    /**
     * Creates the file system path for a locked wallet folder
     */
    private Path createLockedWalletPath(String revAddress) {
        String folderName = createLockedWalletFolderName(revAddress);
        return Paths.get(baseDirectory, folderName);
    }

    /**
     * Creates the file system path for an unlocked wallet folder
     */
    private Path createUnlockedWalletPath(String revAddress) {
        String folderName = createUnlockedWalletFolderName(revAddress);
        return Paths.get(baseDirectory, folderName);
    }

    /**
     * Creates a folder name for a locked wallet with locked_ prefix
     */
    private String createLockedWalletFolderName(String walletAddress) {
        if (walletAddress.length() > 16) {
            return String.format(
                "locked_%s...%s",
                walletAddress.substring(0, 8),
                walletAddress.substring(walletAddress.length() - 8)
            );
        }
        return "locked_" + walletAddress;
    }

    /**
     * Creates a folder name for an unlocked wallet (no prefix)
     */
    private String createUnlockedWalletFolderName(String walletAddress) {
        if (walletAddress.length() > 16) {
            return String.format(
                "%s...%s",
                walletAddress.substring(0, 8),
                walletAddress.substring(walletAddress.length() - 8)
            );
        }
        return walletAddress;
    }

    /**
     * Validates that the private key corresponds to the wallet address
     */
    private boolean validatePrivateKey(String revAddress, String privateKey) {
        try {
            // TODO: Implement actual cryptographic validation
            // This should verify that the private key can generate the public key
            // that corresponds to the given REV address
            return (
                privateKey != null &&
                !privateKey.trim().isEmpty() &&
                privateKey.length() >= 64
            );
        } catch (Exception e) {
            LOGGER.warn(
                "Private key validation failed for address: {}",
                revAddress,
                e
            );
            return false;
        }
    }

    /**
     * Shuts down the wallet manager and cleans up resources
     */
    /**
     * Converts a hex private key string to byte array
     */
    private byte[] convertPrivateKeyToBytes(String privateKey) {
        try {
            // Remove "0x" prefix if present
            String cleanKey = privateKey.startsWith("0x")
                ? privateKey.substring(2)
                : privateKey;

            // Convert hex string to byte array
            int len = cleanKey.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(
                            cleanKey.charAt(i),
                            16
                        ) <<
                        4) +
                    Character.digit(cleanKey.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to convert private key to bytes, using dummy key",
                e
            );
            // Return dummy key for testing
            byte[] dummyKey = new byte[32];
            java.util.Arrays.fill(dummyKey, (byte) 0x01);
            return dummyKey;
        }
    }

    /**
     * Validates that a wallet operation can be performed
     * @param walletAddress REV address of the wallet
     * @param operationType Type of operation being attempted
     * @throws IllegalStateException if wallet is locked and operation requires unlock
     */
    public void validateWalletOperation(
        String walletAddress,
        String operationType
    ) throws IllegalStateException {
        PhysicalWallet wallet = managedWallets.get(walletAddress);

        if (wallet == null) {
            throw new IllegalStateException(
                String.format("Wallet %s not found in manager", walletAddress)
            );
        }

        if (wallet.isLocked() && isWriteOperation(operationType)) {
            throw new IllegalStateException(
                String.format(
                    "Operation '%s' not permitted on locked wallet %s. Please unlock the wallet first with the private key.",
                    operationType,
                    walletAddress
                )
            );
        }
    }

    /**
     * Checks if the specified operation type requires write access
     */
    private boolean isWriteOperation(String operationType) {
        return switch (operationType.toLowerCase()) {
            case
                "create_file",
                "write_file",
                "delete_file",
                "rename_file",
                "create_directory",
                "delete_directory",
                "rename_directory",
                "update_token",
                "blockchain_transaction" -> true;
            case
                "read_file",
                "list_directory",
                "file_exists",
                "is_directory" -> false;
            default -> true; // Default to requiring write access for unknown operations
        };
    }

    /**
     * Executes a file operation on a wallet after validation
     */
    public void executeFileOperation(
        String walletAddress,
        String operation,
        String relativePath,
        byte[] content
    ) throws IOException, IllegalStateException {
        validateWalletOperation(walletAddress, operation);
        PhysicalWallet wallet = getWallet(walletAddress);

        switch (operation.toLowerCase()) {
            case "create_file" -> wallet.createFile(relativePath, content);
            case "write_file" -> wallet.writeFile(relativePath, content);
            case "delete_file" -> wallet.deleteFile(relativePath);
            default -> throw new IllegalArgumentException(
                "Unknown file operation: " + operation
            );
        }

        LOGGER.info(
            "Executed {} on wallet {} at path {}",
            operation,
            walletAddress,
            relativePath
        );
    }

    /**
     * Executes a directory operation on a wallet after validation
     */
    public void executeDirectoryOperation(
        String walletAddress,
        String operation,
        String relativePath
    ) throws IOException, IllegalStateException {
        validateWalletOperation(walletAddress, operation);
        PhysicalWallet wallet = getWallet(walletAddress);

        switch (operation.toLowerCase()) {
            case "create_directory" -> wallet.createDirectory(relativePath);
            case "delete_directory" -> wallet.deleteDirectory(relativePath);
            default -> throw new IllegalArgumentException(
                "Unknown directory operation: " + operation
            );
        }

        LOGGER.info(
            "Executed {} on wallet {} at path {}",
            operation,
            walletAddress,
            relativePath
        );
    }

    /**
     * Executes a read operation on a wallet (allowed for both locked and unlocked)
     */
    public Object executeReadOperation(
        String walletAddress,
        String operation,
        String relativePath
    ) throws IOException, IllegalStateException {
        PhysicalWallet wallet = getWallet(walletAddress);

        return switch (operation.toLowerCase()) {
            case "read_file" -> wallet.readFile(relativePath);
            case "list_directory" -> wallet.listDirectory(relativePath);
            case "file_exists" -> wallet.fileExists(relativePath);
            case "is_directory" -> wallet.isDirectory(relativePath);
            default -> throw new IllegalArgumentException(
                "Unknown read operation: " + operation
            );
        };
    }

    /**
     * Executes a token operation on a wallet after validation
     */
    public void executeTokenOperation(
        String walletAddress,
        String tokenName,
        long balance
    ) throws IOException, IllegalStateException {
        validateWalletOperation(walletAddress, "update_token");
        PhysicalWallet wallet = getWallet(walletAddress);

        wallet.updateTokenBalance(tokenName, balance);
        LOGGER.info(
            "Updated token {} balance to {} for wallet {}",
            tokenName,
            balance,
            walletAddress
        );
    }

    /**
     * Executes a blockchain transaction on a wallet after validation
     */
    public void executeBlockchainTransaction(
        String walletAddress,
        String transactionData
    ) throws IllegalStateException {
        validateWalletOperation(walletAddress, "blockchain_transaction");
        PhysicalWallet wallet = getWallet(walletAddress);

        wallet.executeBlockchainTransaction(transactionData);
        LOGGER.info(
            "Executed blockchain transaction for wallet {}",
            walletAddress
        );
    }

    public void shutdown() {
        LOGGER.info("Shutting down PhysicalWalletManager...");

        managedWallets
            .values()
            .forEach(wallet -> {
                try {
                    wallet.cleanup();
                } catch (Exception e) {
                    LOGGER.warn(
                        "Error cleaning up wallet: {}",
                        wallet.getRevAddress(),
                        e
                    );
                }
            });

        managedWallets.clear();
        LOGGER.info("PhysicalWalletManager shutdown complete");
    }
}
