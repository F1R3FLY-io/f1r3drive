package io.f1r3fly.f1r3drive.platform;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ChangeListener that routes platform file system events
 * to the appropriate components (FileSystem and PlaceholderManager).
 *
 * This class acts as a bridge between platform-specific file monitoring
 * and the F1r3Drive internal filesystem management.
 */
public class F1r3DriveChangeListener implements ChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        F1r3DriveChangeListener.class
    );

    private final FileSystem fileSystem;
    private final PlaceholderManager placeholderManager;

    /**
     * Creates a new F1r3DriveChangeListener.
     *
     * @param fileSystem the filesystem to notify of changes
     * @param placeholderManager the placeholder manager for lazy loading
     */
    public F1r3DriveChangeListener(
        FileSystem fileSystem,
        PlaceholderManager placeholderManager
    ) {
        this.fileSystem = fileSystem;
        this.placeholderManager = placeholderManager;
    }

    @Override
    public void onFileCreated(String path) {
        LOGGER.debug("File created: {}", path);
        try {
            // Check if this is a placeholder file that needs to be loaded
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.debug("Created file is a placeholder, triggering load: {}", path);
                placeholderManager.loadContent(path);
            }
            // Note: FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error("Error handling file creation: {}", path, e);
        }
    }

    @Override
    public void onFileModified(String path) {
        LOGGER.debug("File modified: {}", path);
        try {
            // If it's a placeholder file, it might need to be reloaded
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.debug("Modified file is a placeholder, invalidating cache: {}", path);
                placeholderManager.invalidateCache(path);
            }
            // FileSystem changes will be handled through FUSE operations
        } catch (Exception e) {
            LOGGER.error("Error handling file modification: {}", path, e);
        }
    }

    @Override
    public void onFileDeleted(String path) {
        LOGGER.debug("File deleted: {}", path);
        try {
            // Remove from placeholder manager if it was a placeholder
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.debug("Deleted file was a placeholder, cleaning up: {}", path);
                placeholderManager.removePlaceholder(path);
            }
            // FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error("Error handling file deletion: {}", path, e);
        }
    }

    @Override
    public void onFileMoved(String oldPath, String newPath) {
        LOGGER.debug("File moved: {} -> {}", oldPath, newPath);
        try {
            // Handle placeholder move
            if (placeholderManager.isPlaceholder(oldPath)) {
                LOGGER.debug("Moved file was a placeholder, updating: {} -> {}", oldPath, newPath);
                placeholderManager.movePlaceholder(oldPath, newPath);
            }
            // FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error("Error handling file move: {} -> {}", oldPath, newPath, e);
        }
    }

    @Override
    public void onFileAccessed(String path) {
        LOGGER.trace("File accessed: {}", path);
        try {
            // If it's a placeholder file, trigger loading
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.debug("Accessed file is a placeholder, ensuring loaded: {}", path);
                placeholderManager.ensureLoaded(path);
            }
        } catch (Exception e) {
            LOGGER.error("Error handling file access: {}", path, e);
        }
    }

    @Override
    public void onFileAttributesChanged(String path) {
        LOGGER.debug("File attributes changed: {}", path);
        // Most attribute changes don't require special handling for placeholders
        // FileSystem will be notified through normal FUSE operations
    }

    @Override
    public void onError(Exception error, String path) {
        if (path != null) {
            LOGGER.error("Platform monitoring error for path {}: {}", path, error.getMessage(), error);
        } else {
            LOGGER.error("Platform monitoring error: {}", error.getMessage(), error);
        }
    }

    @Override
    public void onMonitoringStarted(String watchedPath) {
        LOGGER.info("Platform monitoring started for path: {}", watchedPath);
    }

    @Override
    public void onMonitoringStopped(String watchedPath) {
        LOGGER.info("Platform monitoring stopped for path: {}", watchedPath);
    }
}
