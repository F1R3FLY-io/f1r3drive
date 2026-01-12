package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

/**
 * Automatically discovers existing tokens from blockchain and creates corresponding folders
 * Integrates TokenDiscovery with FolderTokenService to populate ~/demo-f1r3drive
 */
public class AutoFolderCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AutoFolderCreator.class
    );

    private final TokenDiscovery tokenDiscovery;
    private final FolderTokenService folderTokenService;

    // Token denominations (same as TokenDirectory)
    private static final List<Long> DENOMINATIONS = Arrays.asList(
        1_000_000_000_000_000_000L, // 1 quintillion
        100_000_000_000_000_000L, // 100 quadrillion
        10_000_000_000_000_000L, // 10 quadrillion
        1_000_000_000_000_000L, // 1 quadrillion
        100_000_000_000_000L, // 100 trillion
        10_000_000_000_000L, // 10 trillion
        1_000_000_000_000L, // 1 trillion
        100_000_000_000L, // 100 billion
        10_000_000_000L, // 10 billion
        1_000_000_000L, // 1 billion
        100_000_000L, // 100 million
        10_000_000L, // 10 million
        1_000_000L, // 1 million
        100_000L, // 100K
        10_000L, // 10K
        1_000L, // 1K
        100L, // 100
        10L, // 10
        1L // 1
    );
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
            System.getProperty("user.home") + "/demo-f1r3drive"
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

        // First create .tokens directories in main wallet folders
        createTokenDirectories(discoveryResult.getWalletAddresses(), result);

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
     * Creates .tokens directories in main wallet folders
     */
    private void createTokenDirectories(
        Set<String> walletAddresses,
        FolderCreationResult result
    ) {
        LOGGER.info(
            "Creating .tokens directories in main wallet folders for addresses: {}",
            walletAddresses
        );

        for (String walletAddress : walletAddresses) {
            try {
                Path tokensPath = createTokensDirectory(walletAddress);
                result.addCreatedWalletDir(
                    walletAddress,
                    tokensPath.toString()
                );
                LOGGER.info(
                    "Created .tokens directory: {} -> {}",
                    walletAddress,
                    tokensPath
                );
            } catch (IOException e) {
                LOGGER.error(
                    "Failed to create .tokens directory for: {}",
                    walletAddress,
                    e
                );
                result.addFailedWalletDir(walletAddress, e.getMessage());
            }
        }
    }

    /**
     * Creates a .tokens directory in the main wallet folder
     */
    private Path createTokensDirectory(String walletAddress)
        throws IOException {
        // Find the main wallet directory (full wallet address name)
        Path mainWalletPath = Paths.get(baseDirectory, walletAddress);

        // Check if main wallet directory exists
        if (!Files.exists(mainWalletPath)) {
            LOGGER.debug(
                "Main wallet directory doesn't exist yet: {}",
                mainWalletPath
            );
            return null;
        }

        // Create .tokens directory inside the main wallet folder
        Path tokensPath = mainWalletPath.resolve(".tokens");

        if (!Files.exists(tokensPath)) {
            Files.createDirectories(tokensPath);

            // Create physical token files based on wallet balance
            try {
                createTokenFilesForWallet(walletAddress, tokensPath);
                LOGGER.debug(
                    "Created .tokens directory with token files: {}",
                    tokensPath
                );
            } catch (Exception e) {
                LOGGER.warn(
                    "Failed to create token files for wallet {}: {}",
                    walletAddress,
                    e.getMessage()
                );
                LOGGER.debug("Created empty .tokens directory: {}", tokensPath);
            }
        } else {
            LOGGER.debug(".tokens directory already exists: {}", tokensPath);
        }

        return tokensPath;
    }

    /**
     * Creates physical token files based on wallet balance from blockchain
     */
    private void createTokenFilesForWallet(
        String walletAddress,
        Path tokensPath
    ) throws Exception {
        long balance = 0;
        Map<Long, Integer> tokenMap;

        try {
            // Query wallet balance from blockchain
            balance = getWalletBalance(walletAddress);
            tokenMap = splitBalance(balance);

            LOGGER.info(
                "Creating token files for wallet {} with balance {}: {}",
                walletAddress,
                balance,
                tokenMap
            );
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to get balance from blockchain for wallet {}, creating demo tokens: {}",
                walletAddress,
                e.getMessage()
            );

            // Fallback: create demo tokens when blockchain is unavailable
            tokenMap = createDemoTokenMap();
            LOGGER.info(
                "Creating demo token files for wallet {}: {}",
                walletAddress,
                tokenMap
            );
        }

        if (tokenMap.isEmpty()) {
            LOGGER.info("No tokens to create for wallet {}", walletAddress);
            return;
        }

        // Create physical token files
        for (Map.Entry<Long, Integer> entry : tokenMap.entrySet()) {
            long denomination = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                String tokenFileName = denomination + "-REV." + i + ".token";
                Path tokenFilePath = tokensPath.resolve(tokenFileName);

                // Create empty token file
                Files.createFile(tokenFilePath);
                LOGGER.debug("Created token file: {}", tokenFileName);
            }
        }

        LOGGER.info(
            "Created {} token files for wallet {}",
            tokenMap.values().stream().mapToInt(Integer::intValue).sum(),
            walletAddress
        );
    }

    /**
     * Gets wallet balance from blockchain
     */
    private long getWalletBalance(String walletAddress) throws F1r3DriveError {
        String checkBalanceRho = RholangExpressionConstructor.checkBalanceRho(
            walletAddress
        );
        RhoTypes.Expr expr = blockchainClient.exploratoryDeploy(
            checkBalanceRho
        );

        if (!expr.hasGInt()) {
            throw new F1r3DriveError(
                "Invalid balance data for wallet: " + walletAddress
            );
        }

        return expr.getGInt();
    }

    /**
     * Splits balance into denominations (same logic as TokenDirectory)
     */
    private Map<Long, Integer> splitBalance(long balance) {
        Map<Long, Integer> tokenMap = new HashMap<>();

        for (long denomination : DENOMINATIONS) {
            int count = (int) (balance / denomination);
            if (count > 0) {
                tokenMap.put(denomination, count);
                balance %= denomination;
            }
        }

        return tokenMap;
    }

    /**
     * Creates demo token map for testing when blockchain is unavailable
     */
    private Map<Long, Integer> createDemoTokenMap() {
        Map<Long, Integer> tokenMap = new HashMap<>();
        // Create 5 tokens of 10 quadrillion denomination
        tokenMap.put(10_000_000_000_000_000L, 5);
        return tokenMap;
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
                    // Create .tokens directory in main wallet folder
                    Path tokensPath = createTokensDirectory(walletAddress);
                    if (tokensPath != null) {
                        result.addCreatedWalletDir(
                            walletAddress,
                            tokensPath.toString()
                        );
                    }

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
