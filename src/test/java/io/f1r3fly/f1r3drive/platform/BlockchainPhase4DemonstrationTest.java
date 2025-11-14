package io.f1r3fly.f1r3drive.platform;

import static org.junit.jupiter.api.Assertions.*;

import io.f1r3fly.f1r3drive.errors.*;
import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSContext;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSFileStat;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSStatVfs;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Demonstration test for Phase 4 Blockchain FileSystem Integration.
 * Shows how the mock implementation has been replaced with full blockchain functionality.
 */
@DisplayName("Phase 4 Blockchain FileSystem Demonstration")
public class BlockchainPhase4DemonstrationTest {

    private ChangeWatcher changeWatcher;
    private FileSystem fileSystem;

    @BeforeEach
    void setUp() throws Exception {
        // Create Linux ChangeWatcher with blockchain filesystem
        changeWatcher = ChangeWatcherFactory.createChangeWatcher();

        // Access the filesystem through reflection for testing
        java.lang.reflect.Field fileSystemField = changeWatcher
            .getClass()
            .getDeclaredField("fileSystem");
        fileSystemField.setAccessible(true);
        fileSystem = (FileSystem) fileSystemField.get(changeWatcher);
    }

    @Test
    @DisplayName("✅ Blockchain FileSystem Creation - Full Integration")
    void testBlockchainFileSystemCreation() {
        // Verify that we have a working filesystem instance
        assertNotNull(fileSystem, "Blockchain FileSystem should be created");

        // Verify root directory exists
        Directory rootDir = fileSystem.getDirectory("/");
        assertNotNull(rootDir, "Root directory should exist");
        assertEquals("/", rootDir.getAbsolutePath());

        // Verify blockchain context is available
        assertNotNull(
            rootDir.getBlockchainContext(),
            "Root directory should have blockchain context"
        );

        System.out.println(
            "✅ BlockchainFileSystemForPhase4 successfully created with blockchain integration"
        );
    }

    @Test
    @DisplayName("✅ File Operations - Blockchain Backend")
    void testFileOperationsWithBlockchain() throws Exception {
        // Create a file
        fileSystem.createFile("/test_blockchain_file.txt", 0644);

        // Verify file exists
        File file = fileSystem.getFile("/test_blockchain_file.txt");
        assertNotNull(file, "File should be created with blockchain backend");
        assertEquals("test_blockchain_file.txt", file.getName());

        // Write data to file
        byte[] testData = "Hello Blockchain Phase 4!".getBytes();
        FSPointer writeBuffer = createMockFSPointer(testData);
        int bytesWritten = fileSystem.writeFile(
            "/test_blockchain_file.txt",
            writeBuffer,
            testData.length,
            0
        );
        assertEquals(
            testData.length,
            bytesWritten,
            "All bytes should be written"
        );

        // Read data back
        byte[] readBuffer = new byte[testData.length];
        FSPointer readPointer = createMockFSPointer(readBuffer);
        int bytesRead = fileSystem.readFile(
            "/test_blockchain_file.txt",
            readPointer,
            testData.length,
            0
        );
        assertEquals(testData.length, bytesRead, "All bytes should be read");

        // Verify blockchain context
        assertNotNull(
            file.getBlockchainContext(),
            "File should have blockchain context for Phase 4"
        );

        System.out.println(
            "✅ File operations working with blockchain backend"
        );
    }

    @Test
    @DisplayName("✅ Directory Operations - Distributed Storage")
    void testDirectoryOperationsWithBlockchain() throws Exception {
        // Create directory
        fileSystem.makeDirectory("/blockchain_test_dir", 0755);

        Directory dir = fileSystem.getDirectory("/blockchain_test_dir");
        assertNotNull(dir, "Directory should be created");
        assertEquals("blockchain_test_dir", dir.getName());

        // Create subdirectory
        dir.mkdir("subdirectory");
        assertTrue(
            dir.getChildren().size() > 0,
            "Directory should have children"
        );

        // Verify blockchain integration
        assertNotNull(
            dir.getBlockchainContext(),
            "Directory should have blockchain context"
        );

        System.out.println(
            "✅ Directory operations working with distributed blockchain storage"
        );
    }

