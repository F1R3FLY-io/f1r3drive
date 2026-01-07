package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for integrating folder token management with the main F1r3Drive application
 * Provides high-level API for working with folder tokens
 */
public class FolderTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        FolderTokenService.class
    );

    // Idle time in milliseconds after which a token is considered stale
    private static final long DEFAULT_STALE_TIME_MS = TimeUnit.HOURS.toMillis(
        1
    ); // 1 hour

    // Cleanup interval for stale tokens
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(
        15
    ); // 15 minutes

    private final Map<String, FolderTokenManager> walletManagers =
        new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);
    private volatile boolean isRunning = true;

    public FolderTokenService() {
        // Start periodic cleanup of stale tokens
        scheduler.scheduleWithFixedDelay(
            this::cleanupStaleTokens,
            CLEANUP_INTERVAL_MS,
            CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("FolderTokenService started");
    }

    /**
     * Creates or gets token manager for specified wallet
     */
    public FolderTokenManager getOrCreateManager(
        RevWalletInfo walletInfo,
        F1r3flyBlockchainClient blockchainClient
    ) {
        String walletAddress = walletInfo.revAddress();

        return walletManagers.computeIfAbsent(walletAddress, addr -> {
            LOGGER.info("Creating FolderTokenManager for wallet: {}", addr);

            // Create blockchain context for manager
            BlockchainContext context = new BlockchainContext(
                walletInfo,
                new io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher(
                    blockchainClient,
                    new io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager()
                )
            );

            return new FolderTokenManager(context);
        });
    }

    /**
     * Asynchronously retrieves folder token for specified wallet
     */
    public CompletableFuture<FolderToken> retrieveFolderTokenAsync(
        String walletAddress,
        String folderName
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FolderTokenManager manager = walletManagers.get(walletAddress);
                if (manager == null) {
                    throw new IllegalStateException(
                        "Manager not found for wallet: " + walletAddress
                    );
                }

                return manager.retrieveFolderToken(folderName, walletAddress);
            } catch (Exception e) {
                LOGGER.error("Error retrieving folder token asynchronously", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Retrieves folder token synchronously
     */
    public FolderToken retrieveFolderToken(
        String walletAddress,
        String folderName
    ) throws F1r3DriveError {
        FolderTokenManager manager = walletManagers.get(walletAddress);
        if (manager == null) {
            throw new F1r3DriveError(
                "Token manager not found for wallet: " + walletAddress
            );
        }

        if (!manager.isActive()) {
            throw new F1r3DriveError(
                "Token manager inactive for wallet: " + walletAddress
            );
        }

        FolderToken token = manager.retrieveFolderToken(
            folderName,
            walletAddress
        );
        token.updateLastAccess(); // Update last access time

        return token;
    }

    /**
     * Gets existing folder token from cache
     */
    public FolderToken getFolderToken(String walletAddress, String folderName) {
        FolderTokenManager manager = walletManagers.get(walletAddress);
        if (manager != null) {
            FolderToken token = manager.getFolderToken(folderName);
            if (token != null) {
                token.updateLastAccess();
            }
            return token;
        }
        return null;
    }

    /**
     * Removes folder token
     */
    public boolean removeFolderToken(String walletAddress, String folderName) {
        FolderTokenManager manager = walletManagers.get(walletAddress);
        if (manager != null) {
            boolean removed = manager.removeFolderToken(folderName);
            if (removed) {
                LOGGER.info(
                    "Folder token '{}' removed for wallet: {}",
                    folderName,
                    walletAddress
                );
            }
            return removed;
        }
        return false;
    }

    /**
     * Gets all folder tokens for specified wallet
     */
    public Map<String, FolderToken> getAllFolderTokens(String walletAddress) {
        FolderTokenManager manager = walletManagers.get(walletAddress);
        if (manager != null) {
            Map<String, FolderToken> tokens = manager.getAllFolderTokens();
            // Update last access time for all tokens
            tokens.values().forEach(FolderToken::updateLastAccess);
            return tokens;
        }
        return Map.of();
    }

    /**
     * Gets statistics for all managed wallets
     */
    public FolderTokenStats getStats() {
        int totalWallets = walletManagers.size();
        int totalFolders = walletManagers
            .values()
            .stream()
            .mapToInt(FolderTokenManager::getFolderCount)
            .sum();

        int activeManagers = (int) walletManagers
            .values()
            .stream()
            .filter(FolderTokenManager::isActive)
            .count();

        return new FolderTokenStats(totalWallets, totalFolders, activeManagers);
    }

    /**
     * Checks if folder is safe to delete
     */
    public boolean isFolderSafeToDelete(
        String walletAddress,
        String folderName
    ) {
        FolderTokenManager manager = walletManagers.get(walletAddress);
        return manager != null && manager.isFolderSafeToDelete(folderName);
    }

    /**
     * Performs forced cleanup of stale tokens
     */
    public void cleanupStaleTokens() {
        if (!isRunning) {
            return;
        }

        LOGGER.debug("Starting cleanup of stale tokens...");

        int cleanedTokens = 0;
        int cleanedWallets = 0;

        for (Map.Entry<
            String,
            FolderTokenManager
        > entry : walletManagers.entrySet()) {
            String walletAddress = entry.getKey();
            FolderTokenManager manager = entry.getValue();

            if (!manager.isActive()) {
                walletManagers.remove(walletAddress);
                cleanedWallets++;
                LOGGER.info(
                    "Removed inactive manager for wallet: {}",
                    walletAddress
                );
                continue;
            }

            // Find stale tokens
            Map<String, FolderToken> allTokens = manager.getAllFolderTokens();
            List<String> staleTokens = allTokens
                .entrySet()
                .stream()
                .filter(tokenEntry ->
                    tokenEntry.getValue().isStale(DEFAULT_STALE_TIME_MS)
                )
                .filter(tokenEntry ->
                    manager.isFolderSafeToDelete(tokenEntry.getKey())
                )
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            // Remove stale tokens
            for (String folderName : staleTokens) {
                if (manager.removeFolderToken(folderName)) {
                    cleanedTokens++;
                    LOGGER.info(
                        "Removed stale folder token '{}' for wallet: {}",
                        folderName,
                        walletAddress
                    );
                }
            }
        }

        if (cleanedTokens > 0 || cleanedWallets > 0) {
            LOGGER.info(
                "Cleanup completed: removed tokens: {}, removed managers: {}",
                cleanedTokens,
                cleanedWallets
            );
        }
    }

    /**
     * Locks token for operation execution
     */
    public boolean lockToken(String walletAddress, String folderName) {
        FolderToken token = getFolderToken(walletAddress, folderName);
        if (token != null && !token.isLocked()) {
            token.lock();
            LOGGER.debug("Folder token '{}' locked", folderName);
            return true;
        }
        return false;
    }

    /**
     * Unlocks token after operation execution
     */
    public void unlockToken(String walletAddress, String folderName) {
        FolderToken token = getFolderToken(walletAddress, folderName);
        if (token != null) {
            token.unlock();
            LOGGER.debug("Folder token '{}' unlocked", folderName);
        }
    }

    /**
     * Shuts down the service and releases resources
     */
    public void shutdown() {
        if (isRunning) {
            isRunning = false;

            LOGGER.info("Shutting down FolderTokenService...");

            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Managers will automatically clean up through their shutdown hooks
            walletManagers.clear();

            LOGGER.info("FolderTokenService shutdown complete");
        }
    }

    /**
     * Statistics for folder token service operation
     */
    public static class FolderTokenStats {

        private final int totalWallets;
        private final int totalFolders;
        private final int activeManagers;

        public FolderTokenStats(
            int totalWallets,
            int totalFolders,
            int activeManagers
        ) {
            this.totalWallets = totalWallets;
            this.totalFolders = totalFolders;
            this.activeManagers = activeManagers;
        }

        public int getTotalWallets() {
            return totalWallets;
        }

        public int getTotalFolders() {
            return totalFolders;
        }

        public int getActiveManagers() {
            return activeManagers;
        }

        @Override
        public String toString() {
            return String.format(
                "FolderTokenStats{wallets=%d, folders=%d, active=%d}",
                totalWallets,
                totalFolders,
                activeManagers
            );
        }
    }
}
