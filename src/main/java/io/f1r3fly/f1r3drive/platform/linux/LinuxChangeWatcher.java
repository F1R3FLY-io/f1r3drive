package io.f1r3fly.f1r3drive.platform.linux;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import io.f1r3fly.f1r3drive.platform.PlatformInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.FuseFS;

/**
 * Main integration point for Linux platform.
 * Manages FUSE filesystem and inotify monitoring, providing bidirectional synchronization
 * with InMemoryFileSystem and supporting placeholder files for lazy loading.
 */
public class LinuxChangeWatcher implements ChangeWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        LinuxChangeWatcher.class
    );

    private final FileSystem fileSystem;
    private final LinuxPlatformInfo platformInfo;
    private final AtomicBoolean monitoring;

    private InotifyMonitor inotifyMonitor;
    private FuseFilesystem fuseFilesystem;
    private Object fuseMounter; // Can be FuseStubFS or similar
    private ChangeListener changeListener;
    private FileChangeCallback fileChangeCallback;
    private String mountPath;

    public LinuxChangeWatcher(FileSystem fileSystem) {
        if (fileSystem == null) {
            throw new NullPointerException("FileSystem cannot be null");
        }

        this.fileSystem = fileSystem;
        this.platformInfo = new LinuxPlatformInfo();
        this.monitoring = new AtomicBoolean(false);

        LOGGER.info("Initialized LinuxChangeWatcher: {}", platformInfo);
    }

    @Override
    public void startMonitoring(String path, ChangeListener listener)
        throws Exception {
        if (monitoring.get()) {
            throw new IllegalStateException("Monitoring is already active");
        }

        if (listener == null) {
            throw new IllegalArgumentException("ChangeListener cannot be null");
        }

        this.changeListener = listener;
        this.mountPath = path;

        // Validate platform capabilities
        validatePlatformSupport();

        // Create mount point directory if it doesn't exist
        ensureMountPointExists(path);

        try {
            // Initialize FUSE filesystem
            initializeFuseFilesystem();

            // Initialize inotify monitor
            initializeInotifyMonitor();

            // Mount FUSE filesystem
            mountFilesystem(path);

            // Start inotify monitoring on the mount point
            startInotifyMonitoring(path);

            monitoring.set(true);
            LOGGER.info("Started Linux monitoring for path: {}", path);
            changeListener.onMonitoringStarted(path);
        } catch (Exception e) {
            // Cleanup on failure
            cleanupResources();
            throw new Exception("Failed to start Linux monitoring", e);
        }
    }

    @Override
    public void stopMonitoring() {
        if (!monitoring.get()) {
            return;
        }

        LOGGER.info("Stopping Linux monitoring...");
        monitoring.set(false);

        // Stop inotify monitoring first
        if (inotifyMonitor != null && inotifyMonitor.isMonitoring()) {
            try {
                inotifyMonitor.stopMonitoring();
            } catch (Exception e) {
                LOGGER.warn("Error stopping inotify monitor", e);
            }
        }

        // Unmount FUSE filesystem
        unmountFilesystem();

        // Cleanup resources
        cleanupResources();

        LOGGER.info("Stopped Linux monitoring");
        if (changeListener != null) {
            changeListener.onMonitoringStopped(mountPath);
        }
    }

    @Override
    public boolean isMonitoring() {
        return monitoring.get();
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return platformInfo;
    }

    @Override
    public void cleanup() {
        stopMonitoring();
    }

    @Override
    public void setFileChangeCallback(FileChangeCallback callback) {
        this.fileChangeCallback = callback;
        if (fuseFilesystem != null) {
            // FUSE filesystem will use this callback for lazy loading
            LOGGER.debug("Updated file change callback for FUSE filesystem");
        }
    }

    /**
     * Registers a placeholder file that will be loaded from blockchain on access.
     *
     * @param path the virtual file path
     * @param size the file size (if known, 0 if unknown)
     * @param lastModified the last modified timestamp
     */
    public void registerPlaceholderFile(
        String path,
        long size,
        long lastModified
    ) {
        if (fuseFilesystem != null) {
            // Convert absolute path to relative path within the mount
            String relativePath = toRelativePath(path);
            fuseFilesystem.registerPlaceholder(
                relativePath,
                size,
                lastModified
            );
            LOGGER.debug(
                "Registered placeholder file: {} -> {} (size: {})",
                path,
                relativePath,
                size
            );
        }
    }

    /**
     * Preloads blockchain files as placeholders in the FUSE filesystem.
     *
     * @param blockchainFiles array of file paths from blockchain
     */
    public void preloadBlockchainFiles(String[] blockchainFiles) {
        if (fuseFilesystem == null || fileChangeCallback == null) {
            LOGGER.warn(
                "Cannot preload files - FUSE or callback not initialized"
            );
            return;
        }

        LOGGER.info(
            "Preloading {} blockchain files as placeholders",
            blockchainFiles.length
        );

        for (String filePath : blockchainFiles) {
            try {
                // Get metadata from blockchain
                FileChangeCallback.FileMetadata metadata =
                    fileChangeCallback.getFileMetadata(filePath);
                if (metadata != null && !metadata.isDirectory()) {
                    registerPlaceholderFile(
                        filePath,
                        metadata.getSize(),
                        metadata.getLastModified()
                    );
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to preload file: {}", filePath, e);
            }
        }

        LOGGER.info(
            "Preloaded {} placeholder files",
            fuseFilesystem.getPlaceholderCount()
        );
    }

    /**
     * Gets statistics about the current monitoring state.
     *
     * @return monitoring statistics
     */
    public LinuxMonitoringStatistics getMonitoringStatistics() {
        InotifyMonitor.MonitoringStatistics inotifyStats = null;
        if (inotifyMonitor != null) {
            inotifyStats = inotifyMonitor.getStatistics();
        }

        int placeholderCount = 0;
        if (fuseFilesystem != null) {
            placeholderCount = fuseFilesystem.getPlaceholderCount();
        }

        return new LinuxMonitoringStatistics(
            monitoring.get(),
            mountPath,
            inotifyStats,
            placeholderCount,
            fuseMounter != null
        );
    }

    private void validatePlatformSupport() throws Exception {
        if (!platformInfo.hasInotifySupport()) {
            throw new Exception("inotify is not supported on this system");
        }

        if (!platformInfo.hasFuseSupport()) {
            throw new Exception("FUSE is not supported on this system");
        }

        if (!platformInfo.isJVMCompatible()) {
            throw new Exception(
                "JVM version is not compatible (requires Java 17+)"
            );
        }

        LOGGER.debug("Platform validation passed: {}", platformInfo);
    }

    private void ensureMountPointExists(String path) throws IOException {
        Path mountPoint = Paths.get(path);

        if (!Files.exists(mountPoint)) {
            Files.createDirectories(mountPoint);
            LOGGER.debug("Created mount point directory: {}", path);
        } else if (!Files.isDirectory(mountPoint)) {
            throw new IOException(
                "Mount point exists but is not a directory: " + path
            );
        } else if (!isDirectoryEmpty(mountPoint)) {
            LOGGER.warn("Mount point is not empty: {}", path);
        }
    }

    private boolean isDirectoryEmpty(Path directory) {
        try {
            return Files.list(directory).findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private void initializeFuseFilesystem() {
        // Create integrated change listener that forwards events
        ChangeListener integratedListener = new IntegratedChangeListener();

        fuseFilesystem = new FuseFilesystem(
            fileSystem,
            fileChangeCallback,
            integratedListener
        );
        LOGGER.debug("Initialized FUSE filesystem");
    }

    private void initializeInotifyMonitor() {
        // Create integrated change listener for inotify events
        ChangeListener inotifyListener = new InotifyChangeListener();

        inotifyMonitor = new InotifyMonitor(inotifyListener);
        LOGGER.debug("Initialized inotify monitor");
    }

    private void mountFilesystem(String path) throws Exception {
        if (fuseFilesystem == null) {
            throw new IllegalStateException("FUSE filesystem not initialized");
        }

        // Get mount options from platform info
        String[] mountOptions = platformInfo.getMountOptions();

        try {
            // Use fuseFilesystem directly for mounting
            // Mount with a timeout to prevent hanging
            CompletableFuture<Void> mountFuture = CompletableFuture.runAsync(
                () -> {
                    try {
                        fuseFilesystem.mount(
                            Paths.get(path),
                            false,
                            false,
                            mountOptions
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("FUSE mount failed", e);
                    }
                }
            );

            // Wait for mount with timeout
            mountFuture.get(30, TimeUnit.SECONDS);
            fuseMounter = fuseFilesystem; // Store reference for unmounting

            LOGGER.info("Successfully mounted FUSE filesystem at: {}", path);
        } catch (Exception e) {
            fuseMounter = null;
            throw new Exception("Failed to mount FUSE filesystem", e);
        }
    }

    private void unmountFilesystem() {
        if (fuseMounter != null) {
            try {
                if (fuseMounter instanceof ru.serce.jnrfuse.FuseStubFS) {
                    ((ru.serce.jnrfuse.FuseStubFS) fuseMounter).umount();
                }
                LOGGER.info("Unmounted FUSE filesystem from: {}", mountPath);
            } catch (Exception e) {
                LOGGER.warn("Error unmounting FUSE filesystem", e);

                // Try force unmount
                try {
                    Runtime.getRuntime().exec(
                        new String[] { "fusermount", "-u", "-z", mountPath }
                    );
                    LOGGER.info("Force unmounted FUSE filesystem");
                } catch (Exception forceError) {
                    LOGGER.error(
                        "Failed to force unmount FUSE filesystem",
                        forceError
                    );
                }
            } finally {
                fuseMounter = null;
            }
        }
    }

    private void startInotifyMonitoring(String path) throws IOException {
        if (inotifyMonitor == null) {
            throw new IllegalStateException("Inotify monitor not initialized");
        }

        inotifyMonitor.startMonitoring(path);
        LOGGER.debug("Started inotify monitoring for: {}", path);
    }

    private void cleanupResources() {
        inotifyMonitor = null;
        fuseFilesystem = null;
        fuseMounter = null;
    }

    private String toRelativePath(String absolutePath) {
        if (mountPath == null) {
            return absolutePath;
        }

        Path mount = Paths.get(mountPath);
        Path file = Paths.get(absolutePath);

        try {
            return mount.relativize(file).toString();
        } catch (Exception e) {
            return absolutePath;
        }
    }

    /**
     * Integrated change listener that forwards FUSE filesystem events to the main listener.
     */
    private class IntegratedChangeListener implements ChangeListener {

        @Override
        public void onFileCreated(String path) {
            if (changeListener != null) {
                changeListener.onFileCreated(path);
            }
        }

        @Override
        public void onFileModified(String path) {
            if (changeListener != null) {
                changeListener.onFileModified(path);
            }
        }

        @Override
        public void onFileDeleted(String path) {
            if (changeListener != null) {
                changeListener.onFileDeleted(path);
            }
        }

        @Override
        public void onFileMoved(String oldPath, String newPath) {
            if (changeListener != null) {
                changeListener.onFileMoved(oldPath, newPath);
            }
        }

        @Override
        public void onFileAccessed(String path) {
            if (changeListener != null) {
                changeListener.onFileAccessed(path);
            }
        }

        @Override
        public void onFileAttributesChanged(String path) {
            if (changeListener != null) {
                changeListener.onFileAttributesChanged(path);
            }
        }

        @Override
        public void onError(Exception error, String path) {
            LOGGER.error("FUSE filesystem error at path: {}", path, error);
            if (changeListener != null) {
                changeListener.onError(error, path);
            }
        }

        @Override
        public void onMonitoringStarted(String watchedPath) {
            LOGGER.debug("FUSE monitoring started for: {}", watchedPath);
        }

        @Override
        public void onMonitoringStopped(String watchedPath) {
            LOGGER.debug("FUSE monitoring stopped for: {}", watchedPath);
        }
    }

    /**
     * Change listener specifically for inotify events.
     */
    private class InotifyChangeListener implements ChangeListener {

        @Override
        public void onFileCreated(String path) {
            LOGGER.trace("Inotify: file created - {}", path);
            if (changeListener != null) {
                changeListener.onFileCreated(path);
            }
        }

        @Override
        public void onFileModified(String path) {
            LOGGER.trace("Inotify: file modified - {}", path);
            if (changeListener != null) {
                changeListener.onFileModified(path);
            }
        }

        @Override
        public void onFileDeleted(String path) {
            LOGGER.trace("Inotify: file deleted - {}", path);
            if (changeListener != null) {
                changeListener.onFileDeleted(path);
            }
        }

        @Override
        public void onFileMoved(String oldPath, String newPath) {
            LOGGER.trace("Inotify: file moved - {} -> {}", oldPath, newPath);
            if (changeListener != null) {
                changeListener.onFileMoved(oldPath, newPath);
            }
        }

        @Override
        public void onFileAccessed(String path) {
            LOGGER.trace("Inotify: file accessed - {}", path);
            if (changeListener != null) {
                changeListener.onFileAccessed(path);
            }
        }

        @Override
        public void onFileAttributesChanged(String path) {
            LOGGER.trace("Inotify: file attributes changed - {}", path);
            if (changeListener != null) {
                changeListener.onFileAttributesChanged(path);
            }
        }

        @Override
        public void onError(Exception error, String path) {
            LOGGER.error("Inotify error at path: {}", path, error);
            if (changeListener != null) {
                changeListener.onError(error, path);
            }
        }

        @Override
        public void onMonitoringStarted(String watchedPath) {
            LOGGER.debug("Inotify monitoring started for: {}", watchedPath);
        }

        @Override
        public void onMonitoringStopped(String watchedPath) {
            LOGGER.debug("Inotify monitoring stopped for: {}", watchedPath);
        }
    }

    /**
     * Statistics about Linux monitoring state.
     */
    public static class LinuxMonitoringStatistics {

        private final boolean monitoring;
        private final String mountPath;
        private final InotifyMonitor.MonitoringStatistics inotifyStats;
        private final int placeholderCount;
        private final boolean fuseMounted;

        public LinuxMonitoringStatistics(
            boolean monitoring,
            String mountPath,
            InotifyMonitor.MonitoringStatistics inotifyStats,
            int placeholderCount,
            boolean fuseMounted
        ) {
            this.monitoring = monitoring;
            this.mountPath = mountPath;
            this.inotifyStats = inotifyStats;
            this.placeholderCount = placeholderCount;
            this.fuseMounted = fuseMounted;
        }

        public boolean isMonitoring() {
            return monitoring;
        }

        public String getMountPath() {
            return mountPath;
        }

        public InotifyMonitor.MonitoringStatistics getInotifyStatistics() {
            return inotifyStats;
        }

        public int getPlaceholderCount() {
            return placeholderCount;
        }

        public boolean isFuseMounted() {
            return fuseMounted;
        }

        @Override
        public String toString() {
            return String.format(
                "LinuxMonitoring[monitoring=%s, mount=%s, placeholders=%d, fuse=%s, inotify=%s]",
                monitoring,
                mountPath,
                placeholderCount,
                fuseMounted,
                inotifyStats
            );
        }
    }
}
