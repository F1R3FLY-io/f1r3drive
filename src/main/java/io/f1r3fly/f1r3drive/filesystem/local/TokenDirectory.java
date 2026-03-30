package io.f1r3fly.f1r3drive.filesystem.local;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSContext;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSFileStat;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSFillDir;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

public class TokenDirectory extends AbstractLocalPath implements Directory {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        TokenDirectory.class
    );

    public static String NAME = ".tokens";

    private final Set<Path> children = new HashSet<>();
    private final UnlockedWalletDirectory parent;

    private volatile boolean balanceChanged = true;

    private static final List<Long> denominations = Arrays.asList(
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

    // Operation costs in whole REV tokens
    private static final Map<String, Long> OPERATION_COSTS;

    static {
        Map<String, Long> costs = new HashMap<>();
        costs.put("CREATE_FILE", 1L);
        costs.put("READ_FILE", 1L);
        costs.put("WRITE_FILE", 3L);
        costs.put("DELETE_FILE", 2L);
        costs.put("CREATE_DIRECTORY", 5L);
        costs.put("READ_DIRECTORY", 1L);
        costs.put("DEPLOY_CONTRACT", 100L);
        costs.put("QUERY_BALANCE", 1L);
        costs.put("CHANGE_TOKEN", 2L);
        costs.put("CREATE_TOKEN_FILE", 1L);
        costs.put("UNLOCK_WALLET", 5L);
        OPERATION_COSTS = Collections.unmodifiableMap(costs);
    }

    // Per-wallet operation costs tracking
    private static final Map<String, Long> walletOperationCosts =
        new ConcurrentHashMap<>();

    public TokenDirectory(
        BlockchainContext blockchainContext,
        UnlockedWalletDirectory parent
    ) {
        super(blockchainContext, NAME, parent);
        this.parent = parent;
        refreshLastUpdated();
        // Force initial token creation
        this.balanceChanged = true;
        recreateTokenFiles();
    }

    @Override
    public synchronized void addChild(Path p) {
        children.add(p);
    }

    @Override
    public synchronized void deleteChild(Path child) {
        children.remove(child);
    }

    @Override
    public void mkdir(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void mkfile(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    public void recreateTokenFiles() {
        LOGGER.info(
            "Starting token recreation for wallet: {}",
            getBlockchainContext().getWalletInfo().revAddress()
        );

        int oldTokenCount = (int) children
            .stream()
            .filter(c -> c instanceof TokenFile)
            .count();
        LOGGER.debug("Removing {} existing token files", oldTokenCount);

        children.removeIf(c -> c instanceof TokenFile);
        refreshLastUpdated(); // Update timestamp when recreating children

        long rawBalance;
        try {
            LOGGER.debug("Checking balance for wallet...");
            String walletAddress = getBlockchainContext()
                .getWalletInfo()
                .revAddress();
            // Record cost for balance query
            recordOperationCost(walletAddress, "QUERY_BALANCE");
            rawBalance = checkBalance();
            LOGGER.info(
                "Retrieved raw balance: {} for wallet: {}",
                rawBalance,
                getBlockchainContext().getWalletInfo().revAddress()
            );
        } catch (F1r3DriveError e) {
            LOGGER.error(
                "Failed to check balance for wallet: {}",
                getBlockchainContext().getWalletInfo().revAddress(),
                e
            );
            return;
        }

        // Calculate adjusted balance by subtracting operation costs
        String walletAddress = getBlockchainContext()
            .getWalletInfo()
            .revAddress();
        long operationCosts = getTotalOperationCosts(walletAddress);
        long balance = Math.max(0, rawBalance - operationCosts);

        LOGGER.info(
            "Adjusted balance calculation for wallet {}: raw={}, costs={}, available={}",
            walletAddress,
            rawBalance,
            operationCosts,
            balance
        );

        if (balance == 0) {
            LOGGER.warn(
                "Wallet has zero available balance (raw: {}, costs: {}), no tokens will be created",
                rawBalance,
                operationCosts
            );
            return;
        }

        Map<Long, Integer> tokenMap = splitBalance(balance);
        LOGGER.info(
            "Split available balance {} into {} denominations: {}",
            balance,
            tokenMap.size(),
            tokenMap
        );

        for (Map.Entry<Long, Integer> entry : tokenMap.entrySet()) {
            long denomination = entry.getKey();
            int amount = entry.getValue();
            LOGGER.info(
                "Creating {} tokens of denomination {}",
                amount,
                denomination
            );
            addTokens(denomination, amount);
        }

        int finalTokenCount = (int) children
            .stream()
            .filter(c -> c instanceof TokenFile)
            .count();
        LOGGER.info(
            "Token creation completed: {} tokens created for wallet {}",
            finalTokenCount,
            getBlockchainContext().getWalletInfo().revAddress()
        );

        // Log all created token names for debugging
        children
            .stream()
            .filter(c -> c instanceof TokenFile)
            .forEach(tokenFile ->
                LOGGER.debug("Created token: {}", tokenFile.getName())
            );
    }

    private Map<Long, Integer> splitBalance(long balance) {
        Map<Long, Integer> tokenMap = new HashMap<>();

        for (long denom : denominations) {
            int count = (int) (balance / denom);
            if (count > 0) {
                tokenMap.put(denom, count);
                balance %= denom;
            }
        }
        return tokenMap;
    }

    private long checkBalance() throws F1r3DriveError {
        String checkBalanceRho = RholangExpressionConstructor.checkBalanceRho(
            getBlockchainContext().getWalletInfo().revAddress()
        );
        RhoTypes.Expr expr = getBlockchainContext()
            .getBlockchainClient()
            .exploratoryDeploy(checkBalanceRho);

        if (!expr.hasGInt()) {
            throw new F1r3DriveError("Invalid balance data");
        }

        return expr.getGInt();
    }

    public void addTokens(long denomination, long N) {
        LOGGER.debug("Adding {} tokens of denomination {}", N, denomination);
        for (int i = 0; i < N; i++) {
            String tokenName = denomination + "-REV." + i + ".token";
            TokenFile tokenFile = new TokenFile(
                getBlockchainContext(),
                tokenName,
                this,
                denomination
            );
            children.add(tokenFile);
            LOGGER.debug("Added token: {}", tokenName);
        }
        if (N > 0) {
            refreshLastUpdated(); // Update timestamp when adding tokens
            LOGGER.info(
                "Added {} tokens of denomination {} (total children: {})",
                N,
                denomination,
                children.size()
            );
        } else {
            LOGGER.debug(
                "No tokens added for denomination {} (N={})",
                denomination,
                N
            );
        }
    }

    @Override
    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public Set<Path> getChildren() {
        return children;
    }

    @Override
    public void getAttr(FSFileStat stat, FSContext context) {
        stat.setMode(FSFileStat.S_IFDIR | 0777); // Read-only permissions
        stat.setUid(context.getUid());
        stat.setGid(context.getGid());
        stat.setModificationTime(getLastUpdated());
    }

    /**
     * Change the token file into smaller token files and add them to the children
     *
     * @param tokenFile the token file to change
     *                  <p>
     *                  Example: 1000 -> 100, 100, 100, 100, 100, 100, 100, 100, 100, 100
     *                  or 14 -> 10, 4
     *                  or 10 -> 10
     *                  or 9 -> 9
     *                  or 99 -> 10, 10, 10, 10, 10, 10, 10, 10, 10, 9
     */
    public void change(TokenFile tokenFile) {
        long amount = tokenFile.value;
        this.deleteChild(tokenFile);
        refreshLastUpdated(); // Update timestamp when modifying children

        // Extract original denomination from token name
        String originalName = tokenFile.getName();
        String originalDenomination = originalName.split(".token")[0];

        // Find the largest denomination that divides the amount
        long denomination = 1;
        while (amount > denomination) {
            denomination *= 10;
        }
        denomination /= 10;

        // Break down the amount into smaller denominations
        int i = 0;
        while (amount > 0) {
            if (amount >= denomination) {
                // Create a token of the current denomination
                String tokenName =
                    denomination +
                    "-REV.changed-" +
                    originalDenomination +
                    "." +
                    i++ +
                    ".token";
                LOGGER.info(
                    "Creating token " +
                        tokenName +
                        " with value " +
                        denomination
                );
                TokenFile newTokenFile = new TokenFile(
                    getBlockchainContext(),
                    tokenName,
                    this,
                    denomination
                );
                this.addChild(newTokenFile);
                amount -= denomination;
            } else {
                // Move to next smaller denomination
                denomination /= 10;
            }
        }
    }

    public void handleWalletBalanceChanged() {
        this.balanceChanged = true;
    }

    @Override
    public void read(FSFillDir filler) {
        LOGGER.info(
            "Opening .tokens directory - checking current balance and regenerating tokens..."
        );

        // Always check balance and recreate tokens when folder is accessed
        try {
            recreateTokenFiles();
            // Also sync with physical filesystem
            syncTokensToPhysicalFileSystem();
            LOGGER.info(
                "Token files updated based on current blockchain balance"
            );
        } catch (Exception e) {
            LOGGER.error("Failed to update token files: {}", e.getMessage(), e);
        }

        // Log current children before filling directory
        LOGGER.debug("TokenDirectory children after balance check:");
        children.forEach(child ->
            LOGGER.debug(
                "  - {} (type: {})",
                child.getName(),
                child.getClass().getSimpleName()
            )
        );

        Directory.super.read(filler);

        LOGGER.info(
            "TokenDirectory read completed with {} token files",
            children.size()
        );
    }

    /**
     * Synchronizes virtual token files with physical filesystem
     */
    private void syncTokensToPhysicalFileSystem() {
        try {
            String walletAddress = getBlockchainContext()
                .getWalletInfo()
                .revAddress();
            String baseDirectory =
                System.getProperty("user.home") + "/demo-f1r3drive";

            // Find physical wallet directory
            File walletDir = new File(baseDirectory, walletAddress);
            if (!walletDir.exists()) {
                LOGGER.debug(
                    "Physical wallet directory doesn't exist: {}",
                    walletDir
                );
                return;
            }

            // Create .tokens directory in physical filesystem
            File tokensDir = new File(walletDir, ".tokens");
            if (!tokensDir.exists()) {
                tokensDir.mkdirs();
                LOGGER.debug(
                    "Created physical .tokens directory: {}",
                    tokensDir
                );
            }

            // Remove old token files
            File[] existingFiles = tokensDir.listFiles((dir, name) ->
                name.endsWith(".token")
            );
            if (existingFiles != null) {
                for (File file : existingFiles) {
                    file.delete();
                }
                LOGGER.debug(
                    "Removed {} old token files",
                    existingFiles.length
                );
            }

            // Create new token files based on virtual filesystem
            int createdFiles = 0;
            for (Path child : children) {
                if (child instanceof TokenFile) {
                    TokenFile tokenFile = (TokenFile) child;
                    File physicalFile = new File(
                        tokensDir,
                        tokenFile.getName()
                    );
                    try {
                        physicalFile.createNewFile();
                        createdFiles++;
                    } catch (IOException e) {
                        LOGGER.warn(
                            "Failed to create token file: {}",
                            physicalFile,
                            e
                        );
                    }
                }
            }

            LOGGER.info(
                "Synchronized {} token files to physical filesystem",
                createdFiles
            );
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to sync tokens to physical filesystem: {}",
                e.getMessage()
            );
        }
    }

    @Override
    public @Nullable UnlockedWalletDirectory getParent() {
        return parent;
    }

    /**
     * Records operation cost for a wallet
     */
    public static void recordOperationCost(
        String walletAddress,
        String operation
    ) {
        long cost = OPERATION_COSTS.getOrDefault(operation, 0L);
        walletOperationCosts.put(
            walletAddress,
            walletOperationCosts.getOrDefault(walletAddress, 0L) + cost
        );
        LOGGER.debug(
            "Recorded operation {} for wallet {} with cost {} REV (total: {} REV)",
            operation,
            walletAddress,
            cost,
            walletOperationCosts.get(walletAddress)
        );
    }

    /**
     * Gets total operation costs for a wallet in whole REV tokens
     */
    public static long getTotalOperationCosts(String walletAddress) {
        return walletOperationCosts.getOrDefault(walletAddress, 0L);
    }

    /**
     * Resets operation costs for a wallet
     */
    public static void resetWalletCosts(String walletAddress) {
        walletOperationCosts.remove(walletAddress);
        LOGGER.info("Reset operation costs for wallet: {}", walletAddress);
    }

    /**
     * Gets operation costs report
     */
    public static String getOperationCostsReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== F1r3Drive Operation Costs ===\n");
        report.append("(All costs in whole REV tokens)\n\n");

        report.append("FILE OPERATIONS:\n");
        report.append(
            String.format(
                "  Create File:     %d REV\n",
                OPERATION_COSTS.get("CREATE_FILE")
            )
        );
        report.append(
            String.format(
                "  Read File:       %d REV\n",
                OPERATION_COSTS.get("READ_FILE")
            )
        );
        report.append(
            String.format(
                "  Write File:      %d REV\n",
                OPERATION_COSTS.get("WRITE_FILE")
            )
        );
        report.append(
            String.format(
                "  Delete File:     %d REV\n",
                OPERATION_COSTS.get("DELETE_FILE")
            )
        );

        report.append("\nDIRECTORY OPERATIONS:\n");
        report.append(
            String.format(
                "  Create Directory: %d REV\n",
                OPERATION_COSTS.get("CREATE_DIRECTORY")
            )
        );
        report.append(
            String.format(
                "  Read Directory:   %d REV\n",
                OPERATION_COSTS.get("READ_DIRECTORY")
            )
        );

        report.append("\nBLOCKCHAIN OPERATIONS:\n");
        report.append(
            String.format(
                "  Deploy Contract: %d REV\n",
                OPERATION_COSTS.get("DEPLOY_CONTRACT")
            )
        );
        report.append(
            String.format(
                "  Query Balance:    %d REV\n",
                OPERATION_COSTS.get("QUERY_BALANCE")
            )
        );

        report.append("\nTOKEN OPERATIONS:\n");
        report.append(
            String.format(
                "  Change Token:     %d REV\n",
                OPERATION_COSTS.get("CHANGE_TOKEN")
            )
        );
        report.append(
            String.format(
                "  Create Token:     %d REV\n",
                OPERATION_COSTS.get("CREATE_TOKEN_FILE")
            )
        );

        report.append("\nWALLET OPERATIONS:\n");
        report.append(
            String.format(
                "  Unlock Wallet:    %d REV\n",
                OPERATION_COSTS.get("UNLOCK_WALLET")
            )
        );

        return report.toString();
    }
}
