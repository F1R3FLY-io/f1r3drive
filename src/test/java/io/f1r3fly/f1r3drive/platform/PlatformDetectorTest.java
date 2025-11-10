package io.f1r3fly.f1r3drive.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlatformDetector.
 * Tests OS detection, architecture detection, and system property parsing.
 */
class PlatformDetectorTest {

    @BeforeEach
    void setUp() {
        // Reset any static state if needed
    }

    @Test
    void testDetectPlatform_macOS() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.name"))
                    .thenReturn("Mac OS X");

            PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

            assertEquals(PlatformInfo.Platform.MACOS, platform);
        }
    }

    @Test
    void testDetectPlatform_macOS_VariousNames() {
        String[] macOSNames = {
                "Mac OS X",
                "macOS",
                "Darwin",
                "Mac OS"
        };

        for (String osName : macOSNames) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.name"))
                        .thenReturn(osName);

                PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

                assertEquals(PlatformInfo.Platform.MACOS, platform,
                    "Should detect macOS for OS name: " + osName);
            }
        }
    }

    @Test
    void testDetectPlatform_Linux() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.name"))
                    .thenReturn("Linux");

            PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

            assertEquals(PlatformInfo.Platform.LINUX, platform);
        }
    }

    @Test
    void testDetectPlatform_Linux_VariousNames() {
        String[] linuxNames = {
                "Linux",
                "linux",
                "LINUX",
                "GNU/Linux"
        };

        for (String osName : linuxNames) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.name"))
                        .thenReturn(osName);

                PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

                assertEquals(PlatformInfo.Platform.LINUX, platform,
                    "Should detect Linux for OS name: " + osName);
            }
        }
    }

    @Test
    void testDetectPlatform_Windows() {
        String[] windowsNames = {
                "Windows 10",
                "Windows 11",
                "Windows Server 2019",
                "windows",
                "WINDOWS"
        };

        for (String osName : windowsNames) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.name"))
                        .thenReturn(osName);

                PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

                assertEquals(PlatformInfo.Platform.WINDOWS, platform,
                    "Should detect Windows for OS name: " + osName);
            }
        }
    }

    @Test
    void testDetectPlatform_Unknown() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.name"))
                    .thenReturn("FreeBSD");

            PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

            assertEquals(PlatformInfo.Platform.UNKNOWN, platform);
        }
    }

    @Test
    void testDetectPlatform_Null() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.name"))
                    .thenReturn(null);

            PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

            assertEquals(PlatformInfo.Platform.UNKNOWN, platform);
        }
    }

    @Test
    void testGetArchitecture() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.arch"))
                    .thenReturn("x86_64");

            String architecture = PlatformDetector.getArchitecture();

            assertEquals("x86_64", architecture);
        }
    }

    @Test
    void testGetArchitecture_Various() {
        String[] architectures = {
                "x86_64",
                "amd64",
                "aarch64",
                "arm64",
                "i386",
                "x86"
        };

        for (String arch : architectures) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.arch"))
                        .thenReturn(arch);

                String architecture = PlatformDetector.getArchitecture();

                assertEquals(arch, architecture, "Should return architecture: " + arch);
            }
        }
    }

    @Test
    void testGetArchitecture_Null() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.arch"))
                    .thenReturn(null);

            String architecture = PlatformDetector.getArchitecture();

            assertEquals("unknown", architecture);
        }
    }

    @Test
    void testGetOSVersion() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.version"))
                    .thenReturn("12.0.1");

            String osVersion = PlatformDetector.getOSVersion();

            assertEquals("12.0.1", osVersion);
        }
    }

    @Test
    void testGetOSVersion_Null() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.version"))
                    .thenReturn(null);

            String osVersion = PlatformDetector.getOSVersion();

            assertEquals("unknown", osVersion);
        }
    }

    @Test
    void testGetJavaVersion() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn("17.0.1");

            String javaVersion = PlatformDetector.getJavaVersion();

            assertEquals("17.0.1", javaVersion);
        }
    }

    @Test
    void testGetJavaVersion_Null() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn(null);

            String javaVersion = PlatformDetector.getJavaVersion();

            assertEquals("unknown", javaVersion);
        }
    }

    @Test
    void testIs64Bit_True() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("os.arch"))
                    .thenReturn("x86_64");

            boolean is64Bit = PlatformDetector.is64Bit();

            assertTrue(is64Bit);
        }
    }

    @Test
    void testIs64Bit_Various64BitArchs() {
        String[] arch64Bit = {"x86_64", "amd64", "aarch64", "arm64"};

        for (String arch : arch64Bit) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.arch"))
                        .thenReturn(arch);

                boolean is64Bit = PlatformDetector.is64Bit();

                assertTrue(is64Bit, "Should detect 64-bit for architecture: " + arch);
            }
        }
    }

    @Test
    void testIs64Bit_False() {
        String[] arch32Bit = {"i386", "x86", "arm", "sparc"};

        for (String arch : arch32Bit) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("os.arch"))
                        .thenReturn(arch);

                boolean is64Bit = PlatformDetector.is64Bit();

                assertFalse(is64Bit, "Should detect 32-bit for architecture: " + arch);
            }
        }
    }

    @Test
    void testIsMinimumJavaVersion_True() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn("17.0.1");

            boolean isMinimum = PlatformDetector.isMinimumJavaVersion(17);

            assertTrue(isMinimum);
        }
    }

    @Test
    void testIsMinimumJavaVersion_False() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn("11.0.1");

            boolean isMinimum = PlatformDetector.isMinimumJavaVersion(17);

            assertFalse(isMinimum);
        }
    }

    @Test
    void testIsMinimumJavaVersion_Edge() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn("17.0.0");

            boolean isMinimum = PlatformDetector.isMinimumJavaVersion(17);

            assertTrue(isMinimum);
        }
    }

    @Test
    void testGetJavaMajorVersion() {
        String[][] versionTests = {
                {"17.0.1", "17"},
                {"11.0.16", "11"},
                {"1.8.0_301", "8"},
                {"21.0.0", "21"},
                {"8.0.0", "8"}
        };

        for (String[] test : versionTests) {
            try (MockedStatic<System> systemMock = mockStatic(System.class)) {
                systemMock.when(() -> System.getProperty("java.version"))
                        .thenReturn(test[0]);

                int majorVersion = PlatformDetector.getJavaMajorVersion();

                assertEquals(Integer.parseInt(test[1]), majorVersion,
                    "Should parse major version correctly for: " + test[0]);
            }
        }
    }

    @Test
    void testGetJavaMajorVersion_InvalidFormat() {
        try (MockedStatic<System> systemMock = mockStatic(System.class)) {
            systemMock.when(() -> System.getProperty("java.version"))
                    .thenReturn("invalid");

            int majorVersion = PlatformDetector.getJavaMajorVersion();

            assertEquals(-1, majorVersion);
        }
    }

    // Integration tests for real system properties (only run on actual platforms)
    @Test
    @EnabledOnOs(OS.MAC)
    void testDetectPlatform_RealMacOS() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.MACOS, platform);

        String architecture = PlatformDetector.getArchitecture();
        assertNotNull(architecture);
        assertNotEquals("unknown", architecture);

        String osVersion = PlatformDetector.getOSVersion();
        assertNotNull(osVersion);
        assertNotEquals("unknown", osVersion);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testDetectPlatform_RealLinux() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.LINUX, platform);

        String architecture = PlatformDetector.getArchitecture();
        assertNotNull(architecture);
        assertNotEquals("unknown", architecture);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testDetectPlatform_RealWindows() {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();
        assertEquals(PlatformInfo.Platform.WINDOWS, platform);

        String architecture = PlatformDetector.getArchitecture();
        assertNotNull(architecture);
        assertNotEquals("unknown", architecture);
    }

    @Test
    void testRealJavaVersion() {
        String javaVersion = PlatformDetector.getJavaVersion();
        assertNotNull(javaVersion);
        assertNotEquals("unknown", javaVersion);

        int majorVersion = PlatformDetector.getJavaMajorVersion();
        assertTrue(majorVersion >= 11, "Should be running on Java 11+");

        boolean is64Bit = PlatformDetector.is64Bit();
        // Most modern systems should be 64-bit, but we don't enforce this
        assertNotNull(is64Bit);
    }
}
