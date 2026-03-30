package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import io.f1r3fly.f1r3drive.platform.PlatformInfo;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main integration point for macOS platform-specific file monitoring.
 * Manages FSEventsMonitor and FileProviderIntegration, providing
 * bidirectional synchronization with InMemoryFileSystem.
 *
 * This class serves as the primary bridge between the F1r3Drive core
 * and macOS-specific filesystem monitoring capabilities.
 */
public class MacOSChangeWatcher implements ChangeWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MacOSChangeWatcher.class);

    private final MacOSPlatformInfo platformInfo;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Core components
    private FSEventsMonitor fsEventsMonitor;
    private FileProviderIntegration fileProviderIntegration;
    private ChangeListener changeListener;
    private FileChangeCallback fileChangeCallback;
    private InMemoryFileSystem inMemoryFileSystem;
    private io.f1r3fly.f1r3drive.placeholder.PlaceholderManager placeholderManager;

    // Configuration
    private String watchedPath;
    private boolean fileProviderEnabled = true;
    private boolean deepIntegrationEnabled = true;

    /**
     * Creates a new MacOSChangeWatcher instance.
     */
    public MacOSChangeWatcher() {
        this.platformInfo = new MacOSPlatformInfo();
        this.fsEventsMonitor = new FSEventsMonitor();

        LOGGER.info("MacOSChangeWatcher created for {}", platformInfo);

        // Check system requirements
        if (!platformInfo.meetsSystemRequirements()) {
            LOGGER.warn("System does not meet minimum requirements: {}",
                       platformInfo.getTroubleshootingInfo());
        }
    }

    /**
     * Creates a MacOSChangeWatcher with custom configuration.
     *
     * @param fileProviderEnabled whether to enable File Provider integration
     * @param deepIntegrationEnabled whether to enable deep macOS integration
     */
    public MacOSChangeWatcher(boolean fileProviderEnabled, boolean deepIntegrationEnabled) {
        this();
        this.fileProviderEnabled = fileProviderEnabled;
        this.deepIntegrationEnabled = deepIntegrationEnabled;
    }

    @Override
    public void startMonitoring(String path, ChangeListener listener) throws Exception {
        lock.writeLock().lock();
        try {
            if (isMonitoring.get()) {
                throw new IllegalStateException("MacOS change watcher is already monitoring path: " + watchedPath);
            }

            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("Path cannot be null or empty");
            }

            if (listener == null) {
                throw new IllegalArgumentException("ChangeListener cannot be null");
            }

            this.watchedPath = path;
            this.changeListener = new MacOSChangeListenerAdapter(listener);

            LOGGER.info("Starting macOS file monitoring for path: {}", path);

            // Validate system requirements
            validateSystemRequirements();

            // Initialize File Provider integration if enabled
            if (fileProviderEnabled && platformInfo.isFileProviderAvailable()) {
                initializeFileProvider();
            }

            // Start FSEvents monitoring
            startFSEventsMonitoring();

            isMonitoring.set(true);
            LOGGER.info("macOS file monitoring started successfully for path: {}", path);

        } catch (Exception e) {
            // Clean up on failure
            cleanup();
            throw new Exception("Failed to start macOS file monitoring: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void stopMonitoring() {
        lock.writeLock().lock();
        try {
            if (!isMonitoring.get()) {
                return;
            }

            LOGGER.info("Stopping macOS file monitoring for path: {}", watchedPath);

            isMonitoring.set(false);

            // Stop FSEvents monitoring
            if (fsEventsMonitor != null) {
                try {
                    fsEventsMonitor.stopMonitoring();
                } catch (Exception e) {
                    LOGGER.error("Error stopping FSEvents monitor", e);
                }
            }

            // Stop File Provider integration
            if (fileProviderIntegration != null) {
                try {
                    fileProviderIntegration.shutdown();
                } catch (Exception e) {
                    LOGGER.error("Error stopping File Provider integration", e);
                }
            }

            LOGGER.info("macOS file monitoring stopped for path: {}", watchedPath);

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isMonitoring() {
        lock.readLock().lock();
        try {
            return isMonitoring.get() &&
                   (fsEventsMonitor != null && fsEventsMonitor.isMonitoring());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return platformInfo;
    }

    @Override
    public void cleanup() {
        lock.writeLock().lock();
        try {
            LOGGER.debug("Cleaning up macOS change watcher resources");

            if (fsEventsMonitor != null) {
                fsEventsMonitor.stopMonitoring();
            }

            if (fileProviderIntegration != null) {
                fileProviderIntegration.shutdown();
                fileProviderIntegration = null;
            }

            isMonitoring.set(false);
            watchedPath = null;
            changeListener = null;

            LOGGER.debug("macOS change watcher cleanup completed");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void setFileChangeCallback(FileChangeCallback callback) {
        lock.writeLock().lock();
        try {
            this.fileChangeCallback = callback;

            // Update File Provider integration if active
            if (fileProviderIntegration != null) {
                fileProviderIntegration.setFileChangeCallback(callback);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets the InMemoryFileSystem for bidirectional synchronization.
     *
     * @param fileSystem the in-memory filesystem
     */
    public void setInMemoryFileSystem(InMemoryFileSystem fileSystem) {
        lock.writeLock().lock();
        try {
            this.inMemoryFileSystem = fileSystem;

            if (fileProviderIntegration != null) {
                fileProviderIntegration.setInMemoryFileSystem(fileSystem);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enables or disables File Provider integration.
     *
     * @param enabled true to enable, false to disable
     */
    public void setFileProviderEnabled(boolean enabled) {
        if (isMonitoring.get()) {
            throw new IllegalStateException("Cannot change File Provider setting while monitoring is active");
        }
        this.fileProviderEnabled = enabled;
    }

    /**
     * Configures FSEvents monitoring latency.
     *
     * @param latency latency in seconds
     */
    public void setFSEventsLatency(double latency) {
        if (isMonitoring.get()) {
            throw new IllegalStateException("Cannot change FSEvents latency while monitoring is active");
        }
        if (fsEventsMonitor != null) {
            fsEventsMonitor.setLatency(latency);
        }
    }

    /**
     * Gets the current File Provider integration status.
     *
     * @return File Provider integration or null if not available/enabled
     */
    public FileProviderIntegration getFileProviderIntegration() {
        lock.readLock().lock();
        try {
            return fileProviderIntegration;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the FSEvents monitor instance.
     *
     * @return FSEvents monitor
     */
    public FSEventsMonitor getFSEventsMonitor() {
        lock.readLock().lock();
        try {
            return fsEventsMonitor;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates that the system meets requirements for macOS integration.
     *
     * @throws Exception if system requirements are not met
     */
    private void validateSystemRequirements() throws Exception {
        if (!platformInfo.meetsSystemRequirements()) {
            throw new Exception("System does not meet minimum requirements for macOS integration. " +
                              "Required: macOS 10.15+, JDK 17+");
        }

        if (!platformInfo.isJVMCompatible()) {
            throw new Exception("JVM version is not compatible. Required: JDK 17+");
        }

        // Check if native library is available
        if (platformInfo.requiresNativeLibraries()) {
            try {
                if (!fsEventsMonitor.nativeIsAvailable()) {
                    throw new Exception("Native FSEvents library is not available: " +
                                      platformInfo.getNativeLibraryFileName());
                }
            } catch (UnsatisfiedLinkError e) {
                throw new Exception("Failed to load native FSEvents library: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Initializes File Provider integration if available and enabled.
     */
    private void initializeFileProvider() {
        if (!platformInfo.isFileProviderAvailable()) {
            LOGGER.warn("File Provider Framework is not available on this macOS version");
            return;
        }

        try {
            LOGGER.info("Initializing File Provider integration");

            fileProviderIntegration = new FileProviderIntegration();
            fileProviderIntegration.setFileChangeCallback(fileChangeCallback);
            fileProviderIntegration.setInMemoryFileSystem(inMemoryFileSystem);

            if (deepIntegrationEnabled) {
                fileProviderIntegration.initialize(watchedPath);
                LOGGER.info("File Provider integration initialized successfully");
            }

        } catch (Exception e) {
            LOGGER.error("Failed to initialize File Provider integration", e);
            fileProviderIntegration = null;
            // Don't fail the entire startup - continue with FSEvents only
        }
    }

    /**
     * Starts FSEvents monitoring.
     *
     * @throws Exception if FSEvents monitoring cannot be started
     */
    private void startFSEventsMonitoring() throws Exception {
        LOGGER.debug("Starting FSEvents monitoring");

        try {
            fsEventsMonitor.startMonitoring(watchedPath, changeListener);
            LOGGER.debug("FSEvents monitoring started successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to start FSEvents monitoring", e);
            throw e;
        }
    }

    /**
     * Adapter class to bridge between platform-specific events and core ChangeListener.
     */
    private class MacOSChangeListenerAdapter implements ChangeListener {
        private final ChangeListener coreListener;

        public MacOSChangeListenerAdapter(ChangeListener coreListener) {
            this.coreListener = coreListener;
        }

        @Override
        public void onFileCreated(String path) {
            LOGGER.debug("macOS file created: {}", path);
            handleBidirectionalSync(path, "created");
            coreListener.onFileCreated(path);
        }

        @Override
        public void onFileModified(String path) {
            LOGGER.debug("macOS file modified: {}", path);
            handleBidirectionalSync(path, "modified");
            coreListener.onFileModified(path);
        }

        @Override
        public void onFileDeleted(String path) {
            LOGGER.debug("macOS file deleted: {}", path);
            handleBidirectionalSync(path, "deleted");
            coreListener.onFileDeleted(path);
        }

        @Override
        public void onFileMoved(String oldPath, String newPath) {
            LOGGER.debug("macOS file moved: {} -> {}", oldPath, newPath);
            handleBidirectionalSync(oldPath, "moved_from");
            handleBidirectionalSync(newPath, "moved_to");
            coreListener.onFileMoved(oldPath, newPath);
        }

        @Override
        public void onFileAccessed(String path) {
            LOGGER.debug("macOS file accessed: {}", path);

            // Trigger lazy loading if this is a placeholder file
            if (fileChangeCallback != null) {
                try {
                    // Check if this might be a placeholder that needs loading
                    if (shouldTriggerLazyLoad(path)) {
                        byte[] content = fileChangeCallback.loadFileContent(path).join();
                        if (content != null) {
                            LOGGER.debug("Lazy loaded content for file: {}", path);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error during lazy loading for path: {}", path, e);
                }
            }

            coreListener.onFileAccessed(path);
        }

        @Override
        public void onFileAttributesChanged(String path) {
            LOGGER.debug("macOS file attributes changed: {}", path);
            coreListener.onFileAttributesChanged(path);
        }

        @Override
        public void onError(Exception error, String path) {
            LOGGER.error("macOS monitoring error for path: {}", path, error);
            coreListener.onError(error, path);
        }

        @Override
        public void onMonitoringStarted(String watchedPath) {
            LOGGER.info("macOS monitoring started for path: {}", watchedPath);
            coreListener.onMonitoringStarted(watchedPath);
        }

        @Override
        public void onMonitoringStopped(String watchedPath) {
            LOGGER.info("macOS monitoring stopped for path: {}", watchedPath);
            coreListener.onMonitoringStopped(watchedPath);
        }

        /**
         * Handles bidirectional synchronization between macOS filesystem and InMemoryFileSystem.
         *
         * @param path the file path
         * @param operation the operation type
         */
        private void handleBidirectionalSync(String path, String operation) {
            if (inMemoryFileSystem == null) {
                return;
            }

            try {
                // TODO: Implement bidirectional sync logic
                // This would involve:
                // 1. Detecting if change originated from InMemoryFileSystem or external
                // 2. Updating InMemoryFileSystem if external change
                // 3. Updating macOS filesystem if internal change
                // 4. Handling conflicts and merge scenarios

                LOGGER.debug("Handling bidirectional sync for {}: {}", operation, path);

            } catch (Exception e) {
                LOGGER.error("Error in bidirectional sync for path: {}", path, e);
            }
        }

        /**
         * Determines if a file access should trigger lazy loading.
         *
         * @param path the file path
         * @return true if lazy loading should be triggered
         */
        private boolean shouldTriggerLazyLoad(String path) {
            if (MacOSChangeWatcher.this.placeholderManager == null) return false;
            if (MacOSChangeWatcher.this.inMemoryFileSystem == null) return false;
            
            // Convert absolute path from FSEvents to relative path for PlaceholderManager
            String relativePath = MacOSChangeWatcher.this.inMemoryFileSystem.getRelativePath(path);
            if (relativePath == null) return false;

            return MacOSChangeWatcher.this.placeholderManager.isPlaceholder(relativePath) && 
                   !MacOSChangeWatcher.this.placeholderManager.isMaterialized(relativePath);
        }
    }
}
