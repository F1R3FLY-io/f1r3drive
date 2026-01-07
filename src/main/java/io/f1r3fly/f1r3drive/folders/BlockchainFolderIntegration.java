package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complete integration example showing how to discover existing blockchain tokens
 * and automatically create corresponding folders in /Users/jedoan/demo-f1r3drive
 */
public class BlockchainFolderIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        BlockchainFolderIntegration.class
    );

    private final F1r3flyBlockchainClient blockchainClient;
    private final FolderTokenService folderTokenService;
    private final AutoFolderCreator autoFolderCreator;
    private final TokenDiscovery tokenDiscovery;
    private final GenesisWalletExtractor genesisWalletExtractor;
    private final PhysicalWalletManager walletManager;
    private DeployDispatcher deployDispatcher;
    private StateChangeEventsManager stateChangeEventsManager;

    public BlockchainFolderIntegration(
        F1r3flyBlockchainClient blockchainClient
    ) {
        this(blockchainClient, "/Users/jedoan/demo-f1r3drive");
    }

    public BlockchainFolderIntegration(
        F1r3flyBlockchainClient blockchainClient,
        String baseDirectory
    ) {
        this.blockchainClient = blockchainClient;
        this.folderTokenService = new FolderTokenService();
        this.autoFolderCreator = new AutoFolderCreator(
            blockchainClient,
            folderTokenService,
            baseDirectory
        );
        this.tokenDiscovery = new TokenDiscovery(blockchainClient);
        this.genesisWalletExtractor = new GenesisWalletExtractor(
            blockchainClient,
            baseDirectory
        );

        // Initialize DeployDispatcher and StateChangeEventsManager
        initializeBlockchainServices();

        this.walletManager = new PhysicalWalletManager(
            blockchainClient,
            deployDispatcher,
            baseDirectory
        );

        LOGGER.info(
            "BlockchainFolderIntegration initialized with base directory: {}",
            baseDirectory
        );
    }

    /**
     * Main method to discover and create all folders from blockchain using PhysicalWalletManager
     */
    public CompletableFuture<IntegrationResult> discoverAndCreateAllFolders() {
        return discoverAndCreateAllFolders(Set.of());
    }

    /**
     * Main method to discover and create all folders from blockchain using PhysicalWalletManager
     * @param excludeFromLocked - wallet addresses to exclude from locked wallet creation
     */
    public CompletableFuture<IntegrationResult> discoverAndCreateAllFolders(
        Set<String> excludeFromLocked
    ) {
        LOGGER.info(
            "=== Starting Physical Wallet Discovery and Folder Creation ==="
        );
        if (!excludeFromLocked.isEmpty()) {
            LOGGER.info(
                "Excluding {} wallets from locked creation: {}",
                excludeFromLocked.size(),
                excludeFromLocked
            );
        }

        return genesisWalletExtractor
            .extractWalletAddressesFromGenesis()
            .thenCompose(walletAddresses -> {
                LOGGER.info(
                    "Discovered {} wallet addresses from genesis block",
                    walletAddresses.size()
                );

                // Create locked wallets for all discovered addresses (excluding specified ones)
                Set<String> addressesToLock = walletAddresses
                    .stream()
                    .filter(address -> !excludeFromLocked.contains(address))
                    .collect(Collectors.toSet());

                LOGGER.info(
                    "Creating {} locked wallets out of {} discovered",
                    addressesToLock.size(),
                    walletAddresses.size()
                );

                CompletableFuture<Void> walletCreationFutures =
                    CompletableFuture.allOf(
                        addressesToLock
                            .stream()
                            .map(address ->
                                walletManager.createLockedWallet(address)
                            )
                            .toArray(CompletableFuture[]::new)
                    );

                return walletCreationFutures.thenApply(v -> {
                    IntegrationResult result = new IntegrationResult();

                    Set<String> managedAddresses =
                        walletManager.getManagedWalletAddresses();
                    result.discoveredWallets = walletAddresses.size();
                    result.createdWalletDirs = managedAddresses.size();
                    result.discoveredFolders = managedAddresses.size();
                    result.createdFolderTokens = managedAddresses.size();
                    result.failedOperations =
                        walletAddresses.size() - managedAddresses.size();
                    result.success = !managedAddresses.isEmpty();

                    LOGGER.info(
                        "✓ Physical wallet creation completed: {} wallets created, {} failed",
                        result.createdWalletDirs,
                        result.failedOperations
                    );

                    for (String address : managedAddresses) {
                        LOGGER.info(
                            "  ✓ Created locked wallet folder for: {}",
                            address
                        );
                    }

                    LOGGER.info(
                        "=== Physical Wallet Integration Completed Successfully ==="
                    );
                    return result;
                });
            });
    }

    /**
     * Discovers and creates folders for a single wallet address using PhysicalWalletManager
     */
    public CompletableFuture<IntegrationResult> discoverAndCreateForWallet(
        String walletAddress
    ) {
        LOGGER.info(
            "Creating physical wallet folder for specific address: {}",
            walletAddress
        );

        return walletManager
            .createLockedWallet(walletAddress)
            .thenApply(lockedWallet -> {
                IntegrationResult result = new IntegrationResult();

                result.discoveredWallets = 1;
                result.createdWalletDirs = 1;
                result.discoveredFolders = 1;
                result.createdFolderTokens = 1;
                result.failedOperations = 0;
                result.success = true;

                LOGGER.info(
                    "✓ Created locked physical wallet folder: {}",
                    lockedWallet.getWalletPath()
                );

                return result;
            })
            .exceptionally(throwable -> {
                IntegrationResult result = new IntegrationResult();
                result.success = false;
                result.errorMessage = throwable.getMessage();
                result.failedOperations = 1;

                LOGGER.error(
                    "Failed to create wallet folder for: {}",
                    walletAddress,
                    throwable
                );
                return result;
            });
    }

    /**
     * Unlocks a physical wallet with the provided private key
     */
    public CompletableFuture<IntegrationResult> unlockPhysicalWallet(
        String walletAddress,
        String privateKey
    ) {
        LOGGER.info(
            "Unlocking physical wallet with private key: {}",
            walletAddress
        );

        return walletManager
            .unlockPhysicalWallet(walletAddress, privateKey)
            .thenApply(unlockedWallet -> {
                IntegrationResult result = new IntegrationResult();

                result.discoveredWallets = 1;
                result.createdWalletDirs = 1;
                result.discoveredFolders = 1;
                result.createdFolderTokens = 1;
                result.failedOperations = 0;
                result.success = true;

                LOGGER.info(
                    "✓ Successfully unlocked physical wallet: {}",
                    unlockedWallet.getWalletPath()
                );

                return result;
            })
            .exceptionally(throwable -> {
                IntegrationResult result = new IntegrationResult();
                result.success = false;
                result.errorMessage = throwable.getMessage();
                result.failedOperations = 1;

                LOGGER.error(
                    "Failed to unlock wallet: {}",
                    walletAddress,
                    throwable
                );
                return result;
            });
    }

    /**
     * Initializes blockchain services required for wallet operations
     */
    private void initializeBlockchainServices() throws F1r3DriveError {
        try {
            this.stateChangeEventsManager = new StateChangeEventsManager();
            this.stateChangeEventsManager.start();

            this.deployDispatcher = new DeployDispatcher(
                blockchainClient,
                stateChangeEventsManager
            );
            deployDispatcher.startBackgroundDeploy();

            LOGGER.info("Blockchain services initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize blockchain services", e);
            throw new F1r3DriveError(
                "Blockchain service initialization failed: " + e.getMessage()
            );
        }
    }

    /**
     * Gets the physical wallet manager instance
     */
    public PhysicalWalletManager getWalletManager() {
        return walletManager;
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
        walletManager.validateWalletOperation(walletAddress, operationType);
    }

    /**
     * Executes a file operation on a wallet after validation
     */
    public CompletableFuture<Void> executeWalletFileOperation(
        String walletAddress,
        String operation,
        String relativePath,
        byte[] content
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info(
                    "Executing file operation '{}' on wallet {} at path {}",
                    operation,
                    walletAddress,
                    relativePath
                );

                walletManager.executeFileOperation(
                    walletAddress,
                    operation,
                    relativePath,
                    content
                );

                LOGGER.info(
                    "Successfully executed file operation '{}' on wallet {}",
                    operation,
                    walletAddress
                );
                return null;
            } catch (IllegalStateException e) {
                LOGGER.warn(
                    "Operation '{}' blocked on locked wallet {}: {}",
                    operation,
                    walletAddress,
                    e.getMessage()
                );
                throw new RuntimeException(e);
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to execute file operation '{}' on wallet {}: {}",
                    operation,
                    walletAddress,
                    e.getMessage(),
                    e
                );
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Executes a directory operation on a wallet after validation
     */
    public CompletableFuture<Void> executeWalletDirectoryOperation(
        String walletAddress,
        String operation,
        String relativePath
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info(
                    "Executing directory operation '{}' on wallet {} at path {}",
                    operation,
                    walletAddress,
                    relativePath
                );

                walletManager.executeDirectoryOperation(
                    walletAddress,
                    operation,
                    relativePath
                );

                LOGGER.info(
                    "Successfully executed directory operation '{}' on wallet {}",
                    operation,
                    walletAddress
                );
                return null;
            } catch (IllegalStateException e) {
                LOGGER.warn(
                    "Operation '{}' blocked on locked wallet {}: {}",
                    operation,
                    walletAddress,
                    e.getMessage()
                );
                throw new RuntimeException(e);
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to execute directory operation '{}' on wallet {}: {}",
                    operation,
                    walletAddress,
                    e.getMessage(),
                    e
                );
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Executes a token operation on a wallet after validation
     */
    public CompletableFuture<Void> executeWalletTokenOperation(
        String walletAddress,
        String tokenName,
        long balance
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info(
                    "Executing token operation on wallet {}: update {} balance to {}",
                    walletAddress,
                    tokenName,
                    balance
                );

                walletManager.executeTokenOperation(
                    walletAddress,
                    tokenName,
                    balance
                );

                LOGGER.info(
                    "Successfully updated token {} balance for wallet {}",
                    tokenName,
                    walletAddress
                );
                return null;
            } catch (IllegalStateException e) {
                LOGGER.warn(
                    "Token operation blocked on locked wallet {}: {}",
                    walletAddress,
                    e.getMessage()
                );
                throw new RuntimeException(e);
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to execute token operation on wallet {}: {}",
                    walletAddress,
                    e.getMessage(),
                    e
                );
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Executes a blockchain transaction on a wallet after validation
     */
    public CompletableFuture<Void> executeWalletBlockchainTransaction(
        String walletAddress,
        String transactionData
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info(
                    "Executing blockchain transaction on wallet {}: {}",
                    walletAddress,
                    transactionData
                );

                walletManager.executeBlockchainTransaction(
                    walletAddress,
                    transactionData
                );

                LOGGER.info(
                    "Successfully executed blockchain transaction for wallet {}",
                    walletAddress
                );
                return null;
            } catch (IllegalStateException e) {
                LOGGER.warn(
                    "Blockchain transaction blocked on locked wallet {}: {}",
                    walletAddress,
                    e.getMessage()
                );
                throw new RuntimeException(e);
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to execute blockchain transaction on wallet {}: {}",
                    walletAddress,
                    e.getMessage(),
                    e
                );
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Safely executes a read operation on any wallet (works for both locked and unlocked)
     */
    public CompletableFuture<Object> executeWalletReadOperation(
        String walletAddress,
        String operation,
        String relativePath
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.debug(
                    "Executing read operation '{}' on wallet {} at path {}",
                    operation,
                    walletAddress,
                    relativePath
                );

                Object result = walletManager.executeReadOperation(
                    walletAddress,
                    operation,
                    relativePath
                );

                LOGGER.debug(
                    "Successfully executed read operation '{}' on wallet {}",
                    operation,
                    walletAddress
                );
                return result;
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to execute read operation '{}' on wallet {}: {}",
                    operation,
                    walletAddress,
                    e.getMessage(),
                    e
                );
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Discovers and creates folders for specific wallet addresses
     */
    public CompletableFuture<IntegrationResult> discoverAndCreateForWallets(
        Set<String> walletAddresses
    ) {
        LOGGER.info(
            "Creating folders for specific wallets: {}",
            walletAddresses
        );

        return CompletableFuture.supplyAsync(() -> {
            IntegrationResult result = new IntegrationResult();

            try {
                AutoFolderCreator.FolderCreationResult creationResult =
                    autoFolderCreator
                        .discoverAndCreateFoldersForWallets(walletAddresses)
                        .get(60, TimeUnit.SECONDS);

                result.createdWalletDirs = creationResult
                    .getCreatedWalletDirs()
                    .size();
                result.createdFolderTokens =
                    creationResult.getTotalFolderTokens();
                result.failedOperations = creationResult
                    .getFailedWalletDirs()
                    .size();
                result.success = true;

                LOGGER.info(
                    "Created folders for {} wallets with {} folder tokens",
                    result.createdWalletDirs,
                    result.createdFolderTokens
                );
            } catch (Exception e) {
                LOGGER.error("Error creating folders for specific wallets", e);
                result.success = false;
                result.errorMessage = e.getMessage();
            }

            return result;
        });
    }

    /**
     * Starts continuous monitoring and folder creation
     */
    public void startContinuousMonitoring(int intervalMinutes) {
        LOGGER.info(
            "Starting continuous blockchain monitoring every {} minutes",
            intervalMinutes
        );
        autoFolderCreator.startPeriodicDiscovery(intervalMinutes);
    }

    /**
     * Starts continuous monitoring for a specific wallet address
     */
    public void startContinuousMonitoringForWallet(
        String walletAddress,
        int intervalMinutes
    ) {
        LOGGER.info(
            "Starting continuous monitoring for wallet {} every {} minutes",
            walletAddress,
            intervalMinutes
        );
        autoFolderCreator.startPeriodicDiscoveryForWallet(
            walletAddress,
            intervalMinutes
        );
    }

    /**
     * Properly terminates the blockchain folder integration and cleans up resources
     */
    public void terminate() {
        LOGGER.info("Terminating BlockchainFolderIntegration...");

        try {
            // Shutdown wallet manager
            if (walletManager != null) {
                walletManager.shutdown();
                LOGGER.info("Wallet manager shutdown completed");
            }

            // Stop deploy dispatcher
            if (deployDispatcher != null) {
                deployDispatcher.destroy();
                LOGGER.info("Deploy dispatcher destroyed");
            }

            // Shutdown state change events manager
            if (stateChangeEventsManager != null) {
                stateChangeEventsManager.shutdown();
                LOGGER.info("State change events manager shutdown");
            }

            // Shutdown auto folder creator
            if (autoFolderCreator != null) {
                // Auto folder creator cleanup if needed
                LOGGER.debug("Auto folder creator cleanup completed");
            }

            // Shutdown token discovery
            if (tokenDiscovery != null) {
                tokenDiscovery.shutdown();
                LOGGER.info("Token discovery shutdown");
            }

            LOGGER.info(
                "BlockchainFolderIntegration termination completed successfully"
            );
        } catch (Exception e) {
            LOGGER.error(
                "Error during BlockchainFolderIntegration termination",
                e
            );
        }
    }

    /**
     * Logs detailed results of the discovery and creation process
     */
    private void logDetailedResults(
        TokenDiscovery.DiscoveryResult discoveryResult,
        AutoFolderCreator.FolderCreationResult creationResult
    ) {
        LOGGER.info("\n--- Detailed Results ---");
        LOGGER.info("Discovered Wallets:");
        for (String wallet : discoveryResult.getWalletAddresses()) {
            Set<String> folders = discoveryResult.getFoldersForWallet(wallet);
            LOGGER.info(
                "  {} -> {} folders: {}",
                wallet,
                folders.size(),
                folders
            );
        }

        LOGGER.info("\nCreated Wallet Directories:");
        creationResult
            .getCreatedWalletDirs()
            .forEach((wallet, path) -> {
                LOGGER.info("  {} -> {}", wallet, path);
            });

        LOGGER.info("\nCreated Folder Tokens:");
        creationResult
            .getCreatedFolderTokens()
            .forEach((wallet, folders) -> {
                LOGGER.info("  Wallet {}: {} folders", wallet, folders.size());
                folders.forEach((folderName, path) -> {
                    LOGGER.info("    {} -> {}", folderName, path);
                });
            });

        if (!creationResult.getFailedWalletDirs().isEmpty()) {
            LOGGER.warn("\nFailed Wallet Directory Creation:");
            creationResult
                .getFailedWalletDirs()
                .forEach((wallet, error) -> {
                    LOGGER.warn("  {} -> Error: {}", wallet, error);
                });
        }

        if (!creationResult.getFailedFolderTokens().isEmpty()) {
            LOGGER.warn("\nFailed Folder Token Creation:");
            creationResult
                .getFailedFolderTokens()
                .forEach((wallet, folders) -> {
                    folders.forEach((folderName, error) -> {
                        LOGGER.warn(
                            "  {}/{} -> Error: {}",
                            wallet,
                            folderName,
                            error
                        );
                    });
                });
        }
    }

    /**
     * Gets current statistics about the integration
     */
    public IntegrationStats getStats() {
        FolderTokenService.FolderTokenStats serviceStats =
            folderTokenService.getStats();
        AutoFolderCreator.FolderCreationStats creationStats =
            autoFolderCreator.getStats();

        return new IntegrationStats(
            serviceStats.getTotalWallets(),
            serviceStats.getTotalFolders(),
            creationStats.getTotalWallets(),
            creationStats.getTotalFolders()
        );
    }

    /**
     * Performs cleanup and shuts down the integration
     */
    public void shutdown() {
        LOGGER.info("Shutting down BlockchainFolderIntegration...");

        folderTokenService.shutdown();
        autoFolderCreator.shutdown();

        LOGGER.info("BlockchainFolderIntegration shutdown complete");
    }

    /**
     * Example usage method showing complete workflow
     */
    public static void runExample(F1r3flyBlockchainClient blockchainClient) {
        LOGGER.info("=== Running BlockchainFolderIntegration Example ===");

        BlockchainFolderIntegration integration =
            new BlockchainFolderIntegration(blockchainClient);

        try {
            // Discover and create all folders
            IntegrationResult result = integration
                .discoverAndCreateAllFolders()
                .get();

            if (result.success) {
                LOGGER.info("✓ Integration successful!");
                LOGGER.info(
                    "  - Discovered: {} wallets, {} folders",
                    result.discoveredWallets,
                    result.discoveredFolders
                );
                LOGGER.info(
                    "  - Created: {} wallet dirs, {} folder tokens",
                    result.createdWalletDirs,
                    result.createdFolderTokens
                );

                // Show current stats
                IntegrationStats stats = integration.getStats();
                LOGGER.info("  - Current stats: {}", stats);

                // Start continuous monitoring (optional)
                // integration.startContinuousMonitoring(30); // Every 30 minutes
            } else {
                LOGGER.error("✗ Integration failed: {}", result.errorMessage);
            }
        } catch (Exception e) {
            LOGGER.error("Error running integration example", e);
        } finally {
            integration.shutdown();
        }
    }

    /**
     * Main method for standalone testing
     */
    public static void main(String[] args) {
        // Configuration for blockchain client
        String validatorHost = "localhost";
        int validatorPort = 40402;
        String observerHost = "localhost";
        int observerPort = 40403;
        boolean manualPropose = true;

        try {
            F1r3flyBlockchainClient blockchainClient =
                new F1r3flyBlockchainClient(
                    validatorHost,
                    validatorPort,
                    observerHost,
                    observerPort,
                    manualPropose
                );

            runExample(blockchainClient);
        } catch (Exception e) {
            LOGGER.error("Failed to run blockchain folder integration", e);
            System.exit(1);
        }
    }

    /**
     * Result of integration operation
     */
    public static class IntegrationResult {

        public boolean success = false;
        public String errorMessage = null;
        public int discoveredWallets = 0;
        public int discoveredFolders = 0;
        public int createdWalletDirs = 0;
        public int createdFolderTokens = 0;
        public int failedOperations = 0;

        @Override
        public String toString() {
            return String.format(
                "IntegrationResult{success=%s, discovered=%d/%d, created=%d/%d, failed=%d}",
                success,
                discoveredWallets,
                discoveredFolders,
                createdWalletDirs,
                createdFolderTokens,
                failedOperations
            );
        }
    }

    /**
     * Statistics about the integration
     */
    public static class IntegrationStats {

        private final int managedWallets;
        private final int managedFolders;
        private final int createdWallets;
        private final int createdFolders;

        public IntegrationStats(
            int managedWallets,
            int managedFolders,
            int createdWallets,
            int createdFolders
        ) {
            this.managedWallets = managedWallets;
            this.managedFolders = managedFolders;
            this.createdWallets = createdWallets;
            this.createdFolders = createdFolders;
        }

        public int getManagedWallets() {
            return managedWallets;
        }

        public int getManagedFolders() {
            return managedFolders;
        }

        public int getCreatedWallets() {
            return createdWallets;
        }

        public int getCreatedFolders() {
            return createdFolders;
        }

        @Override
        public String toString() {
            return String.format(
                "IntegrationStats{managed=%d/%d, created=%d/%d}",
                managedWallets,
                managedFolders,
                createdWallets,
                createdFolders
            );
        }
    }
}
