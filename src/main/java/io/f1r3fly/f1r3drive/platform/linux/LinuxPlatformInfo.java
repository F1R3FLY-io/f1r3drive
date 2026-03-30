package io.f1r3fly.f1r3drive.platform.linux;

import io.f1r3fly.f1r3drive.platform.PlatformInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

/**
 * Linux-specific platform information and capabilities.
 * Provides configuration for inotify-based file monitoring and FUSE integration.
 */
public class LinuxPlatformInfo extends PlatformInfo {

    private static final Set<Capability> LINUX_CAPABILITIES = EnumSet.of(
        Capability.NATIVE_FILE_MONITORING,    // inotify
        Capability.VIRTUAL_FILESYSTEM,        // FUSE
        Capability.LAZY_LOADING,             // Placeholder files
        Capability.BACKGROUND_SYNC,          // Background synchronization
        Capability.EXTENDED_ATTRIBUTES,      // xattr support
        Capability.SYMBOLIC_LINKS,           // Symlink support
        Capability.CASE_SENSITIVE_PATHS,     // Case-sensitive filesystem
        Capability.UNICODE_NORMALIZATION     // Unicode filename normalization
    );

    private final String kernelVersion;
    private final String distribution;
    private final boolean hasInotify;
    private final boolean hasFuse;
    private final int maxInotifyWatches;

    public LinuxPlatformInfo() {
        this.kernelVersion = detectKernelVersion();
        this.distribution = detectDistribution();
        this.hasInotify = checkInotifySupport();
        this.hasFuse = checkFuseSupport();
        this.maxInotifyWatches = detectMaxInotifyWatches();
    }

    @Override
    public Platform getPlatform() {
        return Platform.LINUX;
    }

    @Override
    public String getPlatformVersion() {
        return kernelVersion;
    }

    @Override
    public String getArchitecture() {
        return detectArchitecture();
    }

    @Override
    public boolean hasCapability(Capability capability) {
        switch (capability) {
            case NATIVE_FILE_MONITORING:
                return hasInotify;
            case VIRTUAL_FILESYSTEM:
                return hasFuse;
            case SYSTEM_INTEGRATION:
                // Linux has limited system integration compared to macOS
                return false;
            default:
                return LINUX_CAPABILITIES.contains(capability);
        }
    }

    @Override
    public int getRecommendedMonitoringThreads() {
        // Linux inotify is efficient, usually 1-2 threads are sufficient
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(2, Math.max(1, cores / 4));
    }

    @Override
    public long getMaxRecommendedCacheSize() {
        // Conservative cache size for Linux systems
        long maxMemory = Runtime.getRuntime().maxMemory();
        return maxMemory / 8; // Use 12.5% of available heap
    }

    @Override
    public Map<String, String> getConfigurationProperties() {
        Map<String, String> config = new HashMap<>();
        config.put("kernel.version", kernelVersion);
        config.put("distribution", distribution);
        config.put("inotify.supported", String.valueOf(hasInotify));
        config.put("fuse.supported", String.valueOf(hasFuse));
        config.put("inotify.max_watches", String.valueOf(maxInotifyWatches));
        config.put("filesystem.case_sensitive", "true");
        config.put("path.separator", "/");
        config.put("monitoring.backend", "inotify");
        config.put("virtual_fs.backend", "fuse");
        return config;
    }

    @Override
    public String getNativeLibraryName() {
        // Linux uses JNR-FFI, no additional native libraries needed
        return null;
    }

    @Override
    public boolean requiresNativeLibraries() {
        return false;
    }

