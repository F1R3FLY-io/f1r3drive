package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import io.f1r3fly.f1r3drive.platform.PlatformInfo;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MacOSChangeWatcher.
 * Tests macOS-specific file monitoring, FSEvents integration, and File Provider functionality.
 */
class MacOSChangeWatcherTest {

    private MacOSChangeWatcher macOSChangeWatcher;

    @Mock
    private ChangeListener mockChangeListener;

    @Mock
    private FileChangeCallback mockFileChangeCallback;

    @Mock
    private InMemoryFileSystem mockInMemoryFileSystem;

    @Mock
    private FSEventsMonitor mockFSEventsMonitor;

    @Mock
    private FileProviderIntegration mockFileProviderIntegration;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        macOSChangeWatcher = new MacOSChangeWatcher();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (macOSChangeWatcher != null) {
            macOSChangeWatcher.cleanup();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testConstructor_Default() {
        MacOSChangeWatcher watcher = new MacOSChangeWatcher();

        assertNotNull(watcher);
        assertFalse(watcher.isMonitoring());
        assertEquals(PlatformInfo.Platform.MACOS, watcher.getPlatformInfo().getPlatform());
        assertNotNull(watcher.getFSEventsMonitor());
    }

    @Test
    void testConstructor_WithConfiguration() {
        MacOSChangeWatcher watcher = new MacOSChangeWatcher(false, false);

        assertNotNull(watcher);
        assertFalse(watcher.isMonitoring());
        assertEquals(PlatformInfo.Platform.MACOS, watcher.getPlatformInfo().getPlatform());
    }

    @Test
    void testStartMonitoring_Success() throws Exception {
        String testPath = "/test/path";

        // Mock FSEventsMonitor to simulate successful start
        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);

        assertTrue(macOSChangeWatcher.isMonitoring());
        verify(mockFSEventsMonitor).startMonitoring(eq(testPath), any(ChangeListener.class));
    }

    @Test
    void testStartMonitoring_AlreadyMonitoring_ThrowsException() throws Exception {
        String testPath = "/test/path";

        // Mock successful first start
        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);

