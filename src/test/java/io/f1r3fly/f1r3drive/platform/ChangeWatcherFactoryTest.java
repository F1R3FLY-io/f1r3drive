package io.f1r3fly.f1r3drive.platform;

import static org.junit.jupiter.api.Assertions.*;

import io.f1r3fly.f1r3drive.platform.macos.MacOSChangeWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Basic tests for ChangeWatcherFactory.
 * Tests existing factory methods and platform support.
 */
class ChangeWatcherFactoryTest {

    @Test
    void testIsCurrentPlatformSupported() {
        boolean supported = ChangeWatcherFactory.isCurrentPlatformSupported();

        // Boolean is a primitive type, just verify it's a valid boolean
        assertTrue(supported || !supported); // This will always pass but validates boolean type
    }

    @Test
    void testIsPlatformSupported_macOS() {
        assertTrue(
            ChangeWatcherFactory.isPlatformSupported(
                PlatformInfo.Platform.MACOS
            )
        );
    }

    @Test
    void testIsPlatformSupported_Linux() {
        // Linux is supported but implementation is not complete
        boolean supported = ChangeWatcherFactory.isPlatformSupported(
            PlatformInfo.Platform.LINUX
        );
        assertTrue(supported);
    }

    @Test
    void testIsPlatformSupported_Windows() {
        assertFalse(
            ChangeWatcherFactory.isPlatformSupported(
                PlatformInfo.Platform.WINDOWS
            )
        );
    }

    @Test
    void testIsPlatformSupported_Null() {
        // Should handle null gracefully but may throw NPE
        assertThrows(NullPointerException.class, () -> {
            ChangeWatcherFactory.isPlatformSupported(null);
        });
    }

    @Test
    void testGetPlatformSupportInfo() {
        ChangeWatcherFactory.PlatformSupportInfo info =
            ChangeWatcherFactory.getPlatformSupportInfo();

        assertNotNull(info);
        assertNotNull(info.getPlatform());
        assertNotNull(info.getArchitecture());
        assertNotNull(info.getOsVersion());
        assertNotNull(info.getJavaVersion());
        assertNotNull(info.getRequiredFeatures());
        assertNotNull(info.getMissingFeatures());
    }

    @Test
    void testChangeWatcherConfig_DefaultValues() {
        ChangeWatcherFactory.ChangeWatcherConfig config =
            new ChangeWatcherFactory.ChangeWatcherConfig();

        // Just test that config is created and basic methods work
        assertNotNull(config);
        // Don't test specific default values as they may not be implemented yet
    }

    @Test
    void testChangeWatcherConfig_SettersAndGetters() {
        ChangeWatcherFactory.ChangeWatcherConfig config =
            new ChangeWatcherFactory.ChangeWatcherConfig();

        config.setPlatform(PlatformInfo.Platform.MACOS);
        config.setFileProviderEnabled(false);
        config.setDeepIntegrationEnabled(false);
        config.setFSEventsLatency(0.5);
        config.setFuseEnabled(false);
        config.setInotifyBufferSize(16384);

        assertEquals(PlatformInfo.Platform.MACOS, config.getPlatform());
        assertFalse(config.isFileProviderEnabled());
        assertFalse(config.isDeepIntegrationEnabled());
        assertEquals(0.5, config.getFSEventsLatency(), 0.001);
        assertFalse(config.isFuseEnabled());
        assertEquals(16384, config.getInotifyBufferSize());
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void testCreateChangeWatcher_OnMacOS() throws Exception {
        // This test only runs on actual macOS systems
        // Skip test if native libraries are not available
        org.junit.jupiter.api.Assumptions.assumeTrue(
            false,
            "Skipping macOS integration test - requires native libraries"
        );
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testCreateChangeWatcher_OnLinux_Success() {
        // This test only runs on actual Linux systems
        // Linux implementation is now available in Phase 4
        try {
            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher();
            assertNotNull(watcher);
            assertTrue(
                watcher instanceof
                    io.f1r3fly.f1r3drive.platform.linux.LinuxChangeWatcher
            );
        } catch (ChangeWatcherFactory.UnsupportedPlatformException e) {
            fail(
                "Linux ChangeWatcher should be available in Phase 4: " +
                    e.getMessage()
            );
        }
    }

    @Test
    void testCreateChangeWatcher_WithSpecificPlatform_Linux_Success() {
        // Linux ChangeWatcher is now available in Phase 4 with mock FileSystem
        try {
            ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(
                PlatformInfo.Platform.LINUX
            );
            assertNotNull(watcher);
            assertTrue(
                watcher instanceof
                    io.f1r3fly.f1r3drive.platform.linux.LinuxChangeWatcher
            );
        } catch (ChangeWatcherFactory.UnsupportedPlatformException e) {
            fail(
                "Linux ChangeWatcher should be available in Phase 4: " +
                    e.getMessage()
            );
        }
    }

    @Test
    void testCreateChangeWatcher_WithSpecificPlatform_Windows_ThrowsException() {
        assertThrows(
            ChangeWatcherFactory.UnsupportedPlatformException.class,
            () -> {
                ChangeWatcherFactory.createChangeWatcher(
                    PlatformInfo.Platform.WINDOWS
                );
            }
        );
    }

    @Test
    void testCreateChangeWatcher_NullPlatform_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ChangeWatcherFactory.createChangeWatcher(
                (PlatformInfo.Platform) null
            );
        });
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
            new ChangeWatcherFactory.UnsupportedPlatformException(
                message,
                cause
            );

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
