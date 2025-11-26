package io.f1r3fly.f1r3drive.fuse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.app.linux.fuse.F1r3DriveFuse;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderInfo;
import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory;
import io.f1r3fly.f1r3drive.platform.F1r3DriveChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

/**
 * Integration test for F1r3DriveFuse platform integration functionality.
 * Tests the new initialize, start, and shutdown methods with platform monitoring.
 */
class F1r3DriveFuseIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private F1r3flyBlockchainClient mockBlockchainClient;

    @Mock
    private ChangeWatcher mockChangeWatcher;

    @Mock
    private BlockchainContext mockBlockchainContext;

    private RevWalletInfo testWalletInfo;

    private F1r3DriveFuse f1r3DriveFuse;
    private String testMountPath;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup test mount path
        testMountPath = tempDir.resolve("test-mount").toString();

        // Create F1r3DriveFuse instance
        f1r3DriveFuse = new F1r3DriveFuse(mockBlockchainClient);

        // Create real RevWalletInfo instead of mocking
        testWalletInfo = new RevWalletInfo(
            "test-rev-address",
            "test-key".getBytes()
        );

        // Setup mock behavior
        when(mockBlockchainContext.getWalletInfo()).thenReturn(testWalletInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (f1r3DriveFuse != null) {
            try {
                f1r3DriveFuse.shutdown();
            } catch (Exception e) {
                // Ignore cleanup errors in tests
            }
        }
    }

    @Test
    void testInitializeCreatesRequiredComponents() throws Exception {
        // Mock the ChangeWatcherFactory to return our mock
        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            // Test initialization
            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // Verify that the factory was called
            factoryMock.verify(
                () -> ChangeWatcherFactory.createChangeWatcher(),
                times(1)
            );
        }

        // At this point, internal components should be created
        // We can't directly access private fields, but we can test behavior
        assertDoesNotThrow(() -> f1r3DriveFuse.start(testMountPath));
    }

    @Test
    void testStartActivatesMonitoring() throws Exception {
        // Setup
        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // Test start
            f1r3DriveFuse.start(testMountPath);

            // Verify that monitoring was started
            verify(mockChangeWatcher).startMonitoring(
                eq(testMountPath),
                any(ChangeListener.class)
            );
        }
    }

    @Test
    void testShutdownCleansUpResources() throws Exception {
        // Setup
        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);
            f1r3DriveFuse.start(testMountPath);

            // Test shutdown
            f1r3DriveFuse.shutdown();

            // Verify cleanup was called
            verify(mockChangeWatcher).stopMonitoring();
            verify(mockChangeWatcher).cleanup();
        }
    }

    @Test
    void testChangeListenerIntegration() throws Exception {
        // This test verifies that F1r3DriveChangeListener properly integrates
        // with the placeholder system

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // Capture the change listener that was passed to startMonitoring
            verify(mockChangeWatcher).setFileChangeCallback(any());

            f1r3DriveFuse.start(testMountPath);

            verify(mockChangeWatcher).startMonitoring(
                eq(testMountPath),
                any(F1r3DriveChangeListener.class)
            );
        }
    }

    @Test
    void testInitializeWithoutPlatformSupport() throws Exception {
        // Test behavior when platform is not supported
        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenThrow(
                    new ChangeWatcherFactory.UnsupportedPlatformException(
                        "Test platform not supported"
                    )
                );

            // Should throw exception when platform is not supported
            assertThrows(Exception.class, () -> {
                f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);
            });
        }
    }

    @Test
    void testStateChangeEventsManagerIntegration() throws Exception {
        // Test that StateChangeEventsManager properly integrates with change listening

        StateChangeEventsManager eventsManager = new StateChangeEventsManager();

        // Test setting change listener
        ChangeListener testListener = new ChangeListener() {
            @Override
            public void onFileCreated(String path) {
                // Test implementation
            }

            @Override
            public void onFileModified(String path) {
                // Test implementation
            }

            @Override
            public void onFileDeleted(String path) {
                // Test implementation
            }

            @Override
            public void onFileMoved(String oldPath, String newPath) {
                // Test implementation
            }

            @Override
            public void onFileAccessed(String path) {
                // Test implementation
            }

            @Override
            public void onFileAttributesChanged(String path) {
                // Test implementation
            }

            @Override
            public void onError(Exception error, String path) {
                // Test implementation
            }

            @Override
            public void onMonitoringStarted(String watchedPath) {
                // Test implementation
            }

            @Override
            public void onMonitoringStopped(String watchedPath) {
                // Test implementation
            }
        };

        // Test that we can set a change listener without errors
        assertDoesNotThrow(() -> {
            eventsManager.setChangeListener(testListener);
        });

        // Test notification methods
        assertDoesNotThrow(() -> {
            eventsManager.notifyFileChange(
                "/test/path",
                StateChangeEventsManager.ChangeType.CREATED
            );
            eventsManager.notifyFileMove("/old/path", "/new/path");
            eventsManager.notifyExternalChange();
        });

        // Cleanup
        eventsManager.shutdown();
    }

    @Test
    void testPlaceholderManagerIntegration() throws Exception {
        // Test that PlaceholderManager methods work correctly

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // The PlaceholderManager should be created and functional
            // We can't access it directly, but we can test that the system starts without errors
            assertDoesNotThrow(() -> f1r3DriveFuse.start(testMountPath));
        }
    }

    @Test
    void testFileChangeCallbackExecution() throws Exception {
        // Test that file change callback properly handles loading requests

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // Verify that a FileChangeCallback was set
            verify(mockChangeWatcher).setFileChangeCallback(any());
        }
    }

    @Test
    void testErrorHandlingDuringInitialization() throws Exception {
        // Test that errors during initialization are properly handled

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            // Mock an error during ChangeWatcher creation
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenThrow(
                    new RuntimeException("Simulated initialization error")
                );

            // Should handle the error gracefully
            assertThrows(Exception.class, () -> {
                f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);
            });
        }
    }

    @Test
    void testShutdownWithoutStart() throws Exception {
        // Test that shutdown works even if start was never called

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);

            // Should not throw exception when shutdown is called without start
            assertDoesNotThrow(() -> f1r3DriveFuse.shutdown());
        }
    }

    @Test
    void testMultipleShutdownCalls() throws Exception {
        // Test that multiple shutdown calls don't cause issues

        try (
            MockedStatic<ChangeWatcherFactory> factoryMock = mockStatic(
                ChangeWatcherFactory.class
            )
        ) {
            factoryMock
                .when(() -> ChangeWatcherFactory.createChangeWatcher())
                .thenReturn(mockChangeWatcher);

            f1r3DriveFuse.initialize(testMountPath, mockBlockchainContext);
            f1r3DriveFuse.start(testMountPath);

            // Multiple shutdowns should be safe
            assertDoesNotThrow(() -> {
                f1r3DriveFuse.shutdown();
                f1r3DriveFuse.shutdown();
                f1r3DriveFuse.shutdown();
            });
        }
    }
}
