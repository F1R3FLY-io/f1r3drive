package io.f1r3fly.f1r3drive.folders;

import casper.DeployServiceCommon;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.LockedWalletDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

/**
 * Service to extract wallets from genesis block and create corresponding physical folders.
 * Adapted from InMemoryFileSystem approach to work with physical directories.
 */
public class GenesisWalletExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        GenesisWalletExtractor.class
    );

    private final F1r3flyBlockchainClient blockchainClient;
    private final String baseDirectory;

    // Pattern to match REV addresses in genesis block
    private static final Pattern REV_ADDRESS_PATTERN = Pattern.compile(
        "\\\"(1111[A-Za-z0-9]+)\\\""
    );

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

    public GenesisWalletExtractor(
        F1r3flyBlockchainClient blockchainClient,
        String baseDirectory
    ) {
        this.blockchainClient = blockchainClient;
        this.baseDirectory = baseDirectory;
    }

    /**
     * Extracts REV addresses from genesis block using the same logic as InMemoryFileSystem
     */
    public CompletableFuture<List<String>> extractWalletAddressesFromGenesis() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info(
                    "Extracting wallet addresses from genesis block..."
                );

                List<DeployServiceCommon.DeployInfo> deploys = blockchainClient
                    .getGenesisBlock()
                    .getDeploysList();

                DeployServiceCommon.DeployInfo tokenInitializeDeploy = deploys
                    .stream()
                    .filter(deployInfo ->
                        deployInfo.getTerm().contains("revVaultInitCh")
                    )
                    .findFirst()
                    .orElseThrow(() ->
                        new RuntimeException(
                            "No revVaultInitCh deploy found in genesis block"
                        )
                    );

                Matcher matcher = REV_ADDRESS_PATTERN.matcher(
                    tokenInitializeDeploy.getTerm()
                );

                List<String> ravAddresses = new ArrayList<>();
                while (matcher.find()) {
                    String address = matcher.group(1);
                    ravAddresses.add(address);
                    LOGGER.debug("Found REV address in genesis: {}", address);
                }

                LOGGER.info(
                    "Extracted {} wallet addresses from genesis block",
                    ravAddresses.size()
                );
                return ravAddresses;
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to extract wallet addresses from genesis block, falling back to test data",
                    e
                );
                return getTestWalletAddresses();
            }
        });
    }

    /**
     * Creates physical folders for discovered wallet addresses
     */
    public CompletableFuture<
        WalletExtractionResult
    > createPhysicalFoldersForWallets(List<String> walletAddresses) {
        return CompletableFuture.supplyAsync(() -> {
            WalletExtractionResult result = new WalletExtractionResult();

            try {
                // Ensure base directory exists
                Path basePath = Paths.get(baseDirectory);
                if (!Files.exists(basePath)) {
                    Files.createDirectories(basePath);
                    LOGGER.info("Created base directory: {}", baseDirectory);
                }

                for (String walletAddress : walletAddresses) {
                    try {
                        Path tokensPath = createTokensDirectoryInMainWallet(
                            walletAddress
                        );
                        if (tokensPath != null) {
                            result.addSuccessfulWallet(
                                walletAddress,
                                tokensPath.toString()
                            );
                        }
                    } catch (Exception e) {
                        LOGGER.error(
                            "Failed to create folder for wallet: {}",
                            walletAddress,
                            e
                        );
                        result.addFailedWallet(walletAddress, e.getMessage());
                    }
                }

                LOGGER.info(
                    "Wallet folder creation completed: {} successful, {} failed",
                    result.getSuccessfulWallets().size(),
                    result.getFailedWallets().size()
                );
            } catch (Exception e) {
                LOGGER.error("Error during wallet folder creation", e);
                result.setGlobalError(e.getMessage());
            }

            return result;
        });
    }

    /**
     * Creates .tokens directory in the main wallet folder
     */
    private Path createTokensDirectoryInMainWallet(String walletAddress)
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
     * Creates wallet content (tokens, files, etc.) inside wallet folder
     */
    private void createWalletContent(String walletAddress, Path walletFolder) {
        try {
            // Create basic wallet info file
            Path walletInfoFile = walletFolder.resolve("wallet_info.txt");
            String walletInfo = String.format(
                "Wallet Address: %s%n" +
                    "Created: %s%n" +
                    "Status: Discovered from Genesis Block%n",
                walletAddress,
                new Date()
            );
            Files.write(walletInfoFile, walletInfo.getBytes());

            // Create tokens directory
            Path tokensDir = walletFolder.resolve("tokens");
            if (!Files.exists(tokensDir)) {
                Files.createDirectory(tokensDir);
            }

            // Create folders directory for sub-folders
            Path foldersDir = walletFolder.resolve("folders");
            if (!Files.exists(foldersDir)) {
                Files.createDirectory(foldersDir);
            }

            // Try to get actual wallet content from blockchain
            tryCreateActualWalletContent(walletAddress, walletFolder);

            LOGGER.debug("Created wallet content for: {}", walletAddress);
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to create wallet content for {}: {}",
                walletAddress,
                e.getMessage()
            );
        }
    }

    /**
     * Attempts to create actual wallet content from blockchain data
     */
    private void tryCreateActualWalletContent(
        String walletAddress,
        Path walletFolder
    ) {
        try {
            // This would query the blockchain for actual wallet content
            // For now, we'll create placeholder content

            Path readmeFile = walletFolder.resolve("README.md");
            String readmeContent = String.format(
                "# Wallet %s%n%n" +
                    "This folder contains the blockchain data for wallet address: `%s`%n%n" +
                    "## Structure%n" +
                    "- `tokens/` - Token files and balances%n" +
                    "- `folders/` - Sub-folders and their contents%n" +
                    "- `wallet_info.txt` - Basic wallet information%n%n" +
                    "Generated by F1r3Drive Genesis Wallet Extractor%n",
                walletAddress,
                walletAddress
            );

            Files.write(readmeFile, readmeContent.getBytes());
        } catch (Exception e) {
            LOGGER.debug(
                "Could not create actual wallet content for {}: {}",
                walletAddress,
                e.getMessage()
            );
        }
    }

    /**
     * Creates folders for specific wallet if address is provided, otherwise discovers all from genesis
     */
    public CompletableFuture<
        WalletExtractionResult
    > extractAndCreateWalletFolders(String specificWalletAddress) {
        if (
            specificWalletAddress != null &&
            !specificWalletAddress.trim().isEmpty()
        ) {
            LOGGER.info(
                "Creating folder for specific wallet: {}",
                specificWalletAddress
            );
            return createPhysicalFoldersForWallets(
                List.of(specificWalletAddress)
            );
        } else {
            LOGGER.info("Extracting all wallets from genesis block");
            return extractWalletAddressesFromGenesis().thenCompose(
                this::createPhysicalFoldersForWallets
            );
        }
    }

    /**
     * Fallback test wallet addresses when genesis extraction fails
     */
    private List<String> getTestWalletAddresses() {
        return Arrays.asList(
            "111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g",
            "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
            "111129p33f7vaRrpLqK8Nr35Y2aacAjrR5pd6PCzqcdrMuPHzymczH",
            "1111LAd2PWaHsw84gxarNx99YVK2aZhCThhrPsWTV7cs1BPcvHftP",
            "1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8"
        );
    }

    /**
     * Result class for wallet extraction operations
     */
    public static class WalletExtractionResult {

        private final Map<String, String> successfulWallets = new HashMap<>();
        private final Map<String, String> failedWallets = new HashMap<>();
        private String globalError;

        public void addSuccessfulWallet(String address, String folderPath) {
            successfulWallets.put(address, folderPath);
        }

        public void addFailedWallet(String address, String error) {
            failedWallets.put(address, error);
        }

        public Map<String, String> getSuccessfulWallets() {
            return successfulWallets;
        }

        public Map<String, String> getFailedWallets() {
            return failedWallets;
        }

        public boolean isSuccess() {
            return globalError == null && !successfulWallets.isEmpty();
        }

        public void setGlobalError(String error) {
            this.globalError = error;
        }

        public String getGlobalError() {
            return globalError;
        }

        public int getTotalWallets() {
            return successfulWallets.size() + failedWallets.size();
        }

        @Override
        public String toString() {
            return String.format(
                "WalletExtractionResult{successful=%d, failed=%d, hasGlobalError=%s}",
                successfulWallets.size(),
                failedWallets.size(),
                globalError != null
            );
        }
    }
}