    @Override
    public String[] getMountOptions() {
        return new String[] {
            "allow_other",           // Allow other users to access
            "default_permissions",   // Use default permission checking
            "big_writes",           // Enable big writes for performance
            "splice_write",         // Enable splice for better performance
            "splice_move",          // Enable splice move
            "splice_read",          // Enable splice read
            "auto_unmount",         // Automatically unmount on exit
            "fsname=f1r3drive"      // Filesystem name
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
     * Gets the Linux distribution name.
     *
     * @return distribution name
     */
    public String getDistribution() {
        return distribution;
    }

    /**
     * Checks if inotify is supported.
     *
     * @return true if inotify is supported
     */
    public boolean hasInotifySupport() {
        return hasInotify;
    }

    /**
     * Checks if FUSE is supported.
     *
     * @return true if FUSE is supported
     */
    public boolean hasFuseSupport() {
        return hasFuse;
    }

    /**
     * Gets the maximum number of inotify watches allowed.
     *
     * @return maximum inotify watches
     */
    public int getMaxInotifyWatches() {
        return maxInotifyWatches;
    }

    private String detectKernelVersion() {
        try {
            String version = System.getProperty("os.version", "unknown");
            if (!version.equals("unknown")) {
                return version;
            }

            // Try to read from /proc/version
            java.nio.file.Path versionFile = java.nio.file.Paths.get("/proc/version");
            if (java.nio.file.Files.exists(versionFile)) {
                String content = java.nio.file.Files.readString(versionFile);
                // Extract version from "Linux version X.Y.Z..."
                String[] parts = content.split(" ");
                if (parts.length >= 3 && parts[2] != null) {
                    return parts[2];
                }
            }
        } catch (Exception e) {
            // Ignore and return unknown
        }
        return "unknown";
    }

    private String detectDistribution() {
        try {
            // Try /etc/os-release first (standard)
            java.nio.file.Path osRelease = java.nio.file.Paths.get("/etc/os-release");
            if (java.nio.file.Files.exists(osRelease)) {
                String content = java.nio.file.Files.readString(osRelease);
                for (String line : content.split("\n")) {
                    if (line.startsWith("NAME=")) {
                        String name = line.substring(5).replaceAll("\"", "");
                        return name;
                    }
                }
            }

            // Try legacy files
            String[] releaseFiles = {"/etc/redhat-release", "/etc/debian_version", "/etc/alpine-release"};
            for (String file : releaseFiles) {
                java.nio.file.Path path = java.nio.file.Paths.get(file);
                if (java.nio.file.Files.exists(path)) {
                    return java.nio.file.Files.readString(path).trim();
                }
            }
        } catch (Exception e) {
            // Ignore and return unknown
        }
        return "unknown";
    }

    private boolean checkInotifySupport() {
        try {
            // Check if /proc/sys/fs/inotify exists
            java.nio.file.Path inotifyPath = java.nio.file.Paths.get("/proc/sys/fs/inotify");
            return java.nio.file.Files.exists(inotifyPath);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkFuseSupport() {
        try {
            // Check if /dev/fuse exists or /proc/filesystems contains fuse
            java.nio.file.Path fuseDev = java.nio.file.Paths.get("/dev/fuse");
            if (java.nio.file.Files.exists(fuseDev)) {
                return true;
            }

            java.nio.file.Path filesystems = java.nio.file.Paths.get("/proc/filesystems");
            if (java.nio.file.Files.exists(filesystems)) {
                String content = java.nio.file.Files.readString(filesystems);
                return content.contains("fuse");
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private int detectMaxInotifyWatches() {
        try {
            java.nio.file.Path maxWatchesPath = java.nio.file.Paths.get("/proc/sys/fs/inotify/max_user_watches");
            if (java.nio.file.Files.exists(maxWatchesPath)) {
                String content = java.nio.file.Files.readString(maxWatchesPath).trim();
                return Integer.parseInt(content);
            }
        } catch (Exception e) {
            // Ignore
        }
        // Default value on most Linux systems
        return 8192;
    }

    @Override
    public String toString() {
        return String.format("Linux %s (%s) - %s - inotify:%s, fuse:%s",
            kernelVersion, getArchitecture(), distribution, hasInotify, hasFuse);
    }
}
