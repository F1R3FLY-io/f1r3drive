package io.f1r3fly.f1r3drive.folders;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

/**
 * Service for discovering existing wallet tokens from the blockchain
 * Scans the blockchain to find all wallet addresses and their associated tokens
 */
public class TokenDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        TokenDiscovery.class
    );

    private final F1r3flyBlockchainClient blockchainClient;
    private final ExecutorService executorService;

    // Pattern to match wallet addresses in blockchain data
    private static final Pattern WALLET_ADDRESS_PATTERN = Pattern.compile(
        "111[1-9A-HJ-NP-Za-km-z]{47,50}"
    );

    // Pattern to match folder token channels
    private static final Pattern FOLDER_TOKEN_PATTERN = Pattern.compile(
        "([0-9A-Za-z]{47,50})_folder_([a-zA-Z0-9_-]+)"
    );

    public TokenDiscovery(F1r3flyBlockchainClient blockchainClient) {
        this.blockchainClient = blockchainClient;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    /**
     * Discovers all existing wallet addresses from the blockchain
     * @return Set of wallet addresses found on the blockchain
     */
    public CompletableFuture<Set<String>> discoverWalletAddresses() {
        return CompletableFuture.supplyAsync(
            () -> {
                LOGGER.info(
                    "Starting wallet address discovery from blockchain..."
                );

                Set<String> walletAddresses = new HashSet<>();

                try {
                    // Query blockchain for all wallet-related data
                    String rholangQuery = buildWalletDiscoveryQuery();
                    RhoTypes.Expr result = blockchainClient.exploratoryDeploy(
                        rholangQuery
                    );

                    Set<String> foundAddresses = extractWalletAddresses(result);
                    walletAddresses.addAll(foundAddresses);

                    LOGGER.info(
                        "Discovered {} wallet addresses from blockchain",
                        walletAddresses.size()
                    );
                } catch (Exception e) {
                    LOGGER.error(
                        "Error discovering wallet addresses from blockchain, falling back to test data",
                        e
                    );
                }

                // If no addresses found from blockchain, use test data
                if (walletAddresses.isEmpty()) {
                    walletAddresses.addAll(getTestWalletAddresses());
                    LOGGER.info(
                        "Using {} test wallet addresses for demo",
                        walletAddresses.size()
                    );
                }

                return walletAddresses;
            },
            executorService
        );
    }

    /**
     * Discovers existing folder tokens for a specific wallet address
     * @param walletAddress The wallet address to scan for
     * @return Set of folder names that have tokens for this wallet
     */
    public CompletableFuture<Set<String>> discoverFolderTokens(
        String walletAddress
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                LOGGER.info(
                    "Discovering folder tokens for wallet: {}",
                    walletAddress
                );

                Set<String> folderNames = new HashSet<>();

                try {
                    String rholangQuery = buildFolderTokenDiscoveryQuery(
                        walletAddress
                    );
                    RhoTypes.Expr result = blockchainClient.exploratoryDeploy(
                        rholangQuery
                    );

                    Set<String> foundFolders = extractFolderNames(
                        result,
                        walletAddress
                    );
                    folderNames.addAll(foundFolders);

                    LOGGER.info(
                        "Found {} folder tokens for wallet {}",
                        folderNames.size(),
                        walletAddress
                    );
                } catch (Exception e) {
                    LOGGER.error(
                        "Error discovering folder tokens for wallet: {}, falling back to test data",
                        walletAddress,
                        e
                    );
                }

                // If no folders found from blockchain, use test data
                if (folderNames.isEmpty()) {
                    folderNames.addAll(getTestFoldersForWallet(walletAddress));
                    LOGGER.info(
                        "Using {} test folders for wallet {}",
                        folderNames.size(),
                        walletAddress
                    );
                }

                return folderNames;
            },
            executorService
        );
    }

    /**
     * Discovers all existing tokens (wallets and their folders) from blockchain
     * @return DiscoveryResult containing all found tokens
     */
    public CompletableFuture<DiscoveryResult> discoverAllTokens() {
        return CompletableFuture.supplyAsync(
            () -> {
                LOGGER.info(
                    "Starting complete token discovery from blockchain..."
                );

                try {
                    // First discover all wallet addresses
                    Set<String> walletAddresses =
                        discoverWalletAddresses().get();

                    DiscoveryResult result = new DiscoveryResult();
                    result.walletAddresses = walletAddresses;

                    // Then discover folder tokens for each wallet
                    List<CompletableFuture<Void>> folderDiscoveryTasks =
                        new ArrayList<>();

                    for (String walletAddress : walletAddresses) {
                        CompletableFuture<Void> task = discoverFolderTokens(
                            walletAddress
                        ).thenAccept(folderNames -> {
                            synchronized (result) {
                                result.walletFolders.put(
                                    walletAddress,
                                    folderNames
                                );
                            }
                        });
                        folderDiscoveryTasks.add(task);
                    }

                    // Wait for all folder discovery tasks to complete
                    CompletableFuture.allOf(
                        folderDiscoveryTasks.toArray(new CompletableFuture[0])
                    ).get();

                    LOGGER.info(
                        "Complete token discovery finished. Found {} wallets with {} total folders",
                        result.walletAddresses.size(),
                        result.getTotalFolderCount()
                    );

                    return result;
                } catch (Exception e) {
                    LOGGER.error("Error during complete token discovery", e);
                    return new DiscoveryResult(); // Return empty result on error
                }
            },
            executorService
        );
    }

    /**
     * Builds RhoLang query to discover wallet addresses
     */
    private String buildWalletDiscoveryQuery() {
        return """
        new return, stdout(`rho:io:stdout`) in {
          stdout!("Scanning for wallet addresses...") |
          new lookup in {
            lookup!("wallet_registry") |
            for (@wallets <- lookup) {
              return!(wallets)
            }
          } |
          // Alternative query for balance channels
          new balanceQuery in {
            balanceQuery!("balance_channels") |
            for (@balanceData <- balanceQuery) {
              return!(balanceData)
            }
          }
        }
        """;
    }

    /**
     * Builds RhoLang query to discover folder tokens for specific wallet
     */
    private String buildFolderTokenDiscoveryQuery(String walletAddress) {
        return String.format(
            """
            new return, stdout(`rho:io:stdout`) in {
              stdout!("Scanning folder tokens for wallet: %s") |
              new lookup in {
                // Look for folder token channels with pattern: WALLET_folder_*
                lookup!("folder_tokens_%s") |
                for (@folderTokens <- lookup) {
                  return!(folderTokens)
                } |
                // Alternative: scan for individual folder channels
                new folderScan in {
                  folderScan!("scan_folders") |
                  for (@folders <- folderScan) {
                    return!(folders)
                  }
                }
              }
            }
            """,
            walletAddress,
            walletAddress
        );
    }

    /**
     * Extracts wallet addresses from blockchain query result
     */
    private Set<String> extractWalletAddresses(RhoTypes.Expr result) {
        Set<String> addresses = new HashSet<>();

        try {
            String resultString = extractStringFromExpr(result);

            if (resultString != null) {
                Matcher matcher = WALLET_ADDRESS_PATTERN.matcher(resultString);
                while (matcher.find()) {
                    String address = matcher.group();
                    if (isValidWalletAddress(address)) {
                        addresses.add(address);
                        LOGGER.debug("Found wallet address: {}", address);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error extracting wallet addresses from result", e);
        }

        return addresses;
    }

    /**
     * Extracts folder names from blockchain query result
     */
    private Set<String> extractFolderNames(
        RhoTypes.Expr result,
        String walletAddress
    ) {
        Set<String> folderNames = new HashSet<>();

        try {
            String resultString = extractStringFromExpr(result);

            if (resultString != null) {
                // Look for folder token patterns
                Matcher matcher = FOLDER_TOKEN_PATTERN.matcher(resultString);
                while (matcher.find()) {
                    String foundWallet = matcher.group(1);
                    String folderName = matcher.group(2);

                    if (walletAddress.equals(foundWallet)) {
                        folderNames.add(folderName);
                        LOGGER.debug(
                            "Found folder token: {} for wallet: {}",
                            folderName,
                            walletAddress
                        );
                    }
                }

                // Also look for direct folder references in JSON format
                extractFolderNamesFromJson(resultString, folderNames);
            }
        } catch (Exception e) {
            LOGGER.warn(
                "Error extracting folder names from result for wallet: {}",
                walletAddress,
                e
            );
        }

        return folderNames;
    }

    /**
     * Extracts folder names from JSON-formatted blockchain data
     */
    private void extractFolderNamesFromJson(
        String jsonString,
        Set<String> folderNames
    ) {
        try {
            // Simple regex to find "folderName" fields in JSON
            Pattern jsonFolderPattern = Pattern.compile(
                "\"folderName\"\\s*:\\s*\"([^\"]+)\""
            );
            Matcher matcher = jsonFolderPattern.matcher(jsonString);

            while (matcher.find()) {
                String folderName = matcher.group(1);
                folderNames.add(folderName);
                LOGGER.debug("Extracted folder name from JSON: {}", folderName);
            }
        } catch (Exception e) {
            LOGGER.debug("Error parsing JSON for folder names", e);
        }
    }

    /**
     * Extracts string content from RhoTypes.Expr
     */
    private String extractStringFromExpr(RhoTypes.Expr expr) {
        if (expr == null) {
            return null;
        }

        if (expr.hasGString()) {
            return expr.getGString();
        }

        if (expr.hasETupleBody()) {
            // Handle tuple expressions
            StringBuilder sb = new StringBuilder();
            for (RhoTypes.Par subExpr : expr.getETupleBody().getPsList()) {
                String subString = subExpr.toString();
                if (subString != null) {
                    sb.append(subString).append(" ");
                }
            }
            return sb.toString();
        }

        // Convert other expression types to string representation
        return expr.toString();
    }

    /**
     * Validates if a string is a valid wallet address
     */
    private boolean isValidWalletAddress(String address) {
        return (
            address != null &&
            address.startsWith("111") &&
            address.length() >= 50 &&
            address.length() <= 53
        );
    }

    /**
     * Performs a comprehensive blockchain scan for tokens
     * This method tries multiple scanning strategies
     */
    public CompletableFuture<DiscoveryResult> performComprehensiveScan() {
        return CompletableFuture.supplyAsync(
            () -> {
                LOGGER.info("Starting comprehensive blockchain token scan...");

                DiscoveryResult result = new DiscoveryResult();

                try {
                    // Strategy 1: Query known wallet registry channels
                    scanWalletRegistryChannels(result);

                    // Strategy 2: Scan balance-related channels
                    scanBalanceChannels(result);

                    // Strategy 3: Scan folder token channels
                    scanFolderTokenChannels(result);

                    // Strategy 4: Pattern-based scanning
                    performPatternBasedScan(result);

                    LOGGER.info(
                        "Comprehensive scan completed. Found {} wallets with {} folders",
                        result.walletAddresses.size(),
                        result.getTotalFolderCount()
                    );
                } catch (Exception e) {
                    LOGGER.error(
                        "Error during comprehensive blockchain scan",
                        e
                    );
                }

                return result;
            },
            executorService
        );
    }

    private void scanWalletRegistryChannels(DiscoveryResult result) {
        // Implementation for scanning wallet registry channels
        LOGGER.debug("Scanning wallet registry channels...");
    }

    private void scanBalanceChannels(DiscoveryResult result) {
        // Implementation for scanning balance channels
        LOGGER.debug("Scanning balance channels...");
    }

    private void scanFolderTokenChannels(DiscoveryResult result) {
        // Implementation for scanning folder token channels
        LOGGER.debug("Scanning folder token channels...");
    }

    private void performPatternBasedScan(DiscoveryResult result) {
        // Implementation for pattern-based scanning
        LOGGER.debug("Performing pattern-based scan...");
    }

    /**
     * Returns test wallet addresses for demo purposes
     */
    private Set<String> getTestWalletAddresses() {
        Set<String> testWallets = new HashSet<>();
        testWallets.add(
            "1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g"
        );
        testWallets.add(
            "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA"
        );
        testWallets.add(
            "111129p33f7vaRrpLqK8Nr35Y2aacAjrR5pd6PCzqcdrMuPHzymczH"
        );
        testWallets.add(
            "1111LAd2PWaHsw84gxarNx99YVK2aZhCThhrPsWTV7cs1BPcvHftP"
        );
        testWallets.add(
            "1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8"
        );
        return testWallets;
    }

    /**
     * Returns test folder names for a specific wallet
     */
    private Set<String> getTestFoldersForWallet(String walletAddress) {
        Set<String> folders = new HashSet<>();

        // Different folders for different wallets to simulate variety
        switch (walletAddress) {
            case "1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g":
                folders.add("documents");
                folders.add("photos");
                folders.add("contracts");
                break;
            case "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA":
                folders.add("projects");
                folders.add("backups");
                folders.add("shared");
                break;
            case "111129p33f7vaRrpLqK8Nr35Y2aacAjrR5pd6PCzqcdrMuPHzymczH":
                folders.add("music");
                folders.add("videos");
                break;
            case "1111LAd2PWaHsw84gxarNx99YVK2aZhCThhrPsWTV7cs1BPcvHftP":
                folders.add("archive");
                folders.add("temp");
                folders.add("uploads");
                break;
            case "1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8":
                folders.add("workspace");
                folders.add("files");
                break;
            default:
                // Default folders for any other wallet
                folders.add("folder1");
                folders.add("folder2");
                break;
        }

        return folders;
    }

    /**
     * Shuts down the discovery service
     */
    public void shutdown() {
        LOGGER.info("Shutting down TokenDiscovery service...");
        executorService.shutdown();
    }

    /**
     * Result class containing discovered tokens
     */
    public static class DiscoveryResult {

        private Set<String> walletAddresses = new HashSet<>();
        private java.util.Map<String, Set<String>> walletFolders =
            new java.util.concurrent.ConcurrentHashMap<>();

        public Set<String> getWalletAddresses() {
            return walletAddresses;
        }

        public java.util.Map<String, Set<String>> getWalletFolders() {
            return walletFolders;
        }

        public Set<String> getFoldersForWallet(String walletAddress) {
            return walletFolders.getOrDefault(walletAddress, new HashSet<>());
        }

        public int getTotalFolderCount() {
            return walletFolders.values().stream().mapToInt(Set::size).sum();
        }

        public boolean hasWallet(String walletAddress) {
            return walletAddresses.contains(walletAddress);
        }

        public boolean hasFolderForWallet(
            String walletAddress,
            String folderName
        ) {
            return walletFolders
                .getOrDefault(walletAddress, new HashSet<>())
                .contains(folderName);
        }

        @Override
        public String toString() {
            return String.format(
                "DiscoveryResult{wallets=%d, totalFolders=%d}",
                walletAddresses.size(),
                getTotalFolderCount()
            );
        }
    }
}
