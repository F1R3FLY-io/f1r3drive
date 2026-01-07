package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automatically discovers existing tokens from blockchain and creates corresponding folders
 * Integrates TokenDiscovery with FolderTokenService to populate /Users/jedoan/demo-f1r3drive
 */
public class AutoFolderCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AutoFolderCreator.class
    );

    private final TokenDiscovery tokenDiscovery;
    private final FolderTokenService folderTokenService;
    private final F1r3flyBlockchainClient blockchainClient;
    private final String baseDirectory;

    // Track created folders to avoid duplicates
    private final Map<String, Set<String>> createdFolders =
        new ConcurrentHashMap<>();

    public AutoFolderCreator(
        F1r3flyBlockchainClient blockchainClient,
        FolderTokenService folderTokenService
    ) {
        this(
            blockchainClient,
            folderTokenService,
            "/Users/jedoan/demo-f1r3drive"
        );
    }

    public AutoFolderCreator(
        F1r3flyBlockchainClient blockchainClient,
        FolderTokenService folderTokenService,
        String baseDirectory
    ) {
        this.blockchainClient = blockchainClient;
        this.folderTokenService = folderTokenService;
        this.baseDirectory = baseDirectory;
        this.tokenDiscovery = new TokenDiscovery(blockchainClient);

        // Ensure base directory exists
        try {
            Path basePath = Paths.get(baseDirectory);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
                LOGGER.info("Created base directory: {}", baseDirectory);
            } else {
                LOGGER.info("Base directory already exists: {}", baseDirectory);
            }
        } catch (IOException e) {
            LOGGER.error(
                "Failed to create base directory: {}",
                baseDirectory,
                e
            );
        }

        LOGGER.info(
            "AutoFolderCreator initialized for directory: {}",
            baseDirectory
        );
    }

    /**
     * Discovers all tokens from blockchain and creates folders for them
     * @return CompletableFuture with creation results
     */
    public CompletableFuture<FolderCreationResult> discoverAndCreateFolders() {
        LOGGER.info(
            "Starting automatic folder creation from blockchain tokens..."
        );

        return tokenDiscovery
            .discoverAllTokens()
            .thenCompose(this::createFoldersForDiscoveredTokens);
    }

    /**
     * Discovers tokens for specific wallet addresses and creates their folders
     * @param walletAddresses Set of wallet addresses to scan
     * @return CompletableFuture with creation results
     */
    public CompletableFuture<
        FolderCreationResult
    > discoverAndCreateFoldersForWallets(Set<String> walletAddresses) {
        LOGGER.info(
            "Creating folders for specific wallets: {}",
            walletAddresses
        );

        FolderCreationResult result = new FolderCreationResult();

        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            walletAddresses
                .stream()
                .map(this::processWalletAddress)
                .map(future ->
                    future.thenAccept(walletResult -> {
                        synchronized (result) {
                            result.merge(walletResult);
                        }
                    })
                )
                .toArray(CompletableFuture[]::new)
        );

        return allTasks.thenApply(v -> {
            LOGGER.info(
                "Folder creation completed for {} wallets",
                walletAddresses.size()
            );
            return result;
        });
    }

    /**
     * Creates folders for discovered tokens
     */
    private CompletableFuture<
        FolderCreationResult
    > createFoldersForDiscoveredTokens(
        TokenDiscovery.DiscoveryResult discoveryResult
    ) {
        FolderCreationResult result = new FolderCreationResult();

        LOGGER.info(
            "Creating folders for {} discovered wallets",
            discoveryResult.getWalletAddresses().size()
        );

        // First create wallet directories
        createWalletDirectories(discoveryResult.getWalletAddresses(), result);

        // Then create folder token structures
        CompletableFuture<Void> folderCreationTasks = CompletableFuture.allOf(
            discoveryResult
                .getWalletAddresses()
                .stream()
                .map(walletAddress ->
                    createFoldersForWallet(
                        walletAddress,
                        discoveryResult.getFoldersForWallet(walletAddress),
                        result
                    )
                )
                .toArray(CompletableFuture[]::new)
        );

        return folderCreationTasks.thenApply(v -> {
            LOGGER.info(
                "Folder creation completed. Created {} wallet dirs, {} folder tokens",
                result.getCreatedWalletDirs().size(),
                result.getTotalFolderTokens()
            );
            return result;
        });
    }

    /**
     * Creates wallet directories in the base directory
     */
    private void createWalletDirectories(
        Set<String> walletAddresses,
        FolderCreationResult result
    ) {
        LOGGER.info(
            "Creating wallet directories for addresses: {}",
            walletAddresses
        );

        for (String walletAddress : walletAddresses) {
            try {
                Path walletPath = createWalletDirectory(walletAddress);
                result.addCreatedWalletDir(
                    walletAddress,
                    walletPath.toString()
                );
                LOGGER.info(
                    "Created wallet directory: {} -> {}",
                    walletAddress,
                    walletPath
                );
            } catch (IOException e) {
                LOGGER.error(
                    "Failed to create wallet directory for: {}",
                    walletAddress,
                    e
                );
                result.addFailedWalletDir(walletAddress, e.getMessage());
            }
        }
    }

    /**
     * Creates a directory for a specific wallet address
     */
    private Path createWalletDirectory(String walletAddress)
        throws IOException {
        // Create shortened directory name from wallet address
        String dirName = createWalletDirName(walletAddress);
        Path walletPath = Paths.get(baseDirectory, dirName);

        if (!Files.exists(walletPath)) {
            Files.createDirectories(walletPath);
            LOGGER.debug("Created directory: {}", walletPath);
        } else {
            LOGGER.debug("Directory already exists: {}", walletPath);
        }

        return walletPath;
    }

    /**
     * Creates a readable directory name from wallet address
     */
    private String createWalletDirName(String walletAddress) {
        // Use first and last 8 characters for readability
        if (walletAddress.length() > 16) {
            return String.format(
                "wallet_%s...%s",
                walletAddress.substring(0, 8),
                walletAddress.substring(walletAddress.length() - 8)
            );
        }
        return "wallet_" + walletAddress;
    }

    /**
     * Creates folder tokens for a specific wallet
     */
    private CompletableFuture<Void> createFoldersForWallet(
        String walletAddress,
        Set<String> folderNames,
        FolderCreationResult result
    ) {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info(
                "Creating {} folder tokens for wallet: {}",
                folderNames.size(),
                walletAddress
            );

            // Create dummy wallet info for folder token service
            RevWalletInfo walletInfo = createDummyWalletInfo(walletAddress);
            FolderTokenManager manager = folderTokenService.getOrCreateManager(
                walletInfo,
                blockchainClient
            );

            for (String folderName : folderNames) {
                try {
                    FolderToken token = manager.retrieveFolderToken(
                        folderName,
                        walletAddress
                    );
                    result.addCreatedFolderToken(
                        walletAddress,
                        folderName,
                        token.getFolderPath()
                    );
                    LOGGER.info(
                        "Created folder token: {} -> {}",
                        folderName,
                        token.getFolderPath()
                    );

                    // Track created folders
                    createdFolders
                        .computeIfAbsent(walletAddress, k ->
                            ConcurrentHashMap.newKeySet()
                        )
                        .add(folderName);
                } catch (F1r3DriveError e) {
                    LOGGER.error(
                        "Failed to create folder token: {} for wallet: {}",
                        folderName,
                        walletAddress,
                        e
                    );
                    result.addFailedFolderToken(
                        walletAddress,
                        folderName,
                        e.getMessage()
                    );
                }
            }
        });
    }

    /**
     * Processes a single wallet address for folder creation
     */
    private CompletableFuture<FolderCreationResult> processWalletAddress(
        String walletAddress
    ) {
        return tokenDiscovery
            .discoverFolderTokens(walletAddress)
            .thenApply(folderNames -> {
                FolderCreationResult result = new FolderCreationResult();

                try {
                    // Create wallet directory
                    Path walletPath = createWalletDirectory(walletAddress);
                    result.addCreatedWalletDir(
                        walletAddress,
                        walletPath.toString()
                    );

                    // Create folder tokens
                    RevWalletInfo walletInfo = createDummyWalletInfo(
                        walletAddress
                    );
                    FolderTokenManager manager =
                        folderTokenService.getOrCreateManager(
                            walletInfo,
                            blockchainClient
                        );

                    for (String folderName : folderNames) {
                        try {
                            FolderToken token = manager.retrieveFolderToken(
                                folderName,
                                walletAddress
                            );
                            result.addCreatedFolderToken(
                                walletAddress,
                                folderName,
                                token.getFolderPath()
                            );
                        } catch (F1r3DriveError e) {
                            result.addFailedFolderToken(
                                walletAddress,
                                folderName,
                                e.getMessage()
                            );
                        }
                    }
                } catch (IOException e) {
                    result.addFailedWalletDir(walletAddress, e.getMessage());
                }

                return result;
            });
    }

    /**
     * Creates dummy wallet info for folder token service
     * Note: In production, this should use proper wallet authentication
     */
    private RevWalletInfo createDummyWalletInfo(String walletAddress) {
        // Create dummy private key for folder management
        byte[] dummyPrivateKey = new byte[32];
        // Fill with address-based data for consistency
        byte[] addressBytes = walletAddress.getBytes();
        System.arraycopy(
            addressBytes,
            0,
            dummyPrivateKey,
            0,
            Math.min(addressBytes.length, 32)
        );

        return new RevWalletInfo(walletAddress, dummyPrivateKey);
    }

    /**
     * Performs periodic discovery and folder creation
     * @param intervalMinutes Interval in minutes between scans
     */
    public void startPeriodicDiscovery(int intervalMinutes) {
        LOGGER.info(
            "Starting periodic token discovery every {} minutes",
            intervalMinutes
        );

        java.util.concurrent.Executors.newScheduledThreadPool(
            1
        ).scheduleAtFixedRate(
            () -> {
                try {
                    discoverAndCreateFolders().get();
                } catch (Exception e) {
                    LOGGER.error("Error during periodic discovery", e);
                }
            },
            0, // Initial delay
            intervalMinutes,
            java.util.concurrent.TimeUnit.MINUTES
        );
    }

    /**
     * Performs periodic discovery and folder creation for a specific wallet
     * @param walletAddress The wallet address to monitor
     * @param intervalMinutes Interval in minutes between scans
     */
    public void startPeriodicDiscoveryForWallet(
        String walletAddress,
        int intervalMinutes
    ) {
        LOGGER.info(
            "Starting periodic token discovery for wallet {} every {} minutes",
            walletAddress,
            intervalMinutes
        );

        java.util.concurrent.Executors.newScheduledThreadPool(
            1
        ).scheduleAtFixedRate(
            () -> {
                try {
                    discoverAndCreateFoldersForWallets(
                        Set.of(walletAddress)
                    ).get();
                } catch (Exception e) {
                    LOGGER.error(
                        "Error during periodic discovery for wallet " +
                            walletAddress,
                        e
                    );
                }
            },
            0, // Initial delay
            intervalMinutes,
            java.util.concurrent.TimeUnit.MINUTES
        );
    }

    /**
     * Gets statistics about created folders
     */
    public FolderCreationStats getStats() {
        int totalWallets = createdFolders.size();
        int totalFolders = createdFolders
            .values()
            .stream()
            .mapToInt(Set::size)
            .sum();

        return new FolderCreationStats(totalWallets, totalFolders);
    }

    /**
     * Checks if a folder has been created for a wallet
     */
    public boolean isFolderCreated(String walletAddress, String folderName) {
        return createdFolders
            .getOrDefault(walletAddress, Set.of())
            .contains(folderName);
    }

    /**
     * Manually creates folders for a specific wallet
     */
    public CompletableFuture<FolderCreationResult> createFoldersForWallet(
        String walletAddress
    ) {
        LOGGER.info("Manually creating folders for wallet: {}", walletAddress);
        return processWalletAddress(walletAddress);
    }

    /**
     * Shuts down the auto folder creator
     */
    public void shutdown() {
        LOGGER.info("Shutting down AutoFolderCreator...");
        tokenDiscovery.shutdown();
    }

    /**
     * Result class for folder creation operations
     */
    public static class FolderCreationResult {

        private final Map<String, String> createdWalletDirs =
            new ConcurrentHashMap<>();
        private final Map<String, String> failedWalletDirs =
            new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> createdFolderTokens =
            new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> failedFolderTokens =
            new ConcurrentHashMap<>();

        public void addCreatedWalletDir(String walletAddress, String path) {
            createdWalletDirs.put(walletAddress, path);
        }

        public void addFailedWalletDir(String walletAddress, String error) {
            failedWalletDirs.put(walletAddress, error);
        }

        public void addCreatedFolderToken(
            String walletAddress,
            String folderName,
            String path
        ) {
            createdFolderTokens
                .computeIfAbsent(walletAddress, k -> new ConcurrentHashMap<>())
                .put(folderName, path);
        }

        public void addFailedFolderToken(
            String walletAddress,
            String folderName,
            String error
        ) {
            failedFolderTokens
                .computeIfAbsent(walletAddress, k -> new ConcurrentHashMap<>())
                .put(folderName, error);
        }

        public void merge(FolderCreationResult other) {
            this.createdWalletDirs.putAll(other.createdWalletDirs);
            this.failedWalletDirs.putAll(other.failedWalletDirs);
            this.createdFolderTokens.putAll(other.createdFolderTokens);
            this.failedFolderTokens.putAll(other.failedFolderTokens);
        }

        public Map<String, String> getCreatedWalletDirs() {
            return createdWalletDirs;
        }

        public Map<String, String> getFailedWalletDirs() {
            return failedWalletDirs;
        }

        public Map<String, Map<String, String>> getCreatedFolderTokens() {
            return createdFolderTokens;
        }

        public Map<String, Map<String, String>> getFailedFolderTokens() {
            return failedFolderTokens;
        }

        public int getTotalFolderTokens() {
            return createdFolderTokens
                .values()
                .stream()
                .mapToInt(Map::size)
                .sum();
        }

        @Override
        public String toString() {
            return String.format(
                "FolderCreationResult{walletDirs=%d, folderTokens=%d, failures=%d}",
                createdWalletDirs.size(),
                getTotalFolderTokens(),
                failedWalletDirs.size() + failedFolderTokens.size()
            );
        }
    }

    /**
     * Statistics class for folder creation
     */
    public static class FolderCreationStats {

        private final int totalWallets;
        private final int totalFolders;

        public FolderCreationStats(int totalWallets, int totalFolders) {
            this.totalWallets = totalWallets;
            this.totalFolders = totalFolders;
        }

        public int getTotalWallets() {
            return totalWallets;
        }

        public int getTotalFolders() {
            return totalFolders;
        }

        @Override
        public String toString() {
            return String.format(
                "FolderCreationStats{wallets=%d, folders=%d}",
                totalWallets,
                totalFolders
            );
        }
    }
}