        // Try to start again
        assertThrows(IllegalStateException.class, () -> {
            macOSChangeWatcher.startMonitoring("/another/path", mockChangeListener);
        });
    }

    @Test
    void testStartMonitoring_NullPath_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            macOSChangeWatcher.startMonitoring(null, mockChangeListener);
        });
    }

    @Test
    void testStartMonitoring_EmptyPath_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            macOSChangeWatcher.startMonitoring("", mockChangeListener);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            macOSChangeWatcher.startMonitoring("   ", mockChangeListener);
        });
    }

    @Test
    void testStartMonitoring_NullListener_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            macOSChangeWatcher.startMonitoring("/test/path", null);
        });
    }

    @Test
    void testStartMonitoring_FSEventsFailure_ThrowsException() throws Exception {
        String testPath = "/test/path";

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doThrow(new RuntimeException("FSEvents failed")).when(mockFSEventsMonitor)
            .startMonitoring(anyString(), any(ChangeListener.class));

        Exception exception = assertThrows(Exception.class, () -> {
            macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);
        });

        assertTrue(exception.getMessage().contains("Failed to start macOS file monitoring"));
        assertFalse(macOSChangeWatcher.isMonitoring());
    }

    @Test
    void testStopMonitoring_Success() throws Exception {
        String testPath = "/test/path";

        // Start monitoring first
        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true).thenReturn(false);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);
        assertTrue(macOSChangeWatcher.isMonitoring());

        macOSChangeWatcher.stopMonitoring();

        verify(mockFSEventsMonitor).stopMonitoring();
        assertFalse(macOSChangeWatcher.isMonitoring());
    }

    @Test
    void testStopMonitoring_NotMonitoring() {
        // Should not throw exception when not monitoring
        assertDoesNotThrow(() -> {
            macOSChangeWatcher.stopMonitoring();
        });
    }

    @Test
    void testStopMonitoring_WithFileProvider() throws Exception {
        String testPath = "/test/path";

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doReturn(mockFileProviderIntegration).when(macOSChangeWatcher).getFileProviderIntegration();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true).thenReturn(false);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);
        macOSChangeWatcher.stopMonitoring();

        verify(mockFSEventsMonitor).stopMonitoring();
        verify(mockFileProviderIntegration).shutdown();
    }

    @Test
    void testSetFileChangeCallback() {
        macOSChangeWatcher.setFileChangeCallback(mockFileChangeCallback);

        // Verify callback is set (we can't directly test this without exposing getter)
        // The callback would be tested through integration with FileProviderIntegration
        assertDoesNotThrow(() -> {
            macOSChangeWatcher.setFileChangeCallback(mockFileChangeCallback);
        });
    }

    @Test
    void testSetInMemoryFileSystem() {
        macOSChangeWatcher.setInMemoryFileSystem(mockInMemoryFileSystem);

        // Verify filesystem is set (integration would be tested in FileProviderIntegration)
        assertDoesNotThrow(() -> {
            macOSChangeWatcher.setInMemoryFileSystem(mockInMemoryFileSystem);
        });
    }

    @Test
    void testSetFileProviderEnabled_WhileMonitoring_ThrowsException() throws Exception {
        String testPath = "/test/path";

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);

        assertThrows(IllegalStateException.class, () -> {
            macOSChangeWatcher.setFileProviderEnabled(false);
        });
    }

    @Test
    void testSetFSEventsLatency_WhileMonitoring_ThrowsException() throws Exception {
        String testPath = "/test/path";

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);

        assertThrows(IllegalStateException.class, () -> {
            macOSChangeWatcher.setFSEventsLatency(0.5);
        });
    }

    @Test
    void testSetFSEventsLatency_NotMonitoring() {
        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();

        assertDoesNotThrow(() -> {
            macOSChangeWatcher.setFSEventsLatency(0.2);
        });

        verify(mockFSEventsMonitor).setLatency(0.2);
    }

    @Test
    void testGetPlatformInfo() {
        PlatformInfo platformInfo = macOSChangeWatcher.getPlatformInfo();

        assertNotNull(platformInfo);
        assertEquals(PlatformInfo.Platform.MACOS, platformInfo.getPlatform());
        assertTrue(platformInfo instanceof MacOSPlatformInfo);
    }

    @Test
    void testCleanup() throws Exception {
        String testPath = "/test/path";

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doReturn(mockFileProviderIntegration).when(macOSChangeWatcher).getFileProviderIntegration();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);
        macOSChangeWatcher.cleanup();

        verify(mockFSEventsMonitor).stopMonitoring();
        verify(mockFileProviderIntegration).shutdown();
        assertFalse(macOSChangeWatcher.isMonitoring());
    }

    @Test
    void testCleanup_NotMonitoring() {
        assertDoesNotThrow(() -> {
            macOSChangeWatcher.cleanup();
        });
    }

    @Test
    void testMacOSChangeListenerAdapter_FileCreated() throws Exception {
        String testPath = "/test/path";
        String filePath = "/test/path/new_file.txt";

        AtomicReference<String> receivedPath = new AtomicReference<>();
        ChangeListener captureListener = new ChangeListener() {
            @Override
            public void onFileCreated(String path) {
                receivedPath.set(path);
            }

            @Override public void onFileModified(String path) {}
            @Override public void onFileDeleted(String path) {}
            @Override public void onFileMoved(String oldPath, String newPath) {}
            @Override public void onFileAccessed(String path) {}
            @Override public void onFileAttributesChanged(String path) {}
            @Override public void onError(Exception error, String path) {}
            @Override public void onMonitoringStarted(String watchedPath) {}
            @Override public void onMonitoringStopped(String watchedPath) {}
        };

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();

        // Capture the adapter listener to simulate FSEvents calls
        AtomicReference<ChangeListener> adapterListener = new AtomicReference<>();
        doAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(1));
            return null;
        }).when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));

        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, captureListener);

        // Simulate FSEvents callback
        adapterListener.get().onFileCreated(filePath);

        assertEquals(filePath, receivedPath.get());
    }

    @Test
    void testMacOSChangeListenerAdapter_FileAccessed_WithCallback() throws Exception {
        String testPath = "/test/path";
        String filePath = "/test/path/accessed_file.txt";
        byte[] fileContent = "test content".getBytes();

        when(mockFileChangeCallback.loadFileContent(filePath)).thenReturn(fileContent);

        AtomicBoolean accessedCalled = new AtomicBoolean(false);
        ChangeListener captureListener = new ChangeListener() {
            @Override public void onFileCreated(String path) {}
            @Override public void onFileModified(String path) {}
            @Override public void onFileDeleted(String path) {}
            @Override public void onFileMoved(String oldPath, String newPath) {}
            @Override public void onFileAttributesChanged(String path) {}
            @Override public void onError(Exception error, String path) {}
            @Override public void onMonitoringStarted(String watchedPath) {}
            @Override public void onMonitoringStopped(String watchedPath) {}

            @Override
            public void onFileAccessed(String path) {
                accessedCalled.set(true);
            }
        };

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();

        AtomicReference<ChangeListener> adapterListener = new AtomicReference<>();
        doAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(1));
            return null;
        }).when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));

        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.setFileChangeCallback(mockFileChangeCallback);
        macOSChangeWatcher.startMonitoring(testPath, captureListener);

        // Simulate file access
        adapterListener.get().onFileAccessed(filePath);

        assertTrue(accessedCalled.get());
        // Note: The lazy loading callback is called but we can't easily verify it
        // due to the private shouldTriggerLazyLoad method returning false
    }

    @Test
    void testMacOSChangeListenerAdapter_Error() throws Exception {
        String testPath = "/test/path";
        RuntimeException testException = new RuntimeException("Test error");

        AtomicReference<Exception> receivedException = new AtomicReference<>();
        AtomicReference<String> receivedPath = new AtomicReference<>();
        ChangeListener captureListener = new ChangeListener() {
            @Override public void onFileCreated(String path) {}
            @Override public void onFileModified(String path) {}
            @Override public void onFileDeleted(String path) {}
            @Override public void onFileMoved(String oldPath, String newPath) {}
            @Override public void onFileAccessed(String path) {}
            @Override public void onFileAttributesChanged(String path) {}
            @Override public void onMonitoringStarted(String watchedPath) {}
            @Override public void onMonitoringStopped(String watchedPath) {}

            @Override
            public void onError(Exception error, String path) {
                receivedException.set(error);
                receivedPath.set(path);
            }
        };

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();

        AtomicReference<ChangeListener> adapterListener = new AtomicReference<>();
        doAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(1));
            return null;
        }).when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));

        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        macOSChangeWatcher.startMonitoring(testPath, captureListener);

        // Simulate error
        adapterListener.get().onError(testException, "/error/path");

        assertEquals(testException, receivedException.get());
        assertEquals("/error/path", receivedPath.get());
    }

    @Test
    void testThread_Safety() throws Exception {
        String testPath = "/test/path";
        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        macOSChangeWatcher = spy(new MacOSChangeWatcher());
        doReturn(mockFSEventsMonitor).when(macOSChangeWatcher).getFSEventsMonitor();
        doNothing().when(mockFSEventsMonitor).startMonitoring(anyString(), any(ChangeListener.class));
        when(mockFSEventsMonitor.isMonitoring()).thenReturn(true);

        // Start monitoring
        macOSChangeWatcher.startMonitoring(testPath, mockChangeListener);

        // Multiple threads trying to access methods simultaneously
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    // Try various operations
                    macOSChangeWatcher.isMonitoring();
                    macOSChangeWatcher.getPlatformInfo();
                    macOSChangeWatcher.setFileChangeCallback(mockFileChangeCallback);
                    macOSChangeWatcher.setInMemoryFileSystem(mockInMemoryFileSystem);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Some operations might fail due to state, but shouldn't cause deadlocks
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // At least some operations should succeed
        assertTrue(successCount.get() > 0);
    }

    // Integration test that only runs on actual macOS systems
    @Test
    @EnabledOnOs(OS.MAC)
    void testRealMacOSIntegration() throws Exception {
        MacOSChangeWatcher realWatcher = new MacOSChangeWatcher();

        try {
            PlatformInfo platformInfo = realWatcher.getPlatformInfo();
            assertNotNull(platformInfo);
            assertEquals(PlatformInfo.Platform.MACOS, platformInfo.getPlatform());

            // Don't test actual monitoring on CI as it requires system permissions
            // Just verify the watcher can be created and queried
            assertFalse(realWatcher.isMonitoring());

        } finally {
            realWatcher.cleanup();
        }
    }
}
