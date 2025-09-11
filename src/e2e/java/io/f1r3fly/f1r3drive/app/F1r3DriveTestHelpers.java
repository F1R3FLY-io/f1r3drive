package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class F1r3DriveTestHelpers extends F1R3DriveTestFixture {

    // Retry constants for exploratoryDeploy operations
    private static final Duration INIT_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private static final int RETRIES = 10;

    public static @NotNull byte[] getFileContentFromShardDirectly(File file) {
        try {
            RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

            assertTrue(dirOrFile.isFile(), "Chanel data should be a file");
            assertNotNull(dirOrFile.firstChunk(), "Chanel data should contain firstChunk field");
            assertNotNull(dirOrFile.otherChunks(), "Chanel data should contain otherChunks field");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            outputStream.write(dirOrFile.firstChunk());

            Integer[] sortedKeys = dirOrFile.otherChunks().keySet().stream().sorted().toArray(Integer[]::new);
            for (Integer key : sortedKeys) {
                String subChannel = dirOrFile.otherChunks().get(key);
                String rholangCode = RholangExpressionConstructor.readFromChannel(subChannel);
                RhoTypes.Expr result = f1R3FlyBlockchainClient.exploratoryDeploy(rholangCode);
                byte[] chunk = RholangExpressionConstructor.parseExploratoryDeployBytes(result);
                outputStream.write(chunk);
            }

            return outputStream.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull Set<String> getFolderChildrenFromShardDirectly(File file) {
        RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

        assertTrue(dirOrFile.isDir(), "Chanel data should be a directory");

        return dirOrFile.children();
    }

    public static @NotNull RholangExpressionConstructor.ChannelData getChanelData(File file) {
        // reading data from shard directly:
        // 1. Get a data from the file. File is a chanel at specific block
        // Reducing the path. Fuse changes the path, so we need to change it too:
        // - the REAL path is /tmp/f1r3Drive/test.txt
        // - the FUSE's path is /test.txt
        File fusePath = new File(file.getAbsolutePath().replace(MOUNT_POINT_FILE.getAbsolutePath(), "")); // /tmp/f1r3Drive/test.txt
        // ->
        // /test.txt

        String fileNameAtShard = fusePath.getPath();

        // wait on background deployments
        waitOnBackgroundDeployments();

        RhoTypes.Expr result = null;
        String rholangCode = RholangExpressionConstructor.readFromChannel(fileNameAtShard);

        // Retry exploratoryDeploy with exponential backoff
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            try {
                result = f1R3FlyBlockchainClient.exploratoryDeploy(rholangCode);
                break; // Success, exit retry loop
            } catch (F1r3DriveError e) {
                if (attempt >= RETRIES - 1) {
                    // Last attempt failed, throw the exception
                    throw new RuntimeException(new NoDataByPath(fileNameAtShard, "", e));
                }

                // Wait before retry with exponential backoff
                try {
                    Thread.sleep(Math.min(INIT_DELAY.toMillis() * (1L << attempt), MAX_DELAY.toMillis()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(new NoDataByPath(fileNameAtShard, "", e));
                }
            }
        }

        assertNotNull(result, "Channel data %s should be not null".formatted(fileNameAtShard));

        // 2. Parse Channel value as a map
        return RholangExpressionConstructor.parseExploratoryDeployResult(result);
    }

    public static boolean isWalletDirectory(File dir) {
        return dir.getParentFile().equals(MOUNT_POINT_FILE);
    }

    public static void testIsNotEncrypted(File notEncrypted, String fileContent) throws IOException {
        String readData2 = Files.readString(notEncrypted.toPath());
        assertEquals(fileContent, readData2, "Read data should be equal to written data");

        String decodedFileData2 = new String(getFileContentFromShardDirectly(notEncrypted));
        assertEquals(fileContent, decodedFileData2, "Decoded data should be equal to the original data");
    }

    public static void testIsEncrypted(File encrypted, String expectedFileData) throws IOException, NoDataByPath {
        String readData = Files.readString(encrypted.toPath());
        assertEquals(expectedFileData, readData, "Read data should be equal to written data");

        String decodedFileData = new String(getFileContentFromShardDirectly(encrypted));
        // Actual data is encrypted. It should be different from the original data
        assertNotEquals(expectedFileData, decodedFileData, "Decoded data should be different from the original data");
    }

    public static void testToRenameTxtToDeployableExtension(String newExtension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test.text");

        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        assertTrue(file.renameTo(new File(file.getParent(), "test." + newExtension)), "Failed to rename file");

        waitOnBackgroundDeployments();

        String rholangCode = RholangExpressionConstructor.readFromChannel(newRhoChanel);
        RhoTypes.Expr result = f1R3FlyBlockchainClient.exploratoryDeploy(rholangCode);
        assertEquals(result.getGInt(), chanelValue,
            "Deployed data should be equal to written data");
    }

    public static void testToCreateDeployableFile(String extension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test." + extension);

        // For .metta or .rho extension
        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        waitOnBackgroundDeployments();
        String rholangCode = RholangExpressionConstructor.readFromChannel(newRhoChanel);
        RhoTypes.Expr result = f1R3FlyBlockchainClient.exploratoryDeploy(rholangCode);
        assertEquals(result.getGInt(), chanelValue,
            "Deployed data should be equal to written data");
    }

    public static long checkBalance(String revAddress) {
        String checkBalanceRho = RholangExpressionConstructor.checkBalanceRho(revAddress);
        RhoTypes.Expr expr = f1R3FlyBlockchainClient.exploratoryDeploy(checkBalanceRho);

        if (!expr.hasGInt()) {
            throw new F1r3DriveError("Invalid balance data");
        }

        return expr.getGInt();
    }
} 