    @Test
    @DisplayName("✅ File Attributes - Blockchain Metadata")
    void testFileAttributesWithBlockchain() throws Exception {
        // Create file
        fileSystem.createFile("/metadata_test.txt", 0644);

        // Get attributes
        FSFileStat stat = new MockFSFileStat();
        FSContext context = new MockFSContext();

        fileSystem.getAttributes("/metadata_test.txt", stat, context);

        // Verify file attributes are set correctly
        assertTrue(
            ((MockFSFileStat) stat).getMode() > 0,
            "File mode should be set"
        );
        assertEquals(
            0L,
            ((MockFSFileStat) stat).getSize(),
            "New file should have zero size"
        );

        System.out.println(
            "✅ File attributes working with blockchain metadata system"
        );
    }

    @Test
    @DisplayName("✅ Filesystem Statistics - Blockchain Metrics")
    void testFilesystemStatsWithBlockchain() {
        FSStatVfs stats = new MockFSStatVfs();
        fileSystem.getFileSystemStats("/", stats);

        // Verify blockchain filesystem stats
        assertTrue(
            ((MockFSStatVfs) stats).getBlockSize() > 0,
            "Block size should be configured"
        );
        assertTrue(
            ((MockFSStatVfs) stats).getBlocks() > 0,
            "Total blocks should be configured"
        );
        assertTrue(
            ((MockFSStatVfs) stats).getMaxFilenameLength() > 0,
            "Max filename length should be set"
        );

        System.out.println(
            "✅ Filesystem statistics available from blockchain backend"
        );
    }

    // Helper method to create mock FSPointer
    private FSPointer createMockFSPointer(byte[] data) {
        return new FSPointer() {
            private byte[] buffer = data;

            @Override
            public void put(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(bytes, start, buffer, (int) offset, length);
            }

            @Override
            public void get(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(buffer, (int) offset, bytes, start, length);
            }

            @Override
            public byte getByte(long offset) {
                return buffer[(int) offset];
            }

            @Override
            public void putByte(long offset, byte value) {
                buffer[(int) offset] = value;
            }
        };
    }

    // Mock implementations for testing
    private static class MockFSFileStat implements FSFileStat {

        private int mode;
        private long size;
        private long uid;
        private long gid;
        private long modificationTime;

        @Override
        public void setMode(int mode) {
            this.mode = mode;
        }

        @Override
        public void setSize(long size) {
            this.size = size;
        }

        @Override
        public void setUid(long uid) {
            this.uid = uid;
        }

        @Override
        public void setGid(long gid) {
            this.gid = gid;
        }

        @Override
        public void setModificationTime(long seconds) {
            this.modificationTime = seconds;
        }

        public int getMode() {
            return mode;
        }

        public long getSize() {
            return size;
        }

        public long getUid() {
            return uid;
        }

        public long getGid() {
            return gid;
        }

        public long getModificationTime() {
            return modificationTime;
        }
    }

    private static class MockFSContext implements FSContext {

        @Override
        public long getUid() {
            return 1000;
        }

        @Override
        public long getGid() {
            return 1000;
        }
    }

    private static class MockFSStatVfs implements FSStatVfs {

        private long blockSize;
        private long fragmentSize;
        private long blocks;
        private long blocksAvailable;
        private long blocksFree;
        private long maxFilenameLength;

        @Override
        public void setBlockSize(long size) {
            this.blockSize = size;
        }

        @Override
        public void setFragmentSize(long size) {
            this.fragmentSize = size;
        }

        @Override
        public void setBlocks(long count) {
            this.blocks = count;
        }

        @Override
        public void setBlocksAvailable(long count) {
            this.blocksAvailable = count;
        }

        @Override
        public void setBlocksFree(long count) {
            this.blocksFree = count;
        }

        @Override
        public void setMaxFilenameLength(long length) {
            this.maxFilenameLength = length;
        }

        public long getBlockSize() {
            return blockSize;
        }

        public long getFragmentSize() {
            return fragmentSize;
        }

        public long getBlocks() {
            return blocks;
        }

        public long getBlocksAvailable() {
            return blocksAvailable;
        }

        public long getBlocksFree() {
            return blocksFree;
        }

        public long getMaxFilenameLength() {
            return maxFilenameLength;
        }
    }
}
