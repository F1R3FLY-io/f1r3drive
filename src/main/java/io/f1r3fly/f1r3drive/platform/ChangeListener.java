package io.f1r3fly.f1r3drive.platform;

/**
 * File system event handler interface.
 * Receives notifications about file creation, modification, deletion
 * and routes events to StateChangeEventsManager.
 */
public interface ChangeListener {

    /**
     * Called when a file or directory is created.
     *
     * @param path the path of the created file or directory
     */
    void onFileCreated(String path);

    /**
     * Called when a file is modified.
     *
     * @param path the path of the modified file
     */
    void onFileModified(String path);

    /**
     * Called when a file or directory is deleted.
     *
     * @param path the path of the deleted file or directory
     */
    void onFileDeleted(String path);

    /**
     * Called when a file or directory is moved or renamed.
     *
     * @param oldPath the original path
     * @param newPath the new path
     */
    void onFileMoved(String oldPath, String newPath);

    /**
     * Called when a file is accessed (read).
     *
     * @param path the path of the accessed file
     */
    void onFileAccessed(String path);

    /**
     * Called when file attributes (permissions, metadata) are changed.
     *
     * @param path the path of the file with changed attributes
     */
    void onFileAttributesChanged(String path);

    /**
     * Called when monitoring encounters an error.
     *
     * @param error the error that occurred
     * @param path the path where the error occurred (may be null)
     */
    void onError(Exception error, String path);

    /**
     * Called when monitoring is started successfully.
     *
     * @param watchedPath the path being monitored
     */
    void onMonitoringStarted(String watchedPath);

    /**
     * Called when monitoring is stopped.
     *
     * @param watchedPath the path that was being monitored
     */
    void onMonitoringStopped(String watchedPath);
}
