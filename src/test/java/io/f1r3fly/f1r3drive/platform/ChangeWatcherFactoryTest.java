package io.f1r3fly.f1r3drive.platform;

import io.f1r3fly.f1r3drive.platform.macos.MacOSChangeWatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ChangeWatcherFactory.
 * Tests platform detection, watcher creation, and error conditions.
 */
class ChangeWatcherFactoryTest {

    @BeforeEach
    void setUp() {
        // Reset any static state if needed
    }

    @Test
    void testCreateChangeWatcher_AutoDetection_Success() throws Exception {
        // Mock platform detection to return a supported platform
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.MACOS);

            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher();

            assertNotNull(watcher);
            assertTrue(watcher instanceof MacOSChangeWatcher);
            assertEquals(PlatformInfo.Platform.MACOS, watcher.getPlatformInfo().getPlatform());
        }
    }

    @Test
    void testCreateChangeWatcher_UnsupportedPlatform_ThrowsException() {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.WINDOWS);

            assertThrows(ChangeWatcherFactory.UnsupportedPlatformException.class, () -> {
                ChangeWatcherFactory.createChangeWatcher();
            });
        }
    }

    @Test
    void testCreateChangeWatcher_WithSpecificPlatform_macOS_Success() throws Exception {
        ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(PlatformInfo.Platform.MACOS);

        assertNotNull(watcher);
        assertTrue(watcher instanceof MacOSChangeWatcher);
        assertEquals(PlatformInfo.Platform.MACOS, watcher.getPlatformInfo().getPlatform());
    }

    @Test
    void testCreateChangeWatcher_WithSpecificPlatform_Linux_ThrowsException() {
        assertThrows(ChangeWatcherFactory.UnsupportedPlatformException.class, () -> {
            ChangeWatcherFactory.createChangeWatcher(PlatformInfo.Platform.LINUX);
        });
    }

    @Test
    void testCreateChangeWatcher_WithSpecificPlatform_Windows_ThrowsException() {
        assertThrows(ChangeWatcherFactory.UnsupportedPlatformException.class, () -> {
            ChangeWatcherFactory.createChangeWatcher(PlatformInfo.Platform.WINDOWS);
        });
    }

    @Test
    void testCreateChangeWatcher_NullPlatform_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ChangeWatcherFactory.createChangeWatcher((PlatformInfo.Platform) null);
        });
    }

    @Test
    void testCreateChangeWatcher_WithConfig_DefaultPlatform() throws Exception {
        ChangeWatcherFactory.ChangeWatcherConfig config = new ChangeWatcherFactory.ChangeWatcherConfig();
        // config.setPlatform(null); // will auto-detect

        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.MACOS);

            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(config);

            assertNotNull(watcher);
            assertTrue(watcher instanceof MacOSChangeWatcher);
        }
    }

    @Test
    void testCreateChangeWatcher_WithConfig_SpecificPlatform() throws Exception {
        ChangeWatcherFactory.ChangeWatcherConfig config = new ChangeWatcherFactory.ChangeWatcherConfig();
        config.setPlatform(PlatformInfo.Platform.MACOS);
        config.setFileProviderEnabled(false);
        config.setDeepIntegrationEnabled(false);

        ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(config);

        assertNotNull(watcher);
        assertTrue(watcher instanceof MacOSChangeWatcher);

        MacOSChangeWatcher macOSWatcher = (MacOSChangeWatcher) watcher;
        // Note: We can't directly test the config was applied without exposing getters
        // This would be tested in MacOSChangeWatcherTest
    }

    @Test
    void testCreateChangeWatcher_WithConfig_Null_UsesAutoDetection() throws Exception {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.MACOS);

            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher((ChangeWatcherFactory.ChangeWatcherConfig) null);

            assertNotNull(watcher);
            assertTrue(watcher instanceof MacOSChangeWatcher);
        }
    }

    @Test
    void testIsCurrentPlatformSupported_macOS() {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.MACOS);

            assertTrue(ChangeWatcherFactory.isCurrentPlatformSupported());
        }
    }

    @Test
    void testIsCurrentPlatformSupported_Linux() {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.LINUX);

            assertFalse(ChangeWatcherFactory.isCurrentPlatformSupported());
        }
    }

    @Test
    void testIsCurrentPlatformSupported_Windows() {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.WINDOWS);

            assertFalse(ChangeWatcherFactory.isCurrentPlatformSupported());
        }
    }

    @Test
    void testIsPlatformSupported_macOS() {
        assertTrue(ChangeWatcherFactory.isPlatformSupported(PlatformInfo.Platform.MACOS));
    }

    @Test
    void testIsPlatformSupported_Linux() {
        assertFalse(ChangeWatcherFactory.isPlatformSupported(PlatformInfo.Platform.LINUX));
    }

    @Test
    void testIsPlatformSupported_Windows() {
        assertFalse(ChangeWatcherFactory.isPlatformSupported(PlatformInfo.Platform.WINDOWS));
    }

    @Test
    void testIsPlatformSupported_Null() {
        assertFalse(ChangeWatcherFactory.isPlatformSupported(null));
    }

    @Test
    void testGetPlatformSupportInfo() {
        try (MockedStatic<PlatformDetector> platformDetectorMock = mockStatic(PlatformDetector.class)) {
            platformDetectorMock.when(PlatformDetector::detectPlatform)
                    .thenReturn(PlatformInfo.Platform.MACOS);
            platformDetectorMock.when(PlatformDetector::getArchitecture)
                    .thenReturn("x86_64");
            platformDetectorMock.when(PlatformDetector::getOSVersion)
                    .thenReturn("12.0");
            platformDetectorMock.when(PlatformDetector::getJavaVersion)
                    .thenReturn("17.0.1");

            ChangeWatcherFactory.PlatformSupportInfo info = ChangeWatcherFactory.getPlatformSupportInfo();

            assertNotNull(info);
            assertEquals(PlatformInfo.Platform.MACOS, info.getPlatform());
            assertEquals("x86_64", info.getArchitecture());
            assertEquals("12.0", info.getOsVersion());
            assertEquals("17.0.1", info.getJavaVersion());
            assertTrue(info.canCreateWatcher());
            assertTrue(info.isSupported());
            assertNotNull(info.getRequiredFeatures());
            assertNotNull(info.getMissingFeatures());
        }
    }

    @Test
    void testChangeWatcherConfig_DefaultValues() {
        ChangeWatcherFactory.ChangeWatcherConfig config = new ChangeWatcherFactory.ChangeWatcherConfig();

        assertNull(config.getPlatform()); // Auto-detect by default
        assertTrue(config.isFileProviderEnabled());
        assertTrue(config.isDeepIntegrationEnabled());
        assertEquals(0.1, config.getFSEventsLatency(), 0.001);
        assertTrue(config.isFuseEnabled());
        assertEquals(8192, config.getInotifyBufferSize());
    }

    @Test
    void testChangeWatcherConfig_SettersAndGetters() {
        ChangeWatcherFactory.ChangeWatcherConfig config = new ChangeWatcherFactory.ChangeWatcherConfig();

        config.setPlatform(PlatformInfo.Platform.LINUX);
        config.setFileProviderEnabled(false);
        config.setDeepIntegrationEnabled(false);
        config.setFSEventsLatency(0.5);
        config.setFuseEnabled(false);
        config.setInotifyBufferSize(16384);

        assertEquals(PlatformInfo.Platform.LINUX, config.getPlatform());
        assertFalse(config.isFileProviderEnabled());
        assertFalse(config.isDeepIntegrationEnabled());
        assertEquals(0.5, config.getFSEventsLatency(), 0.001);
        assertFalse(config.isFuseEnabled());
        assertEquals(16384, config.getInotifyBufferSize());
    }

    @Test
    void testUnsupportedPlatformException_WithMessage() {
        String message = "Test exception message";
        ChangeWatcherFactory.UnsupportedPlatformException exception =
            new ChangeWatcherFactory.UnsupportedPlatformException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testUnsupportedPlatformException_WithMessageAndCause() {
        String message = "Test exception message";
        Throwable cause = new RuntimeException("Root cause");
        ChangeWatcherFactory.UnsupportedPlatformException exception =
            new ChangeWatcherFactory.UnsupportedPlatformException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    // Integration test for real platform detection (only runs on actual platforms)
    @Test
    @EnabledOnOs(OS.MAC)
    void testCreateChangeWatcher_RealMacOS() throws Exception {
        // This test only runs on actual macOS systems
        ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher();

        assertNotNull(watcher);
        assertTrue(watcher instanceof MacOSChangeWatcher);
        assertEquals(PlatformInfo.Platform.MACOS, watcher.getPlatformInfo().getPlatform());

        // Cleanup
        watcher.cleanup();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testCreateChangeWatcher_RealLinux_ThrowsException() {
        // This test only runs on actual Linux systems
        // Linux implementation is not yet available
        assertThrows(ChangeWatcherFactory.UnsupportedPlatformException.class, () -> {
            ChangeWatcherFactory.createChangeWatcher();
        });
    }
}
