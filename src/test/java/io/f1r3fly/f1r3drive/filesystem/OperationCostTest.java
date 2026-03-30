package io.f1r3fly.f1r3drive.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class demonstrating operation cost functionality in F1r3Drive
 */
public class OperationCostTest {

    private static final String TEST_WALLET_1 =
        "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA";
    private static final String TEST_WALLET_2 =
        "1111BTC8kZxAJfZiJMX84TKb7nSDPR4pVocdSGp4Q7DxZXD2YKoJJ";

    @BeforeEach
    void setUp() {
        // Reset operation costs before each test
        TokenDirectory.resetWalletCosts(TEST_WALLET_1);
        TokenDirectory.resetWalletCosts(TEST_WALLET_2);
    }

    @Test
    @DisplayName("Should track individual operation costs")
    void shouldTrackIndividualOperationCosts() {
        // Record some operations for wallet 1
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_FILE");
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "WRITE_FILE");
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "READ_FILE");

        // Should cost: 1 + 3 + 1 = 5 REV
        long totalCost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        assertEquals(5L, totalCost, "Total cost should be 5 REV");
    }

    @Test
    @DisplayName("Should track costs separately for different wallets")
    void shouldTrackCostsSeparatelyForDifferentWallets() {
        // Record operations for wallet 1
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_FILE"); // 1 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "DEPLOY_CONTRACT"); // 100 REV

        // Record operations for wallet 2
        TokenDirectory.recordOperationCost(TEST_WALLET_2, "READ_FILE"); // 1 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_2, "WRITE_FILE"); // 3 REV

        long wallet1Cost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        long wallet2Cost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_2);

        assertEquals(
            101L,
            wallet1Cost,
            "Wallet 1 should have 101 REV in costs"
        );
        assertEquals(4L, wallet2Cost, "Wallet 2 should have 4 REV in costs");
    }

    @Test
    @DisplayName("Should handle expensive operations correctly")
    void shouldHandleExpensiveOperationsCorrectly() {
        // Deploy contract is the most expensive operation (100 REV)
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "DEPLOY_CONTRACT");
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "DEPLOY_CONTRACT");

        long totalCost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        assertEquals(200L, totalCost, "Two deployments should cost 200 REV");
    }

    @Test
    @DisplayName("Should return zero for wallets with no operations")
    void shouldReturnZeroForWalletsWithNoOperations() {
        long cost = TokenDirectory.getTotalOperationCosts(
            "unused-wallet-address"
        );
        assertEquals(0L, cost, "Unused wallet should have zero costs");
    }

    @Test
    @DisplayName("Should generate operation costs report")
    void shouldGenerateOperationCostsReport() {
        String report = TokenDirectory.getOperationCostsReport();

        System.out.println("=== DEBUG: Actual Report Content ===");
        System.out.println(report);
        System.out.println("=== END DEBUG ===");

        assertNotNull(report, "Report should not be null");
        assertTrue(
            report.contains("F1r3Drive Operation Costs"),
            "Report should contain title"
        );

        // Print what we're actually looking for
        System.out.println("Looking for 'Create File' in report...");
        System.out.println(
            "Report contains 'Create File': " + report.contains("Create File")
        );
        System.out.println(
            "Report contains 'CREATE_FILE': " + report.contains("CREATE_FILE")
        );

        assertTrue(
            report.contains("Create File") || report.contains("CREATE_FILE"),
            "Report should contain file operations"
        );
        assertTrue(
            report.contains("Deploy Contract") ||
                report.contains("DEPLOY_CONTRACT"),
            "Report should contain blockchain operations"
        );
        assertTrue(report.contains("1 REV"), "Report should show costs in REV");
        assertTrue(
            report.contains("100 REV"),
            "Report should show deployment cost"
        );
    }

    @Test
    @DisplayName("Should accumulate costs correctly over multiple operations")
    void shouldAccumulateCostsCorrectlyOverMultipleOperations() {
        // Simulate a typical user session
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_DIRECTORY"); // 5 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_FILE"); // 1 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "WRITE_FILE"); // 3 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "READ_FILE"); // 1 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CHANGE_TOKEN"); // 2 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "QUERY_BALANCE"); // 1 REV

        long totalCost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        assertEquals(13L, totalCost, "Total session cost should be 13 REV");
    }

    @Test
    @DisplayName("Should handle token operations correctly")
    void shouldHandleTokenOperationsCorrectly() {
        // Record token-specific operations
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_TOKEN_FILE"); // 1 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CHANGE_TOKEN"); // 2 REV
        TokenDirectory.recordOperationCost(TEST_WALLET_1, "QUERY_BALANCE"); // 1 REV

        long totalCost = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        assertEquals(4L, totalCost, "Token operations should cost 4 REV");
    }

    @Test
    @DisplayName("Should demonstrate cost calculation for real scenario")
    void shouldDemonstrateCostCalculationForRealScenario() {
        System.out.println("\n=== Real Scenario Cost Calculation Demo ===");

        long initialBalance = 1000L; // 1000 REV initial balance
        System.out.println(
            "Initial wallet balance: " + initialBalance + " REV"
        );

        // Simulate user operations
        System.out.println("\nPerforming operations:");

        TokenDirectory.recordOperationCost(TEST_WALLET_1, "QUERY_BALANCE");
        System.out.println("- Query balance: 1 REV");

        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_DIRECTORY");
        System.out.println("- Create directory: 5 REV");

        TokenDirectory.recordOperationCost(TEST_WALLET_1, "CREATE_FILE");
        System.out.println("- Create file: 1 REV");

        TokenDirectory.recordOperationCost(TEST_WALLET_1, "WRITE_FILE");
        System.out.println("- Write to file: 3 REV");

        TokenDirectory.recordOperationCost(TEST_WALLET_1, "DEPLOY_CONTRACT");
        System.out.println("- Deploy contract: 100 REV");

        long totalCosts = TokenDirectory.getTotalOperationCosts(TEST_WALLET_1);
        long availableBalance = initialBalance - totalCosts;

        System.out.println("\nCost Summary:");
        System.out.println("Total operation costs: " + totalCosts + " REV");
        System.out.println("Available balance: " + availableBalance + " REV");

        assertEquals(110L, totalCosts, "Total costs should be 110 REV");
        assertEquals(
            890L,
            availableBalance,
            "Available balance should be 890 REV"
        );
    }
}
