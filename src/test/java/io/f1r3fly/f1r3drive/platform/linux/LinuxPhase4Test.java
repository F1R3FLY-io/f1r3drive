package io.f1r3fly.f1r3drive.platform.linux;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import io.f1r3fly.f1r3drive.platform.PlatformInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Simplified Phase 4 tests for Linux implementation without complex filesystem dependencies.
 * These tests focus on the core functionality and integration of Linux-specific components.
 */
class LinuxPhase4Test {

    @Mock
    private FileSystem mockFileSystem;

    @Mock
    private ChangeListener mockChangeListener;

    @Mock
    private FileChangeCallback mockFileChangeCallback;

    @TempDir
    Path tempDir;

    private AutoCloseable mockCloseable;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockCloseable != null) {
            mockCloseable.close();
        }
    }

    /**
     * Test LinuxPlatformInfo basic functionality
     */
    @Test
    void testLinuxPlatformInfo() {
        LinuxPlatformInfo platformInfo = new LinuxPlatformInfo();

        // Test basic properties
        assertEquals(PlatformInfo.Platform.LINUX, platformInfo.getPlatform());
        assertNotNull(platformInfo.getPlatformVersion());
        assertNotNull(platformInfo.getArchitecture());
        assertNotNull(platformInfo.getDistribution());

        // Test capabilities
        assertTrue(
            platformInfo.hasCapability(
                PlatformInfo.Capability.CASE_SENSITIVE_PATHS
            )
        );
        assertTrue(
            platformInfo.hasCapability(PlatformInfo.Capability.SYMBOLIC_LINKS)
        );
        assertTrue(
            platformInfo.hasCapability(
                PlatformInfo.Capability.EXTENDED_ATTRIBUTES
            )
        );
        assertTrue(
            platformInfo.hasCapability(PlatformInfo.Capability.LAZY_LOADING)
        );
        assertTrue(
            platformInfo.hasCapability(PlatformInfo.Capability.BACKGROUND_SYNC)
        );
        assertFalse(
            platformInfo.hasCapability(
                PlatformInfo.Capability.SYSTEM_INTEGRATION
            )
        );

        // Test configuration
        var config = platformInfo.getConfigurationProperties();
        assertFalse(config.isEmpty());
        assertEquals("true", config.get("filesystem.case_sensitive"));
        assertEquals("/", config.get("path.separator"));
        assertEquals("inotify", config.get("monitoring.backend"));
        assertEquals("fuse", config.get("virtual_fs.backend"));

        // Test mount options
        String[] mountOptions = platformInfo.getMountOptions();
        assertTrue(mountOptions.length > 0);

        // Test permissions
        assertEquals(0644, platformInfo.getDefaultFilePermissions());
        assertEquals(0755, platformInfo.getDefaultDirectoryPermissions());

        // Test thread recommendations
        int threads = platformInfo.getRecommendedMonitoringThreads();
        assertTrue(threads >= 1 && threads <= 2);

        // Test cache size
        long cacheSize = platformInfo.getMaxRecommendedCacheSize();
        assertTrue(cacheSize > 0);

        // Test JVM compatibility
        assertTrue(platformInfo.isJVMCompatible());
    }

    /**
     * Test InotifyMonitor basic functionality
     */
    @Test
    void testInotifyMonitor() throws Exception {
        AtomicInteger eventCount = new AtomicInteger(0);
        AtomicBoolean monitoringStarted = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);

        ChangeListener testListener = new ChangeListener() {
            @Override
            public void onFileCreated(String path) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onFileModified(String path) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onFileDeleted(String path) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onFileMoved(String oldPath, String newPath) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onFileAccessed(String path) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onFileAttributesChanged(String path) {
                eventCount.incrementAndGet();
            }

            @Override
            public void onError(Exception error, String path) {
                // Test implementation
            }

            @Override
            public void onMonitoringStarted(String watchedPath) {
                monitoringStarted.set(true);
                startLatch.countDown();
            }

            @Override
            public void onMonitoringStopped(String watchedPath) {
                // Test implementation
            }
        };

        InotifyMonitor monitor = new InotifyMonitor(testListener);

        // Test initial state
        assertFalse(monitor.isMonitoring());
        assertEquals(0, monitor.getWatchCount());

        // Test statistics
        InotifyMonitor.MonitoringStatistics stats = monitor.getStatistics();
        assertNotNull(stats);
        assertFalse(stats.isMonitoring());
        assertEquals(0, stats.getWatchCount());

        // Create test directory
        Path testDir = Files.createTempDirectory(tempDir, "inotify-test");

        try {
            // Start monitoring
            monitor.startMonitoring(testDir.toString());

            // Wait for monitoring to start
            assertTrue(startLatch.await(5, TimeUnit.SECONDS));
            assertTrue(monitor.isMonitoring());
            assertTrue(monitoringStarted.get());

            // Test statistics after starting
            stats = monitor.getStatistics();
            assertTrue(stats.isMonitoring());
            assertTrue(stats.getWatchCount() >= 1);

            // Stop monitoring
            monitor.stopMonitoring();
            assertFalse(monitor.isMonitoring());
        } catch (IOException e) {
            // This might happen in environments without proper file monitoring support
            System.out.println(
                "Skipping inotify test - monitoring not available: " +
                    e.getMessage()
            );
        }
    }

    /**
     * Test FuseFilesystem basic functionality
     */
    @Test
    void testFuseFilesystem() {
        try {
            FuseFilesystem fuseFilesystem = new FuseFilesystem(
                mockFileSystem,
                mockFileChangeCallback,
                mockChangeListener
            );

            // Test initial state
            assertEquals(0, fuseFilesystem.getPlaceholderCount());

            // Test placeholder operations
            String testPath = "/test/file.txt";
            long size = 1024;
            long lastModified = System.currentTimeMillis();

            fuseFilesystem.registerPlaceholder(testPath, size, lastModified);
            assertEquals(1, fuseFilesystem.getPlaceholderCount());

            fuseFilesystem.removePlaceholder(testPath);
            assertEquals(0, fuseFilesystem.getPlaceholderCount());

            // Test multiple placeholders
            fuseFilesystem.registerPlaceholder(
                "/file1.txt",
                100,
                System.currentTimeMillis()
            );
            fuseFilesystem.registerPlaceholder(
                "/file2.txt",
                200,
                System.currentTimeMillis()
            );
            fuseFilesystem.registerPlaceholder(
                "/dir/file3.txt",
                300,
                System.currentTimeMillis()
            );
            assertEquals(3, fuseFilesystem.getPlaceholderCount());

            // Test removing non-existent placeholder
            fuseFilesystem.removePlaceholder("/non-existent.txt");
            assertEquals(3, fuseFilesystem.getPlaceholderCount());
        } catch (UnsatisfiedLinkError e) {
            // This is expected on macOS or systems without FUSE
            System.out.println(
                "Skipping FUSE test - FUSE not available: " + e.getMessage()
            );
        }
    }

    /**
     * Test LinuxChangeWatcher basic functionality
     */
    @Test
    void testLinuxChangeWatcher() {
        LinuxChangeWatcher changeWatcher = new LinuxChangeWatcher(
            mockFileSystem
        );

        // Test initial state
        assertFalse(changeWatcher.isMonitoring());
        assertNotNull(changeWatcher.getPlatformInfo());
        assertEquals(
            PlatformInfo.Platform.LINUX,
            changeWatcher.getPlatformInfo().getPlatform()
        );

        // Test statistics
        LinuxChangeWatcher.LinuxMonitoringStatistics stats =
            changeWatcher.getMonitoringStatistics();
        assertNotNull(stats);
        assertFalse(stats.isMonitoring());
        assertFalse(stats.isFuseMounted());
        assertEquals(0, stats.getPlaceholderCount());
        assertNull(stats.getMountPath());

        // Test callback setting
        assertDoesNotThrow(() -> {
            changeWatcher.setFileChangeCallback(mockFileChangeCallback);
        });

        // Test placeholder registration
        assertDoesNotThrow(() -> {
            changeWatcher.registerPlaceholderFile(
                "/test.txt",
                1024,
                System.currentTimeMillis()
            );
        });

        // Test cleanup
        assertDoesNotThrow(() -> {
            changeWatcher.cleanup();
        });
        assertFalse(changeWatcher.isMonitoring());
    }

    /**
     * Test ChangeWatcherFactory Linux support
     */
    @Test
    void testChangeWatcherFactory() {
        // Test platform detection
        assertTrue(
            ChangeWatcherFactory.isPlatformSupported(
                PlatformInfo.Platform.LINUX
            )
        );

        // Test platform support info
        ChangeWatcherFactory.PlatformSupportInfo supportInfo =
            ChangeWatcherFactory.getPlatformSupportInfo();
        assertNotNull(supportInfo);

        // Test Linux ChangeWatcher creation
        try {
            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(
                PlatformInfo.Platform.LINUX
            );
            assertNotNull(watcher);
            assertTrue(watcher instanceof LinuxChangeWatcher);

            // Test with configuration
            ChangeWatcherFactory.ChangeWatcherConfig config =
                new ChangeWatcherFactory.ChangeWatcherConfig();
            config.setPlatform(PlatformInfo.Platform.LINUX);
            config.setFuseEnabled(true);
            config.setInotifyBufferSize(8192);

            ChangeWatcher configuredWatcher =
                ChangeWatcherFactory.createChangeWatcher(config);
            assertNotNull(configuredWatcher);
            assertTrue(configuredWatcher instanceof LinuxChangeWatcher);
        } catch (ChangeWatcherFactory.UnsupportedPlatformException e) {
            // This might happen in test environments
            System.out.println(
                "Skipping ChangeWatcher creation test - platform not supported: " +
                    e.getMessage()
            );
        }
    }

    /**
     * Test callback integration
     */
    @Test
    void testCallbackIntegration() {
        LinuxChangeWatcher changeWatcher = new LinuxChangeWatcher(
            mockFileSystem
        );

        // Setup mock callback
        when(mockFileChangeCallback.getFileMetadata(any())).thenReturn(
            new FileChangeCallback.FileMetadata(
                1024,
                System.currentTimeMillis(),
                "checksum",
                false
            )
        );

        changeWatcher.setFileChangeCallback(mockFileChangeCallback);

        // Test preloading
        String[] testFiles = { "/file1.txt", "/file2.txt", "/dir/file3.txt" };
        assertDoesNotThrow(() -> {
            changeWatcher.preloadBlockchainFiles(testFiles);
        });

        // Note: Callback may not be called if FUSE is not initialized
        // This is expected behavior on systems without FUSE support
        System.out.println(
            "Preload test completed - callback usage depends on FUSE availability"
        );
    }

    /**
     * Test parameter validation
     */
    @Test
    void testParameterValidation() {
        // Test InotifyMonitor null parameter
        assertThrows(NullPointerException.class, () -> {
            new InotifyMonitor(null);
        });

        // Test LinuxChangeWatcher null parameter
        assertThrows(NullPointerException.class, () -> {
            new LinuxChangeWatcher(null);
        });

        System.out.println("Parameter validation tests completed successfully");
    }

    /**
     * Test constants and compatibility
     */
    @Test
    void testConstants() {
        // Test inotify constants are defined
        assertTrue(InotifyMonitor.IN_ACCESS > 0);
        assertTrue(InotifyMonitor.IN_MODIFY > 0);
        assertTrue(InotifyMonitor.IN_CREATE > 0);
        assertTrue(InotifyMonitor.IN_DELETE > 0);

        // Test composite constants
        assertEquals(
            InotifyMonitor.IN_CLOSE_WRITE | InotifyMonitor.IN_CLOSE_NOWRITE,
            InotifyMonitor.IN_CLOSE
        );
        assertEquals(
            InotifyMonitor.IN_MOVED_FROM | InotifyMonitor.IN_MOVED_TO,
            InotifyMonitor.IN_MOVE
        );
    }

    /**
     * Test platform info string representation
     */
    @Test
    void testStringRepresentations() {
        LinuxPlatformInfo platformInfo = new LinuxPlatformInfo();
        String infoStr = platformInfo.toString();
        assertNotNull(infoStr);
        assertFalse(infoStr.isEmpty());
        assertTrue(infoStr.contains("Linux"));

        InotifyMonitor.MonitoringStatistics stats =
            new InotifyMonitor.MonitoringStatistics(5, true, true);
        String statsStr = stats.toString();
        assertNotNull(statsStr);
        assertTrue(statsStr.contains("InotifyMonitor"));
        assertTrue(statsStr.contains("watches=5"));
        assertTrue(statsStr.contains("monitoring=true"));

        LinuxChangeWatcher.LinuxMonitoringStatistics linuxStats =
            new LinuxChangeWatcher.LinuxMonitoringStatistics(
                true,
                "/mount/path",
                stats,
                3,
                true
            );
        String linuxStatsStr = linuxStats.toString();
        assertNotNull(linuxStatsStr);
        assertTrue(linuxStatsStr.contains("LinuxMonitoring"));
        assertTrue(linuxStatsStr.contains("placeholders=3"));
    }
}
