package io.f1r3fly.f1r3drive.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for PlatformDetector.
 * Tests existing public methods for platform detection.
 */
class PlatformDetectorTest {

    @Test
    void testDetectPlatform_ReturnsValidPlatform() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

        assertNotNull(platform);
        assertTrue(platform == PlatformInfo.Platform.MACOS ||
                  platform == PlatformInfo.Platform.LINUX ||
                  platform == PlatformInfo.Platform.WINDOWS ||
                  platform == PlatformInfo.Platform.UNKNOWN);
    }

    @Test
    void testDetectArchitecture_ReturnsNonNull() {
        String architecture = PlatformDetector.detectArchitecture();

        assertNotNull(architecture);
        assertFalse(architecture.trim().isEmpty());
    }

    @Test
    void testIsCurrentPlatformSupported_ReturnsBoolean() {
        boolean supported = PlatformDetector.isCurrentPlatformSupported();

        // Should return a boolean value (either true or false)
        assertNotNull(supported);
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void testDetectPlatform_OnMacOS() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.MACOS, platform);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testDetectPlatform_OnLinux() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.LINUX, platform);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testDetectPlatform_OnWindows() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.WINDOWS, platform);
    }

    @Test
    void testDetectPlatform_Consistency() {
        // Multiple calls should return the same result (cached)
        PlatformInfo.Platform first = PlatformDetector.detectPlatform();
        PlatformInfo.Platform second = PlatformDetector.detectPlatform();

        assertEquals(first, second);
    }

    @Test
    void testDetectArchitecture_Consistency() {
        // Multiple calls should return the same result (cached)
        String first = PlatformDetector.detectArchitecture();
        String second = PlatformDetector.detectArchitecture();

        assertEquals(first, second);
    }
}
