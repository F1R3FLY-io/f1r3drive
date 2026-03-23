package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.errors.PathIsNotADirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import io.f1r3fly.f1r3drive.finderextensions.client.FinderSyncExtensionServiceClient.WalletUnlockException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static io.f1r3fly.f1r3drive.app.F1r3DriveAssertions.*;
import static io.f1r3fly.f1r3drive.app.F1r3DriveTestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class F1R3DriveTest extends F1R3DriveTestFixture {

    private static final long SHARD_POLL_TIMEOUT_MS = 120_000;

    // TESTS:

    @Test
    @Disabled
    @DisplayName("Should deploy Rho file after renaming from txt extension (manual propose)")
    void shouldDeployRhoFileAfterRename() throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        testToRenameTxtToDeployableExtension("rho");
    }

    @Disabled
    @Test
    @DisplayName("Should deploy Metta file after renaming from txt extension (manual propose)")
    void shouldDeployMettaFileAfterRename() throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        testToRenameTxtToDeployableExtension("metta");
    }

    @Test
    @Disabled
    @DisplayName("Should encrypt on save and decrypt on read for encrypted extension (manual propose)")
    void shouldEncryptOnSaveAndDecryptOnReadForEncryptedExtension()
        throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        Files.writeString(notEncrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsNotEncrypted(notEncrypted, fileContent);
    }

    @Test
    @Disabled
    @DisplayName("Should encrypt content when changing file extension to encrypted (manual propose)")
    void shouldEncryptOnChangingExtension() throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File encrypted = new File(MOUNT_POINT_FILE, "test.txt.encrypted");
        String fileContent = "Hello, world!";

        Files.writeString(encrypted.toPath(), fileContent, StandardCharsets.UTF_8);

        testIsEncrypted(encrypted, fileContent);

        File notEncrypted = new File(MOUNT_POINT_FILE, "test.txt");
        assertRenameFileLocally(encrypted, notEncrypted);

        testIsNotEncrypted(notEncrypted, fileContent);

        assertContainChildsLocally(MOUNT_POINT_FILE, notEncrypted);
        assertContainChildsAtShard(MOUNT_POINT_FILE, notEncrypted);

        assertRenameFileLocally(notEncrypted, encrypted);
        testIsEncrypted(encrypted, fileContent);
    }

    @Disabled
    @Test
    @DisplayName("Should store Metta file and deploy it (manual propose)")
    void shouldStoreMettaFileAndDeployIt() throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        testToCreateDeployableFile("metta"); // TODO: pass the correct Metta code from this line
    }

    @Test
    @Disabled
    @DisplayName("Should store Rho file and deploy it (manual propose)")
    void shouldStoreRhoFileAndDeployIt() throws IOException, NoDataByPath, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        testToCreateDeployableFile("rho");
    }

    @Disabled
    @Test
    @DisplayName("Should write and read large file (512MB) (manual propose)")
    void shouldWriteAndReadLargeFile() throws IOException, NoDataByPath, PathIsNotADirectory {
        File file = new File(MOUNT_POINT_FILE, "file.bin");

        assertCreateNewFileLocally(file);

        byte[] inputDataAsBinary = new byte[512 * 1024 * 1024]; // 512mb
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        assertWrittenDataLocally(file, inputDataAsBinary, "Read data should be equal to written data");
        assertFileContentAtShard(inputDataAsBinary, file);

        remount();

        assertWrittenDataLocally(file, inputDataAsBinary, "Read data should be equal to written data after remount");
    }

    @Test
    @DisplayName("Should perform CRUD operations on files: create, rename, read, and delete (manual propose)")
    void shouldCreateRenameGetDeleteFiles() throws IOException, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File file = new File(UNLOCKED_WALLET_DIR_1, "file.bin");

        assertCreateNewFileLocally(file);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, file);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, file);

        byte[] inputDataAsBinary = new byte[1024 * 1024]; // 1 MB
        new Random().nextBytes(inputDataAsBinary);
        Files.write(file.toPath(), inputDataAsBinary);
        assertWrittenDataLocally(file, inputDataAsBinary, "Read data should be equal to written data");
        assertFileContentAtShard(inputDataAsBinary, file);
        log.info("Written data length: {}", inputDataAsBinary.length);

        File renamedFile = new File(file.getParent(), "renamed.txt");
        assertRenameFileLocally(file, renamedFile);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedFile);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedFile);

        assertWrittenDataLocally(renamedFile, inputDataAsBinary, "Read data (from renamed file) should be equal to written data");
        assertFileContentAtShard(inputDataAsBinary, renamedFile);

        String inputDataAsString = "a".repeat(1024);
        Files.writeString(renamedFile.toPath(), inputDataAsString, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        byte[] dataBytes = inputDataAsString.getBytes();
        assertWrittenDataLocally(renamedFile, dataBytes, "Read data should be equal to written data");
        assertFileContentAtShard(dataBytes, renamedFile);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedFile); // it has to be the same folder after truncate and
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedFile); // override

        File dir = new File(UNLOCKED_WALLET_DIR_1, "testDir");
        assertCreateNewDirectoryLocally(dir);

        File nestedFile = new File(dir, "nestedFile.txt");
        assertCreateNewFileLocally(nestedFile);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsLocally(dir, nestedFile);
        assertContainChildsAtShard(dir, nestedFile);

        remount(); // umount and mount back
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsLocally(dir, nestedFile);
        assertContainChildsAtShard(dir, nestedFile);

        // check if deployed data is correct:

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedFile, dir);
        assertContainChildsLocally(dir, nestedFile);
        assertContainChildsAtShard(dir, nestedFile);

        String readDataAfterRemount = Files.readString(renamedFile.toPath());
        assertEquals(inputDataAsString, readDataAfterRemount, "Read data should be equal to written data");

        assertDeleteFileLocally(renamedFile);
        assertDeleteFileLocally(nestedFile);
        assertDeleteDirectoryLocally(dir);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1); // empty
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1); // empty

    }

    @Test
    @DisplayName("Should perform CRUD operations on directories: create, rename, list, and delete (manual propose)")
    void shouldCreateRenameListDeleteDirectoriesManualPropose() throws InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        assertContainTokenDirectoryLocally(UNLOCKED_WALLET_DIR_1);

        File dir1 = new File(UNLOCKED_WALLET_DIR_1, "testDir");

        assertCreateNewDirectoryLocally(dir1);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, dir1);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, dir1);

        File renamedDir = new File(UNLOCKED_WALLET_DIR_1, "renamedDir");

        assertRenameFileLocally(dir1, renamedDir);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1, renamedDir);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");

        boolean created = nestedDir2.mkdirs();
        assertTrue(created, "Should be able to create nested directories");
        assertTrue(nestedDir1.exists(), "nestedDir1 should exist after mkdirs");
        assertTrue(nestedDir2.exists(), "nestedDir2 should exist after mkdirs");

        assertContainChildsLocally(renamedDir, nestedDir1);
        assertContainChildsAtShard(renamedDir, nestedDir1);

        assertDeleteFileLocally(nestedDir2);

        assertContainChildsLocally(nestedDir1);
        assertContainChildsAtShard(nestedDir1);

        assertDeleteFileLocally(nestedDir1);

        assertContainChildsLocally(renamedDir);
        assertContainChildsAtShard(renamedDir);

        assertDeleteFileLocally(renamedDir);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1);
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1);
    }

    @Test
    @DisplayName("Should perform CRUD operations on directories: create, rename, list, and delete (auto propose via Heartbeat)")
    void shouldCreateRenameListDeleteDirectoriesAutoPropose() throws InterruptedException {

        mountF1r3Drive(false);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        assertContainTokenDirectoryLocally(UNLOCKED_WALLET_DIR_1);

        // === PHASE 1: CREATE OPERATIONS AND VERIFY AT SHARD ===

        File dir1 = new File(UNLOCKED_WALLET_DIR_1, "testDir");
        assertCreateNewDirectoryLocally(dir1);
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, UNLOCKED_WALLET_DIR_1, dir1);

        File renamedDir = new File(UNLOCKED_WALLET_DIR_1, "renamedDir");
        assertRenameFileLocally(dir1, renamedDir);
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, UNLOCKED_WALLET_DIR_1, renamedDir);

        File nestedDir1 = new File(renamedDir, "nestedDir1");
        File nestedDir2 = new File(nestedDir1, "nestedDir2");

        boolean created = nestedDir2.mkdirs();
        assertTrue(created, "Should be able to create nested directories");
        assertTrue(nestedDir1.exists(), "nestedDir1 should exist after mkdirs");
        assertTrue(nestedDir2.exists(), "nestedDir2 should exist after mkdirs");

        // === PHASE 2 & 3: WAIT FOR AUTO PROPOSER AND CHECK CREATED STATE AT SHARD ===
        log.info("Polling for auto proposer to process creation operations...");
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, UNLOCKED_WALLET_DIR_1, renamedDir);
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, renamedDir, nestedDir1);

        // === PHASE 4: DELETION OPERATIONS ===

        assertDeleteFileLocally(nestedDir2);
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, nestedDir1);

        assertDeleteFileLocally(nestedDir1);
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, renamedDir);

        assertDeleteFileLocally(renamedDir);

        // === PHASE 5 & 6: WAIT FOR AUTO PROPOSER AGAIN AND CHECK FINAL STATE AT SHARD ===
        log.info("Polling for auto proposer to process deletion operations...");
        pollAssertContainChildsAtShard(SHARD_POLL_TIMEOUT_MS, UNLOCKED_WALLET_DIR_1); // should be empty after all deletions
    }

    @Test
    @DisplayName("Should properly handle operations on non-existent files and directories (manual propose)")
    void shouldHandleOperationsWithNotExistingFileAndDirectory() throws InterruptedException {

        mountF1r3Drive(true);

        assertThrows(WalletUnlockException.class, () -> simulateUnlockWalletDirectoryAction(REV_WALLET_1, "abc")); // invalid
        // private
        // key
        assertThrows(WalletUnlockException.class,
            () -> simulateUnlockWalletDirectoryAction(REV_WALLET_1, PRIVATE_KEY_2)); // wrong private key

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1); // valid private key

        File dir = new File(UNLOCKED_WALLET_DIR_1, "testDir");

        assertNonExistentDirectoryOperationsFailLocally(dir);

        File file = new File(UNLOCKED_WALLET_DIR_1, "testFile.txt");

        assertNonExistentFileOperationsFailLocally(file);

        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1); // empty
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1); // empty

        // test with client2
        // folder is locked, so it should not be visible
        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1); // empty

    }

    @Test
    @DisplayName("Should support token file operations (manual propose)")
    void shouldSupportTokenFileOperations() throws InterruptedException {

        mountF1r3Drive(true);

        int BIGGER_DENOMINATION = 1000000;
        int SMALLER_DENOMINATION = BIGGER_DENOMINATION / 10;

        assertFalse(new File(LOCKED_WALLET_DIR_1, TokenDirectory.NAME).exists(),
            "Token directory should not exist before unlock");

        assertDirIsEmptyLocally(LOCKED_WALLET_DIR_1); // empty because of locked

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        File tokenDirectory = new File(UNLOCKED_WALLET_DIR_1, TokenDirectory.NAME);

        assertContainTokenDirectoryLocally(UNLOCKED_WALLET_DIR_1);
        assertContainChildsLocally(UNLOCKED_WALLET_DIR_1); // empty
        assertContainChildsAtShard(UNLOCKED_WALLET_DIR_1); // empty yet because of new token directory

        // .tokens directory should contain *.token files only
        File[] tokensFiles = tokenDirectory.listFiles();
        assertNotNull(tokensFiles, "Token directory should contain *.token files");
        for (File tokenFile : tokensFiles) {
            assertTokenFilePropertiesLocally(tokenFile);
        }

        // .tokens directory should be closed to changes
        assertThrows(IOException.class, () -> Files.writeString(
            new File(UNLOCKED_WALLET_DIR_1 + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME,
                "test.token")
                .toPath(),
            "test", StandardCharsets.UTF_8), "write should return error");

        // create token file manually
        File tokenFile = new File(UNLOCKED_WALLET_DIR_1 + PathUtils.getPathDelimiterBasedOnOS() + TokenDirectory.NAME,
            "x.token");
        assertFalse(tokenFile.exists(), "Token file should not exist");
        assertThrows(IOException.class, tokenFile::createNewFile, "Failed to create token file");

        File tokenFileToChange = Arrays.stream(tokensFiles)
            .filter(file -> file.getName().startsWith(BIGGER_DENOMINATION + "-REV"))
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Token file not found in list " + Arrays.toString(tokensFiles)));

        assertDoesNotThrow(() -> simulateChangeTokenAction(tokenFileToChange.getAbsolutePath()));

        // only one token file should be effected by change
        assertFalse(tokenFileToChange.exists(), "Token file should not exist");

        File[] otherTokenFiles = Arrays.stream(tokensFiles)
            .filter(file -> !file.getName().equals(tokenFileToChange.getName()))
            .toArray(File[]::new);

        // other token files should not be effected by change
        for (File otherTokenFile : otherTokenFiles) {
            assertTrue(otherTokenFile.exists(), "Token file should exist");
        }

        File[] allTokenFiles = tokenDirectory.listFiles();
        assertNotNull(allTokenFiles, "Token directory should contain *.token files");

        Set<File> newTokenFilesAfterChange = Set.of(allTokenFiles)
            .stream()
            .filter(file -> Arrays.stream(otherTokenFiles)
                .noneMatch(file2 -> file2.getName().equals(file.getName())))
            .collect(Collectors.toSet());

        // 10 because of denomination (e.g. 10 => 1,1,1,1,1,1,1,1,1,1)
        assertEquals(10, newTokenFilesAfterChange.size(), "Only 10 token files should be effected by change");

        for (File newTokenFileAfterChange : newTokenFilesAfterChange) {
            assertTokenFilePropertiesLocally(newTokenFileAfterChange);
            assertTrue(
                newTokenFileAfterChange.getName()
                    .startsWith("%s-REV.changed-%s-REV.".formatted(SMALLER_DENOMINATION, BIGGER_DENOMINATION)),
                "Token file %s should be called in the format of %s-REV.changed-%s-REV.{N}.token"
                    .formatted(SMALLER_DENOMINATION, newTokenFileAfterChange.getName(), BIGGER_DENOMINATION));
        }

        waitOnBackgroundDeployments();

        // check balance
        long revBalance1Before = checkBalance(REV_WALLET_1);
        long revBalance2Before = checkBalance(REV_WALLET_2);

        // transfer 1 token from rev1 to rev2
        File tokenFileToTransfer = newTokenFilesAfterChange.stream().findFirst()
            .orElseThrow(() -> new RuntimeException("Token file not found"));
        // rev1 -> rev2
        File transferTarget = new File(LOCKED_WALLET_DIR_2, tokenFileToTransfer.getName());
        assertRenameFileLocally(tokenFileToTransfer, transferTarget);

        waitOnBackgroundDeployments();

        // wait on transfer because of the immediate check could get fail sometime
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }

        long revBalance1Expected = revBalance1Before - SMALLER_DENOMINATION;
        long revBalance2Expected = revBalance2Before + SMALLER_DENOMINATION;

        // check balance
        long revBalance1Actual = checkBalance(REV_WALLET_1);
        long revBalance2Actual = checkBalance(REV_WALLET_2);

        long avg_deployment_cost = 50_000L;

        assertEquals(revBalance1Expected, revBalance1Actual, avg_deployment_cost,
            "Balance of wallet 1 should be decreased. Balance before: " + revBalance1Before + ", balance after: "
                + revBalance1Actual);
        assertEquals(revBalance2Expected, revBalance2Actual,
            "Balance of wallet 2 should be increased. Balance before: " + revBalance2Before + ", balance after: "
                + revBalance2Actual);

    }

    @Test
    @DisplayName("Should properly track and update last modified dates for files and directories (manual propose)")
    void shouldTrackLastModifiedDatesForFilesAndDirectories() throws IOException, InterruptedException {

        mountF1r3Drive(true);

        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        // Test file creation and last modified time
        File testFile = new File(UNLOCKED_WALLET_DIR_1, "testFile.txt");
        long beforeFileCreation = waitForTimestampChange();

        assertCreateNewFileLocally(testFile);

        long afterFileCreation = System.currentTimeMillis();
        assertLastModifiedTimeAfterLocally(testFile, beforeFileCreation,
            "File creation operation should set last modified time to creation timestamp");
        assertTrue(testFile.lastModified() <= afterFileCreation,
            "File last modified time validation failed - timestamp should not be in the future. " +
                "File: " + testFile.getAbsolutePath() + ", " +
                "Last modified: " + testFile.lastModified() + " (" + new java.util.Date(testFile.lastModified())
                + "), " +
                "After creation: " + afterFileCreation + " (" + new java.util.Date(afterFileCreation) + ")");

        // Wait and modify file content
        long beforeContentModification = waitForTimestampChange();
        String initialContent = "Initial content";
        Files.writeString(testFile.toPath(), initialContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        byte[] initialDataBytes = initialContent.getBytes();
        assertWrittenDataLocally(testFile, initialDataBytes, "Read data should be equal to written data");
        assertFileContentAtShard(initialDataBytes, testFile);

        assertLastModifiedTimeUpdatedLocally(testFile, beforeContentModification, "writing initial content to file");

        // Test directory creation and last modified time
        File testDir = new File(UNLOCKED_WALLET_DIR_1, "testDirectory");
        long beforeDirCreation = waitForTimestampChange();

        assertCreateNewDirectoryLocally(testDir);

        assertLastModifiedTimeUpdatedLocally(testDir, beforeDirCreation, "directory creation");

        // Test that adding files to directory updates directory's last modified time
        long dirInitialTime = testDir.lastModified();
        long beforeAddingFileToDir = waitForTimestampChange();

        File fileInDir = new File(testDir, "fileInDirectory.txt");
        assertCreateNewFileLocally(fileInDir);

        // Note: Directory last modified time behavior may vary by filesystem
        // Some filesystems update parent directory time when files are added
        long dirTimeAfterAddingFile = testDir.lastModified();
        log.info("Directory timestamp tracking - Dir: {}, Before adding file: {} ({}), After: {} ({}), Changed: {}",
            testDir.getAbsolutePath(),
            dirInitialTime, new java.util.Date(dirInitialTime),
            dirTimeAfterAddingFile, new java.util.Date(dirTimeAfterAddingFile),
            dirTimeAfterAddingFile != dirInitialTime);

        // Test file rename updates last modified time
        long beforeRename = waitForTimestampChange();
        File renamedFile = new File(UNLOCKED_WALLET_DIR_1, "renamedFile.txt");

        assertRenameFileLocally(testFile, renamedFile);

        // Wait after rename to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Renamed file should have updated last modified time
        assertLastModifiedTimeUpdatedLocally(renamedFile, beforeRename, "file rename operation");

        // Test directory rename updates last modified time
        long beforeDirRename = waitForTimestampChange();
        File renamedDir = new File(UNLOCKED_WALLET_DIR_1, "renamedDirectory");

        assertRenameFileLocally(testDir, renamedDir);

        // Wait after directory rename to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertLastModifiedTimeUpdatedLocally(renamedDir, beforeDirRename, "directory rename operation");

        // Test that different files have different last modified times
        File anotherFile = new File(UNLOCKED_WALLET_DIR_1, "anotherFile.txt");
        waitForTimestampChange();
        assertCreateNewFileLocally(anotherFile);

        // Wait after file creation to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertDifferentLastModifiedTimesLocally(renamedFile, anotherFile,
            "Sequential file operations at different time points should result in distinct timestamps");

        // Test file content modification updates time
        String originalContent = Files.readString(renamedFile.toPath());
        long beforeContentUpdate = waitForTimestampChange();
        String updatedContent = originalContent + " - UPDATED";

        Files.writeString(renamedFile.toPath(), updatedContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        byte[] updatedDataBytes = updatedContent.getBytes();
        assertWrittenDataLocally(renamedFile, updatedDataBytes, "Read data should be equal to written data");
        assertFileContentAtShard(updatedDataBytes, renamedFile);

        // Wait after content update to ensure timestamp is properly set
        try {
            Thread.sleep(100); // Short wait to ensure filesystem operations complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertLastModifiedTimeUpdatedLocally(renamedFile, beforeContentUpdate, "file content modification");

        // Verify content was actually updated
        String readContent = Files.readString(renamedFile.toPath());
        assertEquals(updatedContent, readContent,
            "File content validation failed after modification. " +
                "Expected content length: " + updatedContent.length() + " bytes, " +
                "Actual content length: " + readContent.length() + " bytes, " +
                "File: " + renamedFile.getAbsolutePath());

        // Test persistence after remount
        long fileTimeBeforeRemount = renamedFile.lastModified();
        long dirTimeBeforeRemount = renamedDir.lastModified();
        long anotherFileTimeBeforeRemount = anotherFile.lastModified();

        remount();
        assertUnlockWalletDirectory(REV_WALLET_1, PRIVATE_KEY_1);

        // Files should still exist and have the same last modified times after remount
        assertTrue(renamedFile.exists(),
            "Post-remount validation failed: Renamed file should still exist - " + renamedFile.getAbsolutePath());
        assertTrue(renamedDir.exists(), "Post-remount validation failed: Renamed directory should still exist - "
            + renamedDir.getAbsolutePath());
        assertTrue(anotherFile.exists(),
            "Post-remount validation failed: Another file should still exist - " + anotherFile.getAbsolutePath());

        assertLastModifiedTimeApproximatelyLocally(renamedFile, fileTimeBeforeRemount,
            "Persistence validation for renamed file after filesystem remount operation");
        assertLastModifiedTimeApproximatelyLocally(renamedDir, dirTimeBeforeRemount,
            "Persistence validation for renamed directory after filesystem remount operation");
        assertLastModifiedTimeApproximatelyLocally(anotherFile, anotherFileTimeBeforeRemount,
            "Persistence validation for additional file after filesystem remount operation");

    }
}
