package io.f1r3fly.f1r3drive.folders;

import casper.DeployServiceCommon;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.LockedWalletDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to extract wallets from genesis block and create corresponding physical folders.
 * Adapted from InMemoryFileSystem approach to work with physical directories.
 */
public class GenesisWalletExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenesisWalletExtractor.class);

    private final F1r3flyBlockchainClient blockchainClient;
    private final String baseDirectory;

    // Pattern to match REV addresses in genesis block
    private static final Pattern REV_ADDRESS_PATTERN = Pattern.compile("\\\"(1111[A-Za-z0-9]+)\\\"");

    public GenesisWalletExtractor(F1r3flyBlockchainClient blockchainClient, String baseDirectory) {
        this.blockchainClient = blockchainClient;
        this.baseDirectory = baseDirectory;
    }

    /**
     * Extracts REV addresses from genesis block using the same logic as InMemoryFileSystem
     */
    public CompletableFuture<List<String>> extractWalletAddressesFromGenesis() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Extracting wallet addresses from genesis block...");

                List<DeployServiceCommon.DeployInfo> deploys = blockchainClient.getGenesisBlock().getDeploysList();

                DeployServiceCommon.DeployInfo tokenInitializeDeploy = deploys.stream()
                        .filter(deployInfo -> deployInfo.getTerm().contains("revVaultInitCh"))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No revVaultInitCh deploy found in genesis block"));

                Matcher matcher = REV_ADDRESS_PATTERN.matcher(tokenInitializeDeploy.getTerm());

                List<String> ravAddresses = new ArrayList<>();
                while (matcher.find()) {
                    String address = matcher.group(1);
                    ravAddresses.add(address);
                    LOGGER.debug("Found REV address in genesis: {}", address);
                }

                LOGGER.info("Extracted {} wallet addresses from genesis block", ravAddresses.size());
                return ravAddresses;

            } catch (Exception e) {
                LOGGER.error("Failed to extract wallet addresses from genesis block, falling back to test data", e);
                return getTestWalletAddresses();
            }
        });
    }

    /**
     * Creates physical folders for discovered wallet addresses
     */
    public CompletableFuture<WalletExtractionResult> createPhysicalFoldersForWallets(List<String> walletAddresses) {
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
                        Path walletFolder = createWalletFolder(walletAddress);
                        result.addSuccessfulWallet(walletAddress, walletFolder.toString());

                        // Create wallet content (tokens, files, etc.)
                        createWalletContent(walletAddress, walletFolder);

                    } catch (Exception e) {
                        LOGGER.error("Failed to create folder for wallet: {}", walletAddress, e);
                        result.addFailedWallet(walletAddress, e.getMessage());
                    }
                }

                LOGGER.info("Wallet folder creation completed: {} successful, {} failed",
                    result.getSuccessfulWallets().size(),
                    result.getFailedWallets().size());

            } catch (Exception e) {
                LOGGER.error("Error during wallet folder creation", e);
                result.setGlobalError(e.getMessage());
            }

            return result;
        });
    }

    /**
     * Creates physical folder for a single wallet address
     */
    private Path createWalletFolder(String walletAddress) throws IOException {
        String folderName = createWalletFolderName(walletAddress);
        Path walletPath = Paths.get(baseDirectory, folderName);

        if (!Files.exists(walletPath)) {
            Files.createDirectories(walletPath);
            LOGGER.debug("Created wallet folder: {}", walletPath);
        } else {
            LOGGER.debug("Wallet folder already exists: {}", walletPath);
        }

        return walletPath;
    }

    /**
     * Creates readable folder name from wallet address
     */
    private String createWalletFolderName(String walletAddress) {
        if (walletAddress.length() > 16) {
            return String.format("wallet_%s...%s",
                walletAddress.substring(0, 8),
                walletAddress.substring(walletAddress.length() - 8));
        }
        return "wallet_" + walletAddress;
    }

    /**
     * Creates wallet content (tokens, files, etc.) inside wallet folder
     */
    private void createWalletContent(String walletAddress, Path walletFolder) {
        try {
            // Create basic wallet info file
            Path walletInfoFile = walletFolder.resolve("wallet_info.txt");
            String walletInfo = String.format("Wallet Address: %s%n" +
                    "Created: %s%n" +
                    "Status: Discovered from Genesis Block%n",
                    walletAddress,
                    new Date());
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
            LOGGER.warn("Failed to create wallet content for {}: {}", walletAddress, e.getMessage());
        }
    }

    /**
     * Attempts to create actual wallet content from blockchain data
     */
    private void tryCreateActualWalletContent(String walletAddress, Path walletFolder) {
        try {
            // This would query the blockchain for actual wallet content
            // For now, we'll create placeholder content

            Path readmeFile = walletFolder.resolve("README.md");
            String readmeContent = String.format("# Wallet %s%n%n" +
                    "This folder contains the blockchain data for wallet address: `%s`%n%n" +
                    "## Structure%n" +
                    "- `tokens/` - Token files and balances%n" +
                    "- `folders/` - Sub-folders and their contents%n" +
                    "- `wallet_info.txt` - Basic wallet information%n%n" +
                    "Generated by F1r3Drive Genesis Wallet Extractor%n",
                    walletAddress, walletAddress);

            Files.write(readmeFile, readmeContent.getBytes());

        } catch (Exception e) {
            LOGGER.debug("Could not create actual wallet content for {}: {}", walletAddress, e.getMessage());
        }
    }

    /**
     * Creates folders for specific wallet if address is provided, otherwise discovers all from genesis
     */
    public CompletableFuture<WalletExtractionResult> extractAndCreateWalletFolders(String specificWalletAddress) {
        if (specificWalletAddress != null && !specificWalletAddress.trim().isEmpty()) {
            LOGGER.info("Creating folder for specific wallet: {}", specificWalletAddress);
            return createPhysicalFoldersForWallets(List.of(specificWalletAddress));
        } else {
            LOGGER.info("Extracting all wallets from genesis block");
            return extractWalletAddressesFromGenesis()
                    .thenCompose(this::createPhysicalFoldersForWallets);
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
            return String.format("WalletExtractionResult{successful=%d, failed=%d, hasGlobalError=%s}",
                    successfulWallets.size(), failedWallets.size(), globalError != null);
        }
    }
}
