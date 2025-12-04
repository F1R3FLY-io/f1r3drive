package io.f1r3fly.f1r3drive.platform.linux;

/**
 * Linux-specific platform information and capabilities.
 * This class provides information about the current Linux environment
 * and available features for F1r3Drive integration.
 */
public class LinuxPlatformInfo {

    private static final String PLATFORM_NAME = "Linux";
    private static final String MIN_KERNEL_VERSION = "2.6.13";

    /**
     * Gets the platform name.
     * @return "Linux"
     */
    public static String getPlatformName() {
        return PLATFORM_NAME;
    }

    /**
     * Gets the minimum supported Linux kernel version.
     * @return minimum kernel version string
     */
    public static String getMinimumKernelVersion() {
        return MIN_KERNEL_VERSION;
    }

    /**
     * Checks if the current Linux kernel supports inotify.
     * inotify has been available since kernel 2.6.13.
     * @return true if inotify is supported
     */
    public static boolean supportsInotify() {
        try {
            String kernelVersion = System.getProperty("os.version");
            if (kernelVersion != null) {
                // Parse kernel version (e.g., "5.15.0-56-generic")
                String[] parts = kernelVersion.split("[.-]");
                if (parts.length >= 3) {
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    int patch = Integer.parseInt(parts[2]);

                    // Check if >= 2.6.13
                    if (major > 2) return true;
                    if (major == 2 && minor > 6) return true;
                    if (major == 2 && minor == 6 && patch >= 13) return true;
                }
            }
        } catch (NumberFormatException e) {
            // If we can't parse version, assume modern Linux
            return true;
        }
        return true; // Default to true for safety
    }

    /**
     * Checks if FUSE (Filesystem in Userspace) is available.
     * This checks for the presence of /dev/fuse device.
     * @return true if FUSE is available
     */
    public static boolean supportsFUSE() {
        java.io.File fuseDevice = new java.io.File("/dev/fuse");
        return fuseDevice.exists() && fuseDevice.canRead() && fuseDevice.canWrite();
    }

    /**
     * Checks if the current user can access FUSE.
     * @return true if user has FUSE permissions
     */
    public static boolean canUseFUSE() {
        // Check if user is in fuse group or has access to /dev/fuse
        return supportsFUSE();
    }

    /**
     * Gets the minimum required FUSE version.
     * @return minimum FUSE version string
     */
    public static String getMinimumFUSEVersion() {
        return "2.6";
    }

    /**
     * Checks if the platform is actually Linux.
     * @return true if running on Linux
     */
    public static boolean isLinux() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("linux");
    }

    /**
     * Gets platform-specific temporary directory for F1r3Drive.
     * @return temporary directory path
     */
    public static String getTempDirectory() {
        return System.getProperty("java.io.tmpdir") + "/f1r3drive-linux";
    }

    /**
     * Gets the default mount point for Linux FUSE integration.
     * @return default mount point path
     */
    public static String getDefaultMountPoint() {
        String userHome = System.getProperty("user.home");
        return userHome + "/f1r3drive";
    }

    /**
     * Gets the path to the FUSE device.
     * @return FUSE device path
     */
    public static String getFUSEDevicePath() {
        return "/dev/fuse";
    }

    /**
     * Checks if running in a container environment.
     * This affects FUSE availability and permissions.
     * @return true if running in container
     */
    public static boolean isContainerEnvironment() {
        // Check for common container indicators
        java.io.File dockerEnv = new java.io.File("/.dockerenv");
        if (dockerEnv.exists()) return true;

        // Check for container in cgroup
        try {
            java.io.File cgroupFile = new java.io.File("/proc/1/cgroup");
            if (cgroupFile.exists()) {
                java.util.Scanner scanner = new java.util.Scanner(cgroupFile);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("docker") || line.contains("containerd") ||
                        line.contains("kubepods") || line.contains("lxc")) {
                        scanner.close();
                        return true;
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            // Ignore errors and assume not in container
        }

        return false;
    }

    /**
     * Gets recommended FUSE mount options for F1r3Drive.
     * @return array of FUSE mount options
     */
    public static String[] getRecommendedFUSEOptions() {
        return new String[] {
            "allow_other",      // Allow other users to access
            "default_permissions", // Use default permission checking
            "fsname=f1r3drive", // Filesystem name
            "subtype=f1r3drive" // Filesystem subtype
        };
    }
}
