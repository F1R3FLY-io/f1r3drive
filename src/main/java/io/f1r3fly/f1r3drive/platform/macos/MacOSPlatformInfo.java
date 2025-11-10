package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.platform.PlatformInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

/**
 * macOS-specific platform information implementation.
 * Provides details about macOS capabilities, configuration, and system requirements.
 */
public class MacOSPlatformInfo extends PlatformInfo {

    private static final String NATIVE_LIBRARY_NAME = "f1r3drive-fsevents";
    private static final Set<Capability> SUPPORTED_CAPABILITIES = EnumSet.of(
        Capability.NATIVE_FILE_MONITORING,      // FSEvents API
        Capability.VIRTUAL_FILESYSTEM,          // File Provider Framework
        Capability.LAZY_LOADING,               // Placeholder files support
        Capability.BACKGROUND_SYNC,            // Background synchronization
        Capability.SYSTEM_INTEGRATION,         // Deep macOS integration
        Capability.EXTENDED_ATTRIBUTES,        // xattr support
        Capability.SYMBOLIC_LINKS,             // Symlink support
        Capability.UNICODE_NORMALIZATION       // HFS+ Unicode normalization
    );

    private final String platformVersion;
    private final String architecture;
    private final Map<String, String> configurationProperties;

    /**
     * Creates a new MacOSPlatformInfo instance.
     */
    public MacOSPlatformInfo() {
        this.platformVersion = detectMacOSVersion();
        this.architecture = detectArchitecture();
        this.configurationProperties = createConfigurationProperties();
    }

    @Override
    public Platform getPlatform() {
        return Platform.MACOS;
    }

    @Override
    public String getPlatformVersion() {
        return platformVersion;
    }

    @Override
    public String getArchitecture() {
        return architecture;
    }

    @Override
    public boolean hasCapability(Capability capability) {
        return SUPPORTED_CAPABILITIES.contains(capability);
    }

    @Override
    public int getRecommendedMonitoringThreads() {
        // macOS FSEvents is very efficient, usually 1-2 threads are sufficient
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(2, Math.max(1, cores / 4));
    }

    @Override
    public long getMaxRecommendedCacheSize() {
        // Recommend up to 25% of available memory for caching
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        if (maxMemory == Long.MAX_VALUE) {
            // If max memory is not limited, use a conservative default
            return 512 * 1024 * 1024; // 512MB
        }
        return maxMemory / 4;
    }

    @Override
    public Map<String, String> getConfigurationProperties() {
        return new HashMap<>(configurationProperties);
    }

    @Override
    public String getNativeLibraryName() {
        return NATIVE_LIBRARY_NAME;
    }

    @Override
    public boolean requiresNativeLibraries() {
        return true;
    }

    @Override
    public String[] getMountOptions() {
        return new String[] {
            // macOS-specific FUSE mount options
            "-o", "noappledouble",              // Disable ._ metadata files
            "-o", "daemon_timeout=60",          // 60 second timeout
            "-o", "defer_permissions",          // Defer permission checks to filesystem
            "-o", "local",                      // Local filesystem (not network)
            "-o", "allow_other",               // Allow other users to access
            "-o", "auto_cache",                // Enable automatic caching
            "-o", "volname=F1r3Drive",         // Volume name in Finder
            "-o", "iosize=65536",              // Optimize I/O block size
            "-o", "fsname=f1r3drive"           // Filesystem name
        };
    }

    @Override
    public int getDefaultFilePermissions() {
        return 0644; // rw-r--r--
    }

    @Override
    public int getDefaultDirectoryPermissions() {
        return 0755; // rwxr-xr-x
    }

    /**
     * Detects the macOS version.
     *
     * @return macOS version string
     */
    private String detectMacOSVersion() {
        String osVersion = System.getProperty("os.version", "unknown");
        String osName = System.getProperty("os.name", "unknown");

        // Try to get more detailed macOS version information
        try {
            ProcessBuilder pb = new ProcessBuilder("sw_vers", "-productVersion");
            Process process = pb.start();
            process.waitFor();

            if (process.exitValue() == 0) {
                byte[] output = process.getInputStream().readAllBytes();
                String version = new String(output).trim();
                return String.format("macOS %s", version);
            }
        } catch (Exception e) {
            // Fall back to system properties
        }

        return String.format("%s %s", osName, osVersion);
    }

