package io.f1r3fly.f1r3drive.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for automatic platform detection and system information gathering.
 * Provides methods to detect the current operating system, architecture, and capabilities.
 */
public class PlatformDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        PlatformDetector.class
    );

    /**
     * Holder pattern for thread-safe initialization without synchronization overhead.
     * Ensures all values are initialized together and visible to all threads.
     */
    private static class PlatformInfoHolder {

        static final PlatformInfo.Platform PLATFORM =
            performPlatformDetection();
        static final String ARCHITECTURE = performArchitectureDetection();
        static final boolean IS_SUPPORTED = checkPlatformSupport();

        static {
            LOGGER.info("Detected platform: {}", PLATFORM.getDisplayName());
            LOGGER.info("Detected architecture: {}", ARCHITECTURE);
            LOGGER.info("Platform supported: {}", IS_SUPPORTED);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private PlatformDetector() {
        // Utility class
    }

    /**
     * Detects the current platform.
     *
     * @return the detected platform
     */
    public static PlatformInfo.Platform detectPlatform() {
        return PlatformInfoHolder.PLATFORM;
    }

    /**
     * Detects the current system architecture.
     *
     * @return the detected architecture
     */
    public static String detectArchitecture() {
        return PlatformInfoHolder.ARCHITECTURE;
    }

    /**
     * Checks if the current platform is supported by F1r3Drive.
     *
     * @return true if platform is supported
     */
    public static boolean isCurrentPlatformSupported() {
        return PlatformInfoHolder.IS_SUPPORTED;
    }

    /**
     * Gets detailed system information.
     *
     * @return SystemInfo object with detailed information
     */
    public static SystemInfo getSystemInfo() {
        return new SystemInfo(
            detectPlatform(),
            detectArchitecture(),
            getOSVersion(),
            getJavaVersion(),
            getJavaVMName(),
            isCurrentPlatformSupported()
        );
    }

    /**
     * Checks if the current system meets minimum requirements for F1r3Drive.
     *
     * @return true if requirements are met, false otherwise
     */
    public static boolean meetsMinimumRequirements() {
        try {
            // Check platform support
            if (!isCurrentPlatformSupported()) {
                LOGGER.warn(
                    "Current platform is not supported: {}",
                    detectPlatform()
                );
                return false;
            }

            // Check Java version (minimum JDK 17)
            if (!isJavaVersionSupported()) {
                LOGGER.warn(
                    "Java version is not supported: {}",
                    getJavaVersion()
                );
                return false;
            }

            // Check architecture
            String arch = detectArchitecture();
            if (!isSupportedArchitecture(arch)) {
                LOGGER.warn("Architecture is not supported: {}", arch);
                return false;
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("Error checking system requirements", e);
            return false;
        }
    }

    /**
     * Gets a human-readable description of the current system.
     *
     * @return system description string
     */
    public static String getSystemDescription() {
        PlatformInfo.Platform platform = detectPlatform();
        String arch = detectArchitecture();
        String osVersion = getOSVersion();
        String javaVersion = getJavaVersion();

        return String.format(
            "%s %s (%s) - Java %s",
            platform.getDisplayName(),
            osVersion,
            arch,
            javaVersion
        );
    }

    /**
     * Performs the actual platform detection logic.
     *
     * @return the detected platform
     */
    private static PlatformInfo.Platform performPlatformDetection() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        LOGGER.debug("Raw OS name: {}", osName);

        if (osName.contains("mac") || osName.contains("darwin")) {
            return PlatformInfo.Platform.MACOS;
        } else if (osName.contains("linux")) {
            return PlatformInfo.Platform.LINUX;
        } else if (osName.contains("windows") || osName.contains("win")) {
            return PlatformInfo.Platform.WINDOWS;
        } else {
            LOGGER.warn("Unknown operating system: {}", osName);
            return PlatformInfo.Platform.UNKNOWN;
        }
    }

    /**
     * Performs the actual architecture detection logic.
     *
     * @return the detected architecture
     */
    private static String performArchitectureDetection() {
        String arch = System.getProperty("os.arch", "").toLowerCase();

        LOGGER.debug("Raw architecture: {}", arch);

        // Normalize common architecture names
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        } else if (arch.contains("arm")) {
            return "arm";
        } else if (
            arch.contains("x86") ||
            arch.contains("i386") ||
            arch.contains("i686")
        ) {
            return "x86";
        }

        return arch;
    }

    /**
     * Checks if the detected platform is supported.
     *
     * @return true if platform is supported
     */
    private static boolean checkPlatformSupport() {
        PlatformInfo.Platform platform = detectPlatform();

        switch (platform) {
            case MACOS:
                return checkMacOSSupport();
            case LINUX:
                return checkLinuxSupport();
            case WINDOWS:
                return false; // Windows support is planned but not yet implemented
            case UNKNOWN:
            default:
                return false;
        }
    }

    /**
     * Checks macOS-specific support requirements.
     *
     * @return true if macOS is supported on this system
     */
    private static boolean checkMacOSSupport() {
        try {
            // Check macOS version (minimum 10.15 for File Provider Framework)
            String version = getOSVersion();
            if (isMacOSVersionSupported(version)) {
                return true;
            } else {
                LOGGER.warn(
                    "macOS version {} is not supported. Minimum required: 10.15",
                    version
                );
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Error checking macOS support", e);
            return false;
        }
    }

    /**
     * Checks Linux-specific support requirements.
     *
     * @return true if Linux is supported on this system
     */
    private static boolean checkLinuxSupport() {
        try {
            // Check for FUSE support
            if (!isLinuxFuseAvailable()) {
                LOGGER.warn("FUSE is not available on this Linux system");
                return false;
            }

            // Check kernel version for inotify support (minimum 2.6.13)
            if (!isLinuxKernelSupported()) {
                LOGGER.warn(
                    "Linux kernel version is not supported for inotify"
                );
                return false;
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("Error checking Linux support", e);
            return false;
        }
    }

    /**
     * Gets the operating system version.
     *
     * @return OS version string
     */
    private static String getOSVersion() {
        return System.getProperty("os.version", "unknown");
    }

    /**
     * Gets the Java version.
     *
     * @return Java version string
     */
    private static String getJavaVersion() {
        return System.getProperty("java.version", "unknown");
    }

    /**
     * Gets the Java VM name.
     *
     * @return Java VM name
     */
    private static String getJavaVMName() {
        return System.getProperty("java.vm.name", "unknown");
    }

    /**
     * Checks if the Java version is supported.
     *
     * @return true if Java version is supported
     */
    private static boolean isJavaVersionSupported() {
        try {
            String javaVersion = getJavaVersion();

            // Parse major version number
            String[] parts = javaVersion.split("\\.");
            int majorVersion;

            if (parts[0].equals("1")) {
                // Old version format (e.g., 1.8.0_xxx)
                majorVersion = Integer.parseInt(parts[1]);
            } else {
                // New version format (e.g., 17.0.1)
                majorVersion = Integer.parseInt(parts[0]);
            }

            return majorVersion >= 17;
        } catch (Exception e) {
            LOGGER.error("Error parsing Java version", e);
            return false;
        }
    }

    /**
     * Checks if the architecture is supported.
     *
     * @param architecture the architecture to check
     * @return true if architecture is supported
     */
    private static boolean isSupportedArchitecture(String architecture) {
        switch (architecture) {
            case "x86_64":
            case "arm64":
                return true;
            case "x86":
            case "arm":
                // 32-bit architectures have limited support
                LOGGER.warn(
                    "32-bit architecture detected: {}. Support may be limited.",
                    architecture
                );
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if macOS version is supported.
     *
     * @param version macOS version string
     * @return true if version is supported
     */
    private static boolean isMacOSVersionSupported(String version) {
        try {
            // Try to get detailed version via sw_vers
            ProcessBuilder pb = new ProcessBuilder(
                "sw_vers",
                "-productVersion"
            );
            Process process = pb.start();
            process.waitFor();

            if (process.exitValue() == 0) {
                byte[] output = process.getInputStream().readAllBytes();
                String detailedVersion = new String(output).trim();
                return parseMacOSVersion(detailedVersion) >= 10.15;
            }
        } catch (Exception e) {
            LOGGER.debug(
                "Could not get detailed macOS version, falling back to system property"
            );
        }

        // Fallback to system property parsing
        return parseMacOSVersion(version) >= 10.15;
    }

    /**
     * Parses macOS version string to comparable double.
     *
     * @param version version string
     * @return version as double for comparison
     */
    private static double parseMacOSVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                double major = Double.parseDouble(parts[0]);
                double minor = Double.parseDouble(parts[1]);
                return major + (minor / 100.0);
            }
        } catch (Exception e) {
            LOGGER.debug("Error parsing macOS version: {}", version);
        }
        return 0.0;
    }

    /**
     * Checks if FUSE is available on Linux.
     *
     * @return true if FUSE is available
     */
    private static boolean isLinuxFuseAvailable() {
        try {
            // Check if /dev/fuse exists
            java.io.File fuseDevice = new java.io.File("/dev/fuse");
            if (fuseDevice.exists()) {
                return true;
            }

            // Check if fusermount is available
            ProcessBuilder pb = new ProcessBuilder("which", "fusermount");
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.debug("Error checking FUSE availability", e);
            return false;
        }
    }

    /**
     * Checks if Linux kernel supports required features.
     *
     * @return true if kernel is supported
     */
    private static boolean isLinuxKernelSupported() {
        try {
            String version = System.getProperty("os.version", "");
            String[] parts = version.split("\\.");

            if (parts.length >= 3) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = Integer.parseInt(parts[2].split("-")[0]); // Handle versions like "5.4.0-74-generic"

                // Check for minimum kernel 2.6.13 (inotify support)
                if (major > 2) return true;
                if (major == 2 && minor > 6) return true;
                if (major == 2 && minor == 6 && patch >= 13) return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Error parsing Linux kernel version", e);
        }

        return false;
    }

    /**
     * System information container class.
     */
    public static class SystemInfo {

        private final PlatformInfo.Platform platform;
        private final String architecture;
        private final String osVersion;
        private final String javaVersion;
        private final String javaVMName;
        private final boolean isSupported;

        private SystemInfo(
            PlatformInfo.Platform platform,
            String architecture,
            String osVersion,
            String javaVersion,
            String javaVMName,
            boolean isSupported
        ) {
            this.platform = platform;
            this.architecture = architecture;
            this.osVersion = osVersion;
            this.javaVersion = javaVersion;
            this.javaVMName = javaVMName;
            this.isSupported = isSupported;
        }

        public PlatformInfo.Platform getPlatform() {
            return platform;
        }

        public String getArchitecture() {
            return architecture;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public String getJavaVMName() {
            return javaVMName;
        }

        public boolean isSupported() {
            return isSupported;
        }

        @Override
        public String toString() {
            return String.format(
                "SystemInfo{platform=%s, arch=%s, osVersion='%s', javaVersion='%s', javaVM='%s', supported=%s}",
                platform,
                architecture,
                osVersion,
                javaVersion,
                javaVMName,
                isSupported
            );
        }
    }
}
