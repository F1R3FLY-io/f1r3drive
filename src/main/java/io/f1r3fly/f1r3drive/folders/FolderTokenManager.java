package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

/**
 * Manages folder tokens from blockchain in the /Users/jedoan/demo-f1r3drive directory
 * Provides automatic folder deletion when the application closes
 * Uses in-memory storage for controlling folder token operations
 */
public class FolderTokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        FolderTokenManager.class
    );
    private static final String DEFAULT_BASE_DIRECTORY =
        "/Users/jedoan/demo-f1r3drive";

    // In-memory storage for controlling folder tokens
    private final Map<String, FolderToken> folderTokens =
        new ConcurrentHashMap<>();

    // Blockchain context for working with data
    private final BlockchainContext blockchainContext;

    // Flag to track manager state
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    // Configurable base directory path
    private final String baseDirectory;

    public FolderTokenManager(BlockchainContext blockchainContext) {
        this(blockchainContext, DEFAULT_BASE_DIRECTORY);
    }

    public FolderTokenManager(
        BlockchainContext blockchainContext,
        String baseDirectory
    ) {
        this.blockchainContext = blockchainContext;
        this.baseDirectory = baseDirectory;

        // Register hook for automatic cleanup when application closes
        Runtime.getRuntime().addShutdownHook(
            new Thread(this::cleanup, "FolderTokenManager-Cleanup")
        );

        // Create base directory if it doesn't exist
        createBaseDirectoryIfNotExists();

        LOGGER.info(
            "FolderTokenManager initialized for directory: {}",
            this.baseDirectory
        );
    }

    /**
     * Retrieves folder token from blockchain and creates corresponding folder
     *
     * @param folderName name of the folder
     * @param walletAddress wallet address for token retrieval
     * @return FolderToken object with token information
     * @throws F1r3DriveError if an error occurred while working with blockchain
     */
    public FolderToken retrieveFolderToken(
        String folderName,
        String walletAddress
    ) throws F1r3DriveError {
        if (isShutdown.get()) {
            throw new IllegalStateException(
                "FolderTokenManager is already closed"
            );
        }

        LOGGER.info(
            "Retrieving folder token '{}' for wallet: {}",
            folderName,
            walletAddress
        );

        try {
            // Get folder token from blockchain
            String folderTokenData = getFolderTokenFromBlockchain(
                folderName,
                walletAddress
            );

            // Create folder in filesystem
            Path folderPath = createFolderIfNotExists(folderName);

            // Create FolderToken object
            FolderToken folderToken = new FolderToken(
                folderName,
                folderPath.toString(),
                walletAddress,
                folderTokenData,
                System.currentTimeMillis()
            );

            // Save in in-memory storage
            folderTokens.put(folderName, folderToken);

            LOGGER.info(
                "Folder token '{}' successfully retrieved and saved",
                folderName
            );
            return folderToken;
        } catch (Exception e) {
            LOGGER.error(
                "Error retrieving folder token '{}': {}",
                folderName,
                e.getMessage(),
                e
            );
            throw new F1r3DriveError(
                "Unable to retrieve folder token: " + folderName,
                e
            );
        }
    }

    /**
     * Gets folder token from blockchain using RhoLang query
     */
    private String getFolderTokenFromBlockchain(
        String folderName,
        String walletAddress
    ) throws F1r3DriveError {
        try {
            // Create RhoLang code for getting folder token
            String rholangQuery = String.format(
                "new return in { " +
                    "  for (@folderToken <- @\"%s_folder_%s\") { " +
                    "    return!(folderToken) " +
                    "  } " +
                    "}",
                walletAddress,
                folderName
            );

            LOGGER.debug("Executing RhoLang query: {}", rholangQuery);

            // Execute query through blockchain client
            RhoTypes.Expr result = blockchainContext
                .getBlockchainClient()
                .exploratoryDeploy(rholangQuery);

            if (result.hasGString()) {
                String tokenData = result.getGString();
                LOGGER.debug("Retrieved folder token: {}", tokenData);
                return tokenData;
            } else {
                // If token doesn't exist, create new one
                return createNewFolderToken(folderName, walletAddress);
            }
        } catch (Exception e) {
            LOGGER.error("Error retrieving folder token from blockchain", e);
            throw new F1r3DriveError("Blockchain query error", e);
        }
    }

    /**
     * Creates new folder token in blockchain
     */
    private String createNewFolderToken(String folderName, String walletAddress)
        throws F1r3DriveError {
        try {
            String tokenId = generateTokenId(folderName, walletAddress);
            String timestamp = String.valueOf(System.currentTimeMillis());

            String rholangDeploy = String.format(
                "new folderCh in { " +
                    "  folderCh!(\"{\\\"tokenId\\\": \\\"%s\\\", \\\"folderName\\\": \\\"%s\\\", \\\"owner\\\": \\\"%s\\\", \\\"created\\\": %s}\") | " +
                    "  @\"%s_folder_%s\"!(folderCh) " +
                    "}",
                tokenId,
                folderName,
                walletAddress,
                timestamp,
                walletAddress,
                folderName
            );

            LOGGER.debug("Creating new folder token: {}", rholangDeploy);

            // Deploy new token to blockchain
            blockchainContext
                .getDeployDispatcher()
                .enqueueDeploy(
                    new io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher.Deployment(
                        rholangDeploy,
                        true,
                        F1r3flyBlockchainClient.RHOLANG,
                        walletAddress,
                        blockchainContext.getWalletInfo().signingKey(),
                        System.currentTimeMillis()
                    )
                );

            String tokenData = String.format(
                "{\"tokenId\": \"%s\", \"folderName\": \"%s\", \"owner\": \"%s\", \"created\": %s}",
                tokenId,
                folderName,
                walletAddress,
                timestamp
            );

            LOGGER.info("New folder token created: {}", tokenData);
            return tokenData;
        } catch (Exception e) {
            LOGGER.error("Error creating folder token", e);
            throw new F1r3DriveError("Failed to create folder token", e);
        }
    }

    /**
     * Generates unique ID for folder token
     */
    private String generateTokenId(String folderName, String walletAddress) {
        return String.format(
            "folder_%s_%s_%d",
            folderName.replaceAll("[^a-zA-Z0-9]", "_"),
            walletAddress.substring(0, Math.min(8, walletAddress.length())),
            System.currentTimeMillis()
        );
    }

    /**
     * Creates folder in filesystem if it doesn't exist
     */
    private Path createFolderIfNotExists(String folderName) throws IOException {
        Path folderPath = Paths.get(baseDirectory, folderName);

        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
            LOGGER.info("Created folder: {}", folderPath);
        } else {
            LOGGER.debug("Folder already exists: {}", folderPath);
        }

        return folderPath;
    }

    /**
     * Creates base directory if it doesn't exist
     */
    private void createBaseDirectoryIfNotExists() {
        try {
            Path basePath = Paths.get(baseDirectory);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
                LOGGER.info("Created base directory: {}", baseDirectory);
            }
        } catch (IOException e) {
            LOGGER.error(
                "Failed to create base directory: {}",
                baseDirectory,
                e
            );
        }
    }

    /**
     * Gets folder token from in-memory storage
     */
    public FolderToken getFolderToken(String folderName) {
        return folderTokens.get(folderName);
    }

    /**
     * Gets all folder tokens
     */
    public Map<String, FolderToken> getAllFolderTokens() {
        return Map.copyOf(folderTokens);
    }

    /**
     * Removes folder token and corresponding folder
     */
    public boolean removeFolderToken(String folderName) {
        if (isShutdown.get()) {
            LOGGER.warn(
                "Attempt to remove folder token after manager shutdown: {}",
                folderName
            );
            return false;
        }

        FolderToken token = folderTokens.remove(folderName);
        if (token != null) {
            try {
                deleteFolderIfExists(token.getFolderPath());
                LOGGER.info("Folder token '{}' removed", folderName);
                return true;
            } catch (IOException e) {
                LOGGER.error(
                    "Error deleting folder: {}",
                    token.getFolderPath(),
                    e
                );
                // Return token back if failed to delete folder
                folderTokens.put(folderName, token);
                return false;
            }
        }
        return false;
    }

    /**
     * Deletes folder from filesystem
     */
    private void deleteFolderIfExists(String folderPath) throws IOException {
        Path path = Paths.get(folderPath);
        if (Files.exists(path)) {
            // Recursively delete folder and all contents
            Files.walk(path)
                .sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount()) // Files first, then folders
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete: {}", p, e);
                    }
                });

            LOGGER.info("Folder deleted: {}", folderPath);
        }
    }

    /**
     * Checks if folder contains any important files before deletion
     */
    public boolean isFolderSafeToDelete(String folderName) {
        FolderToken token = folderTokens.get(folderName);
        if (token == null) {
            return true;
        }

        try {
            Path folderPath = Paths.get(token.getFolderPath());
            if (!Files.exists(folderPath)) {
                return true;
            }

            // Check if folder contains files (except hidden system files)
            return Files.list(folderPath).noneMatch(p ->
                !p.getFileName().toString().startsWith(".")
            );
        } catch (IOException e) {
            LOGGER.warn("Error checking folder: {}", token.getFolderPath(), e);
            return false;
        }
    }

    /**
     * Performs cleanup of all folders when application closes
     */
    private void cleanup() {
        if (isShutdown.compareAndSet(false, true)) {
            LOGGER.info("Starting FolderTokenManager cleanup...");

            for (Map.Entry<
                String,
                FolderToken
            > entry : folderTokens.entrySet()) {
                String folderName = entry.getKey();
                FolderToken token = entry.getValue();

                try {
                    if (isFolderSafeToDelete(folderName)) {
                        deleteFolderIfExists(token.getFolderPath());
                        LOGGER.info(
                            "Folder '{}' deleted on shutdown",
                            folderName
                        );
                    } else {
                        LOGGER.warn(
                            "Folder '{}' contains files and will not be deleted",
                            folderName
                        );
                    }
                } catch (IOException e) {
                    LOGGER.error(
                        "Error deleting folder '{}' on shutdown",
                        folderName,
                        e
                    );
                }
            }

            folderTokens.clear();
            LOGGER.info("FolderTokenManager cleanup completed");
        }
    }

    /**
     * Gets number of managed folders
     */
    public int getFolderCount() {
        return folderTokens.size();
    }

    /**
     * Checks if manager is active
     */
    public boolean isActive() {
        return !isShutdown.get();
    }
}