    /**
     * Creates configuration properties specific to macOS.
     *
     * @return configuration properties map
     */
    private Map<String, String> createConfigurationProperties() {
        Map<String, String> properties = new HashMap<>();

        properties.put("platform.name", "macOS");
        properties.put("platform.version", platformVersion);
        properties.put("platform.architecture", architecture);
        properties.put("platform.filesystem.case_sensitive", detectCaseSensitivity());
        properties.put("platform.filesystem.supports_extended_attributes", "true");
        properties.put("platform.filesystem.supports_symbolic_links", "true");
        properties.put("platform.filesystem.unicode_normalization", "nfc");

        // FSEvents specific properties
        properties.put("fsevents.available", String.valueOf(isFSEventsAvailable()));
        properties.put("fsevents.file_events_supported", String.valueOf(isFileEventsSupported()));
        properties.put("fsevents.recommended_latency", "0.1");
        properties.put("fsevents.default_flags", String.valueOf(getDefaultFSEventsFlags()));

        // File Provider Framework properties
        properties.put("file_provider.available", String.valueOf(isFileProviderAvailable()));
        properties.put("file_provider.min_version", "10.15");

        // Memory and performance settings
        properties.put("cache.default_size", String.valueOf(getMaxRecommendedCacheSize()));
        properties.put("monitoring.default_threads", String.valueOf(getRecommendedMonitoringThreads()));
        properties.put("io.buffer_size", "65536");
        properties.put("io.async_supported", "true");

        return properties;
    }

    /**
     * Detects if the filesystem is case-sensitive.
     *
     * @return "true" if case-sensitive, "false" otherwise
     */
    private String detectCaseSensitivity() {
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            java.io.File testFile1 = new java.io.File(tmpDir, "CaseSensitivityTest");
            java.io.File testFile2 = new java.io.File(tmpDir, "casesensitivitytest");

            if (testFile1.createNewFile()) {
                boolean caseSensitive = !testFile2.exists();
                testFile1.delete();
                return String.valueOf(caseSensitive);
            }
        } catch (Exception e) {
            // Default assumption for macOS
        }

        return "false"; // Most macOS installations use case-insensitive APFS/HFS+
    }

    /**
     * Checks if FSEvents is available on this system.
     *
     * @return true if FSEvents is available
     */
    private boolean isFSEventsAvailable() {
        // FSEvents is available on all supported macOS versions (10.5+)
        return true;
    }

    /**
     * Checks if file-level FSEvents are supported.
     *
     * @return true if file-level events are supported
     */
    private boolean isFileEventsSupported() {
        // File-level events require macOS 10.7+
        String version = System.getProperty("os.version", "0");
        try {
            String[] parts = version.split("\\.");
            int majorVersion = Integer.parseInt(parts[0]);
            return majorVersion >= 11; // macOS 10.7 corresponds to Darwin 11
        } catch (Exception e) {
            return true; // Assume it's supported on modern macOS
        }
    }

    /**
     * Gets default FSEvents flags for this platform.
     *
     * @return default FSEvents flags
     */
    private int getDefaultFSEventsFlags() {
        // These flags are defined in FSEventsMonitor class
        return 0x00000010 | 0x00000004 | 0x00000002; // FileEvents | WatchRoot | NoDefer
    }

    /**
     * Checks if File Provider Framework is available.
     *
     * @return true if File Provider Framework is available
     */
    public boolean isFileProviderAvailable() {
        // File Provider Framework requires macOS 10.15+
        try {
            ProcessBuilder pb = new ProcessBuilder("sw_vers", "-productVersion");
            Process process = pb.start();
            process.waitFor();

            if (process.exitValue() == 0) {
                byte[] output = process.getInputStream().readAllBytes();
                String version = new String(output).trim();
                String[] parts = version.split("\\.");

                if (parts.length >= 2) {
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);

                    // macOS 10.15+ or macOS 11+
                    return (major == 10 && minor >= 15) || major >= 11;
                }
            }
        } catch (Exception e) {
            // If we can't determine the version, assume it's not available
        }

        return false;
    }

    /**
     * Checks if the current system meets the minimum requirements for F1r3Drive on macOS.
     *
     * @return true if system requirements are met
     */
    public boolean meetsSystemRequirements() {
        // Check minimum macOS version (10.15 for File Provider Framework)
        if (!isFileProviderAvailable()) {
            return false;
        }

        // Check JVM compatibility
        if (!isJVMCompatible()) {
            return false;
        }

        // Check if running on Apple Silicon or Intel
        String arch = getArchitecture();
        if (!arch.equals("x86_64") && !arch.equals("arm64")) {
            return false;
        }

        return true;
    }

    /**
     * Gets the expected native library file name for the current architecture.
     *
     * @return native library file name
     */
    public String getNativeLibraryFileName() {
        return String.format("lib%s.dylib", NATIVE_LIBRARY_NAME);
    }

    /**
     * Gets macOS-specific error messages and troubleshooting information.
     *
     * @return troubleshooting information map
     */
    public Map<String, String> getTroubleshootingInfo() {
        Map<String, String> info = new HashMap<>();

        info.put("native_library_path", getNativeLibraryFileName());
        info.put("required_permissions", "Full Disk Access may be required for some directories");
        info.put("file_provider_status", isFileProviderAvailable() ? "Available" : "Requires macOS 10.15+");
        info.put("fsevents_status", isFSEventsAvailable() ? "Available" : "Not available");
        info.put("system_requirements_met", String.valueOf(meetsSystemRequirements()));

        if (!meetsSystemRequirements()) {
            info.put("upgrade_recommendation", "Please upgrade to macOS 10.15 (Catalina) or later");
        }

        return info;
    }
}
