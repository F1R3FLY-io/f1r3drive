package io.f1r3fly.f1r3drive.platform;

/**
 * Abstract base class for platform-specific information.
 * Provides common platform detection and capability information.
 */
public abstract class PlatformInfo {

    /**
     * Enumeration of supported platforms.
     */
    public enum Platform {
        MACOS("macOS", "darwin"),
        LINUX("Linux", "linux"),
        WINDOWS("Windows", "windows"),
        UNKNOWN("Unknown", "unknown");

        private final String displayName;
        private final String systemName;

        Platform(String displayName, String systemName) {
            this.displayName = displayName;
            this.systemName = systemName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSystemName() {
            return systemName;
        }
    }

    /**
     * Enumeration of platform capabilities.
     */
    public enum Capability {
        NATIVE_FILE_MONITORING,      // FSEvents, inotify, ReadDirectoryChangesW
        VIRTUAL_FILESYSTEM,          // File Provider, FUSE, WinFsp
        LAZY_LOADING,               // Placeholder files
        BACKGROUND_SYNC,            // Background synchronization
        SYSTEM_INTEGRATION,         // Deep OS integration
        EXTENDED_ATTRIBUTES,        // xattr support
        SYMBOLIC_LINKS,             // Symlink support
        CASE_SENSITIVE_PATHS,       // Case-sensitive filesystem
        UNICODE_NORMALIZATION       // Unicode filename normalization
    }

    /**
     * Gets the current platform.
     *
     * @return the detected platform
     */
    public abstract Platform getPlatform();

    /**
     * Gets the platform version (e.g., macOS version, kernel version).
     *
     * @return platform version string
     */
    public abstract String getPlatformVersion();

    /**
     * Gets the architecture (e.g., x86_64, arm64).
     *
     * @return architecture string
     */
    public abstract String getArchitecture();

    /**
     * Checks if the platform supports a specific capability.
     *
     * @param capability the capability to check
     * @return true if supported, false otherwise
     */
    public abstract boolean hasCapability(Capability capability);

    /**
     * Gets the recommended thread count for file monitoring operations.
     *
     * @return recommended thread count
     */
    public abstract int getRecommendedMonitoringThreads();

    /**
     * Gets the maximum recommended cache size in bytes.
     *
     * @return maximum cache size
     */
    public abstract long getMaxRecommendedCacheSize();

    /**
     * Gets platform-specific configuration properties.
     *
     * @return configuration properties as key-value pairs
     */
    public abstract java.util.Map<String, String> getConfigurationProperties();

    /**
     * Gets the native library name for this platform (if applicable).
     *
     * @return native library name or null if not applicable
     */
    public abstract String getNativeLibraryName();

    /**
     * Checks if native libraries are required for this platform.
     *
     * @return true if native libraries are required, false otherwise
     */
    public abstract boolean requiresNativeLibraries();

    /**
     * Gets platform-specific mount options for FUSE-like filesystems.
     *
     * @return array of mount options
     */
    public abstract String[] getMountOptions();

    /**
     * Gets the default file permissions mask for new files.
     *
     * @return permissions mask (e.g., 0644)
     */
    public abstract int getDefaultFilePermissions();

    /**
     * Gets the default directory permissions mask for new directories.
     *
     * @return permissions mask (e.g., 0755)
     */
    public abstract int getDefaultDirectoryPermissions();

    /**
     * Detects the current platform automatically.
     *
     * @return detected platform
     */
    public static Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            return Platform.MACOS;
        } else if (osName.contains("linux")) {
            return Platform.LINUX;
        } else if (osName.contains("windows")) {
            return Platform.WINDOWS;
        } else {
            return Platform.UNKNOWN;
        }
    }

    /**
     * Gets the current system architecture.
     *
     * @return architecture string
     */
    public static String detectArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase();

        // Normalize common architecture names
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        } else if (arch.contains("arm")) {
            return "arm";
        } else if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }

        return arch;
    }

    /**
     * Checks if the current JVM supports the required features for this platform.
     *
     * @return true if JVM is compatible, false otherwise
     */
    public boolean isJVMCompatible() {
        // Check minimum Java version (17+)
        String javaVersion = System.getProperty("java.version");
        try {
            int majorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);
            return majorVersion >= 17;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s)",
            getPlatform().getDisplayName(),
            getPlatformVersion(),
            getArchitecture());
    }
}
