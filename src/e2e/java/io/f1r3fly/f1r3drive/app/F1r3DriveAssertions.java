package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class F1r3DriveAssertions extends F1R3DriveTestFixture {

    public static void assertWrittenDataLocally(File file, byte[] inputDataAsBinary, String message) throws IOException {
        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, message);
    }

    public static void assertFileContentAtShard(byte[] expectedData, File file) {
        byte[] readData = F1r3DriveTestHelpers.getFileContentFromShardDirectly(file);
        assertArrayEquals(expectedData, readData, "Read data should be equal to written data");
    }

    public static void assertContainChildsAtShard(File dir, File... expectedChilds) {
        F1r3DriveTestHelpers.waitOnBackgroundDeployments();

        Set<String> children = F1r3DriveTestHelpers.getFolderChildrenFromShardDirectly(dir);

        // Filter out .tokens directory for wallet directories, same as local assertion
        if (F1r3DriveTestHelpers.isWalletDirectory(dir)) {
            children = children.stream()
                .filter(child -> !child.equals(".tokens"))
                .collect(Collectors.toSet());
        }

        assertEquals(expectedChilds.length, children.size(), "Should be only %d file(s) in %s. Found: %s".formatted(expectedChilds.length, dir.getAbsolutePath(), children));

        for (File expectedChild : expectedChilds) {
            String expectedChildName = expectedChild.getName();
            assertTrue(children.contains(expectedChildName), "File %s should contain %s".formatted(dir.getAbsolutePath(), expectedChildName));
        }
    }

    public static void assertContainTokenDirectoryLocally(File dir) {
        assertTrue(new File(dir, TokenDirectory.NAME).exists(), "%s directory should exist in %s. %s contains %s"
            .formatted(TokenDirectory.NAME, dir.getAbsolutePath(), dir.getName(), Arrays.toString(dir.listFiles())));
    }

    public static void assertContainChildsLocally(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));

        if (F1r3DriveTestHelpers.isWalletDirectory(dir)) {
            List<File> nonTokenChildren = Arrays.stream(childs)
                .filter(child -> !child.getName().equals(".tokens"))
                .collect(Collectors.toList());
            childs = nonTokenChildren.toArray(new File[0]);
        }

        assertEquals(expectedChilds.length, childs.length, "Should be only %d file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            boolean found = Arrays.stream(childs).anyMatch(child -> child.getName().equals(expectedChild.getName()));
            assertTrue(found, "File %s should contain %s".formatted(dir.getAbsolutePath(), expectedChild.getName()));
        }
    }

    public static void assertDirIsEmptyLocally(File dir) {
        File[] files = dir.listFiles();
        assertNotNull(files, "Dir %s listFiles returned null".formatted(dir));
        assertEquals(0, files.length, "Dir %s should be empty, but %s got".formatted(dir, Arrays.toString(files)));
    }

    /**
     * Unlocks a wallet directory and asserts that it doesn't throw an exception
     */
    public static void assertUnlockWalletDirectory(String wallet, String privateKey) {
        assertDoesNotThrow(() -> simulateUnlockWalletDirectoryAction(wallet, privateKey));
    }

    /**
     * Creates a new file and asserts the creation lifecycle: not exists -> create -> exists
     */
    public static void assertCreateNewFileLocally(File file) throws IOException {
        assertFalse(file.exists(), "File should not exist before creation");
        assertTrue(file.createNewFile(), "Failed to create test file");
        assertTrue(file.exists(), "File should exist after creation");
    }

    /**
     * Creates a new directory and asserts the creation lifecycle: not exists -> create -> exists
     */
    public static void assertCreateNewDirectoryLocally(File dir) {
        assertFalse(dir.exists(), "Directory should not exist before creation");
        assertTrue(dir.mkdir(), "Failed to create test directory");
        assertTrue(dir.exists(), "Directory should exist after creation");
    }

    /**
     * Renames a file and asserts the operation was successful
     */
    public static void assertRenameFileLocally(File source, File target) {
        assertTrue(source.renameTo(target), "Failed to rename file from " + source.getName() + " to " + target.getName());
    }

    /**
     * Deletes a file and asserts it was successful and no longer exists
     */
    public static void assertDeleteFileLocally(File file) {
        assertTrue(file.delete(), "Failed to delete file " + file.getName());
        assertFalse(file.exists(), "File should not exist after deletion");
    }

    /**
     * Deletes a directory and asserts it was successful and no longer exists
     */
    public static void assertDeleteDirectoryLocally(File dir) {
        assertTrue(dir.delete(), "Failed to delete directory " + dir.getName());
        assertFalse(dir.exists(), "Directory should not exist after deletion");
    }

    /**
     * Asserts that operations on non-existent files should fail
     */
    public static void assertNonExistentFileOperationsFailLocally(File file) {
        assertFalse(file.exists(), "Not existing file should not exist");
        assertThrows(IOException.class, () -> Files.readAllBytes(file.toPath()), "read should return error");
        assertFalse(file.renameTo(new File(file.getParent(), "abc")), "rename should return error");
        assertFalse(file.delete(), "unlink should return error");
    }

    /**
     * Asserts that operations on non-existent directories should fail
     */
    public static void assertNonExistentDirectoryOperationsFailLocally(File dir) {
        assertFalse(dir.exists(), "Not existing directory should not exist");
        assertNull(dir.listFiles(), "Not existing directory should not have any files");
        assertFalse(dir.renameTo(new File(dir.getParent(), "abc")), "rename should return error");
        assertFalse(dir.delete(), "unlink should return error");
    }

    /**
     * Validates token file properties: ends with .token, is file, read-only, operations fail
     */
    public static void assertTokenFilePropertiesLocally(File tokenFile) {
        assertTrue(tokenFile.exists(), "Token file should exist");
        assertTrue(tokenFile.getName().endsWith(".token"), "Token file should end with .token");
        assertTrue(tokenFile.isFile(), "Token file should be a file");
        assertFalse(tokenFile.renameTo(new File(tokenFile.getParent(), "10000000.token")), "rename should return error");
        assertFalse(tokenFile.delete(), "unlink should return error");
        assertFalse(tokenFile.mkdir(), "mkdir should return error");
    }

    /**
     * Creates nested directories and validates the structure
     */
    public static void assertCreateNestedDirectoriesLocally(File parentDir, String... dirNames) {
        File currentDir = parentDir;
        for (String dirName : dirNames) {
            File nextDir = new File(currentDir, dirName);
            assertTrue(nextDir.mkdirs(), "Failed to create nested directory " + dirName);
            currentDir = nextDir;
        }
    }


    /**
     * Asserts that the last modified time of a file/directory was updated after a specific operation
     *
     * @param file        The file or directory to check
     * @param initialTime The initial last modified time before the operation (in milliseconds)
     * @param operation   Description of the operation that should have updated the time
     */
    public static void assertLastModifiedTimeUpdatedLocally(File file, long initialTime, String operation) {
        assertTrue(file.exists(), "File/directory should exist before checking last modified time: " + file.getAbsolutePath());
        long currentTime = file.lastModified();
        long timeDifference = currentTime - initialTime;
        String fileType = file.isDirectory() ? "Directory" : "File";
        String fileSize = file.isFile() ? " (size: " + file.length() + " bytes)" : "";

        assertTrue(currentTime > initialTime,
            String.format("%s last modified time validation FAILED after operation: '%s'\n" +
                    "  → File: %s%s\n" +
                    "  → Expected: last modified time should be AFTER initial time\n" +
                    "  → Initial time: %d (%s)\n" +
                    "  → Current time: %d (%s)\n" +
                    "  → Time difference: %d ms\n" +
                    "  → Result: Current time is %s than initial time",
                fileType, operation, file.getAbsolutePath(), fileSize,
                initialTime, new java.util.Date(initialTime),
                currentTime, new java.util.Date(currentTime),
                timeDifference,
                currentTime > initialTime ? "NEWER" : "OLDER OR SAME"
            ));
    }

    /**
     * Asserts that the last modified time of a file/directory is after a specific timestamp
     *
     * @param file      The file or directory to check
     * @param afterTime The timestamp that the last modified time should be after (in milliseconds)
     * @param message   Custom error message
     */
    public static void assertLastModifiedTimeAfterLocally(File file, long afterTime, String message) {
        assertTrue(file.exists(), "File/directory should exist before timestamp validation: " + file.getAbsolutePath());
        long lastModified = file.lastModified();
        long timeDifference = lastModified - afterTime;
        String fileType = file.isDirectory() ? "Directory" : "File";
        String fileSize = file.isFile() ? " (size: " + file.length() + " bytes)" : "";

        assertTrue(lastModified > afterTime,
            String.format("%s timestamp validation FAILED: %s\n" +
                    "  → File: %s%s\n" +
                    "  → Expected: last modified time should be AFTER reference time\n" +
                    "  → Reference time: %d (%s)\n" +
                    "  → Actual last modified: %d (%s)\n" +
                    "  → Time difference: %d ms\n" +
                    "  → Validation status: %s",
                fileType, message, file.getAbsolutePath(), fileSize,
                afterTime, new java.util.Date(afterTime),
                lastModified, new java.util.Date(lastModified),
                timeDifference,
                lastModified > afterTime ? "PASSED" : "FAILED - last modified is NOT after reference time"
            ));
    }

    /**
     * Asserts that the last modified time of a file/directory is approximately equal to expected time
     * Allows for a small tolerance (1 second) due to filesystem precision
     *
     * @param file         The file or directory to check
     * @param expectedTime The expected last modified time (in milliseconds)
     * @param message      Custom error message
     */
    public static void assertLastModifiedTimeApproximatelyLocally(File file, long expectedTime, String message) {
        assertTrue(file.exists(), "File/directory should exist before approximate timestamp validation: " + file.getAbsolutePath());
        long lastModified = file.lastModified();

        // Use larger tolerance for remount operations, especially for directories
        // Remount operations involve complete filesystem unmount/mount cycles which can affect timestamps
        long tolerance = message.contains("remount") ? 10000 : 1000; // 10 seconds for remount, 1 second otherwise

        long diff = Math.abs(lastModified - expectedTime);
        String fileType = file.isDirectory() ? "Directory" : "File";
        String fileSize = file.isFile() ? " (size: " + file.length() + " bytes)" : "";

        assertTrue(diff <= tolerance,
            String.format("%s approximate timestamp validation FAILED: %s\n" +
                    "  → File: %s%s\n" +
                    "  → Expected: last modified time within ±%d ms tolerance\n" +
                    "  → Expected time: %d (%s)\n" +
                    "  → Actual last modified: %d (%s)\n" +
                    "  → Absolute difference: %d ms\n" +
                    "  → Tolerance limit: %d ms\n" +
                    "  → Validation status: %s (difference %s tolerance)",
                fileType, message, file.getAbsolutePath(), fileSize, tolerance,
                expectedTime, new java.util.Date(expectedTime),
                lastModified, new java.util.Date(lastModified),
                diff, tolerance,
                diff <= tolerance ? "PASSED" : "FAILED",
                diff <= tolerance ? "within" : "exceeds"
            ));
    }

    /**
     * Asserts that the last modified time of a file/directory is approximately equal to expected time
     * with a custom tolerance
     *
     * @param file         The file or directory to check
     * @param expectedTime The expected last modified time (in milliseconds)
     * @param toleranceMs  Custom tolerance in milliseconds
     * @param message      Custom error message
     */
    public static void assertLastModifiedTimeApproximatelyLocally(File file, long expectedTime, long toleranceMs, String message) {
        assertTrue(file.exists(), "File/directory should exist before approximate timestamp validation: " + file.getAbsolutePath());
        long lastModified = file.lastModified();
        long diff = Math.abs(lastModified - expectedTime);
        String fileType = file.isDirectory() ? "Directory" : "File";
        String fileSize = file.isFile() ? " (size: " + file.length() + " bytes)" : "";

        assertTrue(diff <= toleranceMs,
            String.format("%s approximate timestamp validation FAILED: %s\n" +
                    "  → File: %s%s\n" +
                    "  → Expected: last modified time within ±%d ms tolerance\n" +
                    "  → Expected time: %d (%s)\n" +
                    "  → Actual last modified: %d (%s)\n" +
                    "  → Absolute difference: %d ms\n" +
                    "  → Tolerance limit: %d ms\n" +
                    "  → Validation status: %s (difference %s tolerance)",
                fileType, message, file.getAbsolutePath(), fileSize, toleranceMs,
                expectedTime, new java.util.Date(expectedTime),
                lastModified, new java.util.Date(lastModified),
                diff, toleranceMs,
                diff <= toleranceMs ? "PASSED" : "FAILED",
                diff <= toleranceMs ? "within" : "exceeds"
            ));
    }

    /**
     * Asserts that two files/directories have different last modified times
     *
     * @param file1   First file or directory
     * @param file2   Second file or directory
     * @param message Custom error message
     */
    public static void assertDifferentLastModifiedTimesLocally(File file1, File file2, String message) {
        assertTrue(file1.exists(), "First file/directory should exist before timestamp comparison: " + file1.getAbsolutePath());
        assertTrue(file2.exists(), "Second file/directory should exist before timestamp comparison: " + file2.getAbsolutePath());
        long time1 = file1.lastModified();
        long time2 = file2.lastModified();
        long timeDifference = Math.abs(time1 - time2);
        String file1Type = file1.isDirectory() ? "Directory" : "File";
        String file2Type = file2.isDirectory() ? "Directory" : "File";
        String file1Size = file1.isFile() ? " (size: " + file1.length() + " bytes)" : "";
        String file2Size = file2.isFile() ? " (size: " + file2.length() + " bytes)" : "";

        assertNotEquals(time1, time2,
            String.format("Different timestamp validation FAILED: %s\n" +
                    "  → Expected: Two files should have DIFFERENT last modified times\n" +
                    "  → %s 1: %s%s\n" +
                    "    └─ Last modified: %d (%s)\n" +
                    "  → %s 2: %s%s\n" +
                    "    └─ Last modified: %d (%s)\n" +
                    "  → Time difference: %d ms\n" +
                    "  → Validation status: %s",
                message,
                file1Type, file1.getAbsolutePath(), file1Size,
                time1, new java.util.Date(time1),
                file2Type, file2.getAbsolutePath(), file2Size,
                time2, new java.util.Date(time2),
                timeDifference,
                time1 != time2 ? "PASSED - timestamps are different" : "FAILED - timestamps are IDENTICAL"
            ));
    }

    /**
     * Gets the current timestamp and waits for at least 1 second to ensure different timestamps
     * This is useful for testing last modified time changes since they are typically in seconds
     *
     * @return The timestamp before the wait (in milliseconds)
     */
    public static long waitForTimestampChange() {
        long beforeTime = System.currentTimeMillis();
        try {
            log.info("Waiting for timestamp change - Before: {} ({})", beforeTime, new java.util.Date(beforeTime));
            Thread.sleep(2000); // Wait 2 seconds to ensure significant timestamp difference
            long afterTime = System.currentTimeMillis();
            log.info("Timestamp change complete - After: {} ({}) - Delta: {} ms",
                afterTime, new java.util.Date(afterTime), (afterTime - beforeTime));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test execution was interrupted while waiting for timestamp change to ensure proper time difference validation. " +
                "This wait is necessary because filesystem timestamps typically have second-level precision. " +
                "Interruption occurred at: " + new java.util.Date(System.currentTimeMillis()));
        }
        return beforeTime;
    }
} 