package io.f1r3fly.f1r3drive.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple build system verification test.
 * Tests that platform-specific resources are correctly included in builds.
 */
public class BuildSystemTest {

    @Test
    public void testPlatformPropertiesAvailable() throws Exception {
        // Test that platform.properties is available in classpath
        InputStream propertiesStream = getClass().getClassLoader().getResourceAsStream("platform.properties");
        assertNotNull(propertiesStream, "platform.properties should be available in classpath");

        Properties platformProps = new Properties();
        platformProps.load(propertiesStream);

        // Verify basic properties exist
        assertTrue(platformProps.containsKey("platform.name"), "Should contain platform.name");
        assertTrue(platformProps.containsKey("platform.type"), "Should contain platform.type");

        String platformName = platformProps.getProperty("platform.name");
        assertNotNull(platformName, "Platform name should not be null");
        assertTrue(platformName.equals("macOS") || platformName.equals("Linux"),
                  "Platform name should be either macOS or Linux, but was: " + platformName);

        propertiesStream.close();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testMacOSPropertiesContent() throws Exception {
        InputStream propertiesStream = getClass().getClassLoader().getResourceAsStream("platform.properties");
        assertNotNull(propertiesStream, "platform.properties should be available");

        Properties props = new Properties();
        props.load(propertiesStream);

        assertEquals("macOS", props.getProperty("platform.name"));
        assertEquals("darwin", props.getProperty("platform.type"));
        assertEquals("10.15", props.getProperty("platform.min.version"));
        assertEquals("libf1r3drive-fsevents.dylib", props.getProperty("native.library.name"));
        assertEquals("true", props.getProperty("fileprovider.enabled"));
        assertEquals("true", props.getProperty("fsevents.enabled"));

        propertiesStream.close();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testLinuxPropertiesContent() throws Exception {
        InputStream propertiesStream = getClass().getClassLoader().getResourceAsStream("platform.properties");
        assertNotNull(propertiesStream, "platform.properties should be available");

        Properties props = new Properties();
        props.load(propertiesStream);

        assertEquals("Linux", props.getProperty("platform.name"));
        assertEquals("linux", props.getProperty("platform.type"));
        assertEquals("2.6.13", props.getProperty("platform.min.kernel.version"));
        assertEquals("2.6", props.getProperty("platform.min.fuse.version"));
        assertEquals("true", props.getProperty("fuse.enabled"));
        assertEquals("true", props.getProperty("inotify.enabled"));
        assertEquals("/dev/fuse", props.getProperty("fuse.device.path"));

        propertiesStream.close();
    }

    @Test
    public void testJavaVersionCompatibility() {
        // Verify we're running on Java 17+ as required
        String javaVersion = System.getProperty("java.version");
        assertNotNull(javaVersion, "Java version should be available");

        // Extract major version
        String majorVersion = javaVersion.split("\\.")[0];
        int majorVersionInt = Integer.parseInt(majorVersion);

        assertTrue(majorVersionInt >= 17,
                  "Should be running on Java 17 or higher, but found: " + javaVersion);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testFUSEDependenciesAvailable() {
        // Test that FUSE-related classes are available on Linux builds
        try {
            Class.forName("ru.serce.jnrfuse.FuseStubFS");
            Class.forName("ru.serce.jnrfuse.struct.FileStat");
            Class.forName("ru.serce.jnrfuse.struct.FuseFileInfo");
            // If we get here, FUSE dependencies are available
            assertTrue(true, "FUSE dependencies should be available on Linux");
        } catch (ClassNotFoundException e) {
            fail("FUSE dependencies should be available on Linux build: " + e.getMessage());
        }
    }

    @Test
    public void testPlatformSpecificClassesAvailable() {
        // Test that platform-specific classes are in the classpath
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            // On macOS, verify macOS classes are available
            try {
                Class.forName("io.f1r3fly.f1r3drive.platform.macos.MacOSPlatformInfo");
                assertTrue(true, "macOS platform classes should be available");
            } catch (ClassNotFoundException e) {
                fail("macOS platform classes should be available: " + e.getMessage());
            }
        } else if (osName.contains("linux")) {
            // On Linux, verify Linux classes are available
            try {
                Class.forName("io.f1r3fly.f1r3drive.platform.linux.LinuxPlatformInfo");
                assertTrue(true, "Linux platform classes should be available");
            } catch (ClassNotFoundException e) {
                fail("Linux platform classes should be available: " + e.getMessage());
            }
        }
    }
}
