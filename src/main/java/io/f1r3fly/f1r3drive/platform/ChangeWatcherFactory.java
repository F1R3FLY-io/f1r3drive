package io.f1r3fly.f1r3drive.platform;

import io.f1r3fly.f1r3drive.platform.macos.MacOSChangeWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating platform-specific ChangeWatcher implementations.
 * Uses automatic platform detection to instantiate the appropriate watcher
 * for the current operating system.
 */
public class ChangeWatcherFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeWatcherFactory.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private ChangeWatcherFactory() {
        // Factory class
    }

    /**
     * Creates a ChangeWatcher instance appropriate for the current platform.
     *
     * @return platform-specific ChangeWatcher implementation
     * @throws UnsupportedPlatformException if the current platform is not supported
     */
    public static ChangeWatcher createChangeWatcher() throws UnsupportedPlatformException {
        PlatformInfo.Platform platform = PlatformDetector.detectPlatform();

        LOGGER.info("Creating ChangeWatcher for platform: {}", platform.getDisplayName());

        switch (platform) {
            case MACOS:
                return createMacOSChangeWatcher();

            case LINUX:
                return createLinuxChangeWatcher();

            case WINDOWS:
                throw new UnsupportedPlatformException("Windows support is not yet implemented");

            case UNKNOWN:
            default:
                throw new UnsupportedPlatformException("Unsupported platform: " + platform.getDisplayName());
        }
    }

    /**
     * Creates a ChangeWatcher with specific platform override.
     * This method is primarily used for testing or when automatic detection is not desired.
     *
     * @param platform the platform to create a watcher for
     * @return platform-specific ChangeWatcher implementation
     * @throws UnsupportedPlatformException if the specified platform is not supported
     */
    public static ChangeWatcher createChangeWatcher(PlatformInfo.Platform platform) throws UnsupportedPlatformException {
        if (platform == null) {
            throw new IllegalArgumentException("Platform cannot be null");
        }

        LOGGER.info("Creating ChangeWatcher for specified platform: {}", platform.getDisplayName());

        switch (platform) {
            case MACOS:
                return createMacOSChangeWatcher();

            case LINUX:
                return createLinuxChangeWatcher();

            case WINDOWS:
                throw new UnsupportedPlatformException("Windows support is not yet implemented");

            case UNKNOWN:
            default:
                throw new UnsupportedPlatformException("Unsupported platform: " + platform.getDisplayName());
        }
    }

    /**
     * Creates a ChangeWatcher with configuration options.
     *
     * @param config configuration for the change watcher
     * @return configured platform-specific ChangeWatcher implementation
     * @throws UnsupportedPlatformException if the current platform is not supported
     */
    public static ChangeWatcher createChangeWatcher(ChangeWatcherConfig config) throws UnsupportedPlatformException {
        if (config == null) {
            return createChangeWatcher();
        }

        PlatformInfo.Platform platform = config.getPlatform() != null ?
            config.getPlatform() : PlatformDetector.detectPlatform();

        LOGGER.info("Creating configured ChangeWatcher for platform: {}", platform.getDisplayName());

        switch (platform) {
            case MACOS:
                return createMacOSChangeWatcher(config);

            case LINUX:
                return createLinuxChangeWatcher(config);

            case WINDOWS:
                throw new UnsupportedPlatformException("Windows support is not yet implemented");

            case UNKNOWN:
            default:
                throw new UnsupportedPlatformException("Unsupported platform: " + platform.getDisplayName());
        }
    }

    /**
     * Checks if a ChangeWatcher can be created for the current platform.
     *
     * @return true if the current platform is supported, false otherwise
     */
    public static boolean isCurrentPlatformSupported() {
        try {
            return PlatformDetector.isCurrentPlatformSupported() &&
                   PlatformDetector.meetsMinimumRequirements();
        } catch (Exception e) {
            LOGGER.error("Error checking platform support", e);
            return false;
        }
    }

    /**
     * Checks if a specific platform is supported.
     *
     * @param platform the platform to check
     * @return true if the platform is supported, false otherwise
     */
    public static boolean isPlatformSupported(PlatformInfo.Platform platform) {
        switch (platform) {
            case MACOS:
            case LINUX:
                return true;
            case WINDOWS:
                return false; // Not yet implemented
            case UNKNOWN:
            default:
                return false;
        }
    }

    /**
     * Gets information about the current platform and its support status.
     *
     * @return PlatformSupportInfo with detailed information
     */
    public static PlatformSupportInfo getPlatformSupportInfo() {
        PlatformDetector.SystemInfo systemInfo = PlatformDetector.getSystemInfo();
        boolean canCreateWatcher = isCurrentPlatformSupported();

        String[] requiredFeatures = getRequiredFeatures(systemInfo.getPlatform());
        String[] missingFeatures = getMissingFeatures(systemInfo.getPlatform());

        return new PlatformSupportInfo(
            systemInfo.getPlatform(),
            systemInfo.getArchitecture(),
            systemInfo.getOsVersion(),
            systemInfo.getJavaVersion(),
            canCreateWatcher,
            systemInfo.isSupported(),
            requiredFeatures,
            missingFeatures
        );
    }

    /**
     * Creates a macOS-specific ChangeWatcher.
     *
     * @return MacOSChangeWatcher instance
     * @throws UnsupportedPlatformException if macOS requirements are not met
     */
    private static ChangeWatcher createMacOSChangeWatcher() throws UnsupportedPlatformException {
        try {
            validateMacOSRequirements();
            MacOSChangeWatcher watcher = new MacOSChangeWatcher();
            LOGGER.debug("Created MacOSChangeWatcher successfully");
            return watcher;
        } catch (Exception e) {
            throw new UnsupportedPlatformException("Failed to create macOS ChangeWatcher: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a configured macOS-specific ChangeWatcher.
     *
     * @param config configuration options
     * @return configured MacOSChangeWatcher instance
     * @throws UnsupportedPlatformException if macOS requirements are not met
     */
    private static ChangeWatcher createMacOSChangeWatcher(ChangeWatcherConfig config) throws UnsupportedPlatformException {
        try {
            validateMacOSRequirements();

            MacOSChangeWatcher watcher = new MacOSChangeWatcher(
                config.isFileProviderEnabled(),
                config.isDeepIntegrationEnabled()
            );

            if (config.getFSEventsLatency() != null) {
                watcher.setFSEventsLatency(config.getFSEventsLatency());
            }

            LOGGER.debug("Created configured MacOSChangeWatcher successfully");
            return watcher;
        } catch (Exception e) {
            throw new UnsupportedPlatformException("Failed to create configured macOS ChangeWatcher: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Linux-specific ChangeWatcher.
     * Note: This is a placeholder for the Linux implementation that will be created in Phase 4.
     *
     * @return LinuxChangeWatcher instance
     * @throws UnsupportedPlatformException if Linux requirements are not met
     */
    private static ChangeWatcher createLinuxChangeWatcher() throws UnsupportedPlatformException {
        try {
            validateLinuxRequirements();

            // TODO: Implement LinuxChangeWatcher in Phase 4
            // For now, throw an exception indicating it's not yet implemented
            throw new UnsupportedPlatformException("Linux ChangeWatcher implementation is not yet available. Will be implemented in Phase 4.");

        } catch (UnsupportedPlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedPlatformException("Failed to create Linux ChangeWatcher: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a configured Linux-specific ChangeWatcher.
     *
     * @param config configuration options
     * @return configured LinuxChangeWatcher instance
     * @throws UnsupportedPlatformException if Linux requirements are not met
     */
    private static ChangeWatcher createLinuxChangeWatcher(ChangeWatcherConfig config) throws UnsupportedPlatformException {
        // TODO: Implement configured Linux ChangeWatcher in Phase 4
        throw new UnsupportedPlatformException("Linux ChangeWatcher implementation is not yet available. Will be implemented in Phase 4.");
    }

    /**
     * Validates macOS-specific requirements.
     *
     * @throws Exception if requirements are not met
     */
    private static void validateMacOSRequirements() throws Exception {
        if (PlatformDetector.detectPlatform() != PlatformInfo.Platform.MACOS) {
            throw new Exception("Current platform is not macOS");
        }

        if (!PlatformDetector.meetsMinimumRequirements()) {
            throw new Exception("System does not meet minimum requirements for macOS integration");
        }

        // Additional macOS-specific validation can be added here
        // For example, checking for specific macOS version, File Provider Framework availability, etc.
    }

    /**
     * Validates Linux-specific requirements.
     *
     * @throws Exception if requirements are not met
     */
    private static void validateLinuxRequirements() throws Exception {
        if (PlatformDetector.detectPlatform() != PlatformInfo.Platform.LINUX) {
            throw new Exception("Current platform is not Linux");
        }

        if (!PlatformDetector.meetsMinimumRequirements()) {
            throw new Exception("System does not meet minimum requirements for Linux integration");
        }

        // Additional Linux-specific validation can be added here
        // For example, checking for FUSE availability, inotify support, etc.
    }

    /**
     * Gets the list of required features for a platform.
     *
     * @param platform the platform
     * @return array of required feature names
     */
    private static String[] getRequiredFeatures(PlatformInfo.Platform platform) {
        switch (platform) {
            case MACOS:
                return new String[]{
                    "macOS 10.15+",
                    "JDK 17+",
                    "FSEvents API",
                    "File Provider Framework (optional)",
                    "Native library support"
                };
            case LINUX:
                return new String[]{
                    "Linux kernel 2.6.13+",
                    "JDK 17+",
                    "FUSE support",
                    "inotify support",
                    "JNR-FFI libraries"
                };
            case WINDOWS:
                return new String[]{
                    "Windows 10 1803+",
                    "JDK 17+",
                    "WinFsp",
                    "ReadDirectoryChangesW API",
                    "JNA support"
                };
            default:
                return new String[0];
        }
    }

    /**
     * Gets the list of missing features for a platform.
     *
     * @param platform the platform
     * @return array of missing feature names
     */
    private static String[] getMissingFeatures(PlatformInfo.Platform platform) {
        // TODO: Implement actual feature detection
        // For now, return empty array - actual implementation would check
        // which required features are missing on the current system
        return new String[0];
    }

    /**
     * Configuration class for ChangeWatcher creation.
     */
    public static class ChangeWatcherConfig {
        private PlatformInfo.Platform platform;
        private Boolean fileProviderEnabled;
        private Boolean deepIntegrationEnabled;
        private Double fsEventsLatency;
        private Boolean fuseEnabled;
        private Integer inotifyBufferSize;

        public ChangeWatcherConfig() {}

        public PlatformInfo.Platform getPlatform() {
            return platform;
        }

        public ChangeWatcherConfig setPlatform(PlatformInfo.Platform platform) {
            this.platform = platform;
            return this;
        }

        public boolean isFileProviderEnabled() {
            return fileProviderEnabled != null ? fileProviderEnabled : true;
        }

        public ChangeWatcherConfig setFileProviderEnabled(boolean enabled) {
            this.fileProviderEnabled = enabled;
            return this;
        }

        public boolean isDeepIntegrationEnabled() {
            return deepIntegrationEnabled != null ? deepIntegrationEnabled : true;
        }

        public ChangeWatcherConfig setDeepIntegrationEnabled(boolean enabled) {
            this.deepIntegrationEnabled = enabled;
            return this;
        }

        public Double getFSEventsLatency() {
            return fsEventsLatency;
        }

        public ChangeWatcherConfig setFSEventsLatency(double latency) {
            this.fsEventsLatency = latency;
            return this;
        }

        public boolean isFuseEnabled() {
            return fuseEnabled != null ? fuseEnabled : true;
        }

        public ChangeWatcherConfig setFuseEnabled(boolean enabled) {
            this.fuseEnabled = enabled;
            return this;
        }

        public Integer getInotifyBufferSize() {
            return inotifyBufferSize;
        }

        public ChangeWatcherConfig setInotifyBufferSize(int bufferSize) {
            this.inotifyBufferSize = bufferSize;
            return this;
        }
    }

    /**
     * Information about platform support status.
     */
    public static class PlatformSupportInfo {
        private final PlatformInfo.Platform platform;
        private final String architecture;
        private final String osVersion;
        private final String javaVersion;
        private final boolean canCreateWatcher;
        private final boolean isSupported;
        private final String[] requiredFeatures;
        private final String[] missingFeatures;

        private PlatformSupportInfo(PlatformInfo.Platform platform, String architecture, String osVersion,
                                   String javaVersion, boolean canCreateWatcher, boolean isSupported,
                                   String[] requiredFeatures, String[] missingFeatures) {
            this.platform = platform;
            this.architecture = architecture;
            this.osVersion = osVersion;
            this.javaVersion = javaVersion;
            this.canCreateWatcher = canCreateWatcher;
            this.isSupported = isSupported;
            this.requiredFeatures = requiredFeatures;
            this.missingFeatures = missingFeatures;
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

        public boolean canCreateWatcher() {
            return canCreateWatcher;
        }

        public boolean isSupported() {
            return isSupported;
        }

        public String[] getRequiredFeatures() {
            return requiredFeatures.clone();
        }

        public String[] getMissingFeatures() {
            return missingFeatures.clone();
        }

        @Override
        public String toString() {
            return String.format("PlatformSupportInfo{platform=%s, arch=%s, osVersion='%s', javaVersion='%s', " +
                               "canCreateWatcher=%s, isSupported=%s, requiredFeatures=%d, missingFeatures=%d}",
                platform, architecture, osVersion, javaVersion, canCreateWatcher, isSupported,
                requiredFeatures.length, missingFeatures.length);
        }
    }

    /**
     * Exception thrown when a platform is not supported.
     */
    public static class UnsupportedPlatformException extends Exception {
        public UnsupportedPlatformException(String message) {
            super(message);
        }

        public UnsupportedPlatformException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
