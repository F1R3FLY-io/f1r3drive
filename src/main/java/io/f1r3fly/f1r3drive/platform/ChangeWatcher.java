package io.f1r3fly.f1r3drive.platform;

/**
 * Main interface for platform-specific file monitoring.
 * Abstracts differences between macOS FSEvents, Linux inotify, and Windows ReadDirectoryChangesW.
 * Provides unified API across all platforms.
 */
public interface ChangeWatcher {

    /**
     * Starts monitoring the specified path for file system changes.
     *
     * @param path the path to monitor
     * @param listener the listener to notify of changes
     * @throws Exception if monitoring cannot be started
     */
    void startMonitoring(String path, ChangeListener listener) throws Exception;

    /**
     * Stops monitoring file system changes.
     */
    void stopMonitoring();

    /**
     * Checks if the watcher is currently active and monitoring.
     *
     * @return true if monitoring is active, false otherwise
     */
    boolean isMonitoring();

    /**
     * Gets the platform-specific information for this watcher.
     *
     * @return platform information
     */
    PlatformInfo getPlatformInfo();

    /**
     * Performs cleanup of any native resources or background threads.
     * This method should be called before the application shuts down.
     */
    void cleanup();

    /**
     * Registers a callback for on-demand file loading from blockchain.
     *
     * @param callback the callback to handle file loading requests
     */
    void setFileChangeCallback(FileChangeCallback callback);
}
