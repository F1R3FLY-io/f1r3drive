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
        LOGGER.info("CHANGE_LISTENER_CREATE: File created - {}", path);
        try {
            // Check if this is a placeholder file that needs to be loaded
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.info(
                    "CHANGE_LISTENER_PLACEHOLDER: Created file is a placeholder, triggering load: {}",
                    path
                );
                placeholderManager.loadContent(path);
                LOGGER.debug(
                    "CHANGE_LISTENER_LOAD_SUCCESS: Placeholder content loaded for: {}",
                    path
                );
            } else {
                LOGGER.debug(
                    "CHANGE_LISTENER_REGULAR_FILE: Created file is not a placeholder: {}",
                    path
                );
            }
            // Note: FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error handling file creation for {}: {}",
                path,
                e.getMessage(),
                e
            );
        }
    }

    @Override
    public void onFileModified(String path) {
        LOGGER.info("CHANGE_LISTENER_MODIFY: File modified - {}", path);
        try {
            // If it's a placeholder file, it might need to be reloaded
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.info(
                    "CHANGE_LISTENER_CACHE_INVALIDATE: Modified file is a placeholder, invalidating cache: {}",
                    path
                );
                placeholderManager.invalidateCache(path);
                LOGGER.debug(
                    "CHANGE_LISTENER_CACHE_SUCCESS: Cache invalidated for placeholder: {}",
                    path
                );
            } else {
                LOGGER.debug(
                    "CHANGE_LISTENER_REGULAR_MODIFY: Modified file is not a placeholder: {}",
                    path
                );
            }
            // FileSystem changes will be handled through FUSE operations
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error handling file modification for {}: {}",
                path,
                e.getMessage(),
                e
            );
        }
    }

    @Override
    public void onFileDeleted(String path) {
        LOGGER.info("CHANGE_LISTENER_DELETE: File deleted - {}", path);
        try {
            // Remove from placeholder manager if it was a placeholder
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.info(
                    "CHANGE_LISTENER_PLACEHOLDER_CLEANUP: Deleted file was a placeholder, cleaning up: {}",
                    path
                );
                placeholderManager.removePlaceholder(path);
                LOGGER.debug(
                    "CHANGE_LISTENER_CLEANUP_SUCCESS: Placeholder removed for: {}",
                    path
                );
            } else {
                LOGGER.debug(
                    "CHANGE_LISTENER_REGULAR_DELETE: Deleted file was not a placeholder: {}",
                    path
                );
            }
            // FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error handling file deletion for {}: {}",
                path,
                e.getMessage(),
                e
            );
        }
    }

    @Override
    public void onFileMoved(String oldPath, String newPath) {
        LOGGER.info(
            "CHANGE_LISTENER_MOVE: File moved - {} -> {}",
            oldPath,
            newPath
        );
        try {
            // Handle placeholder move
            if (placeholderManager.isPlaceholder(oldPath)) {
                LOGGER.info(
                    "CHANGE_LISTENER_PLACEHOLDER_MOVE: Moved file was a placeholder, updating: {} -> {}",
                    oldPath,
                    newPath
                );
                placeholderManager.movePlaceholder(oldPath, newPath);
                LOGGER.debug(
                    "CHANGE_LISTENER_MOVE_SUCCESS: Placeholder moved successfully: {} -> {}",
                    oldPath,
                    newPath
                );
            } else {
                LOGGER.debug(
                    "CHANGE_LISTENER_REGULAR_MOVE: Moved file was not a placeholder: {} -> {}",
                    oldPath,
                    newPath
                );
            }
            // FileSystem will be notified through normal FUSE operations
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error handling file move {} -> {}: {}",
                oldPath,
                newPath,
                e.getMessage(),
                e
            );
        }
    }

    @Override
    public void onFileAccessed(String path) {
        LOGGER.debug("CHANGE_LISTENER_ACCESS: File accessed - {}", path);
        try {
            // If it's a placeholder file, trigger loading
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.info(
                    "CHANGE_LISTENER_LAZY_LOAD: Accessed file is a placeholder, ensuring loaded: {}",
                    path
                );
                placeholderManager.ensureLoaded(path);
                LOGGER.debug(
                    "CHANGE_LISTENER_LOAD_ENSURED: Placeholder loading ensured for: {}",
                    path
                );
            } else {
                LOGGER.trace(
                    "CHANGE_LISTENER_REGULAR_ACCESS: Accessed file is not a placeholder: {}",
                    path
                );
            }
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error handling file access for {}: {}",
                path,
                e.getMessage(),
                e
            );
        }
    }

    @Override
    public void onFileAttributesChanged(String path) {
        LOGGER.debug(
            "CHANGE_LISTENER_ATTRIBUTES: File attributes changed - {}",
            path
        );
        try {
            // Check if placeholder needs special attribute handling
            if (placeholderManager.isPlaceholder(path)) {
                LOGGER.debug(
                    "CHANGE_LISTENER_PLACEHOLDER_ATTRS: Placeholder file attributes changed: {}",
                    path
                );
            } else {
                LOGGER.trace(
                    "CHANGE_LISTENER_REGULAR_ATTRS: Regular file attributes changed: {}",
                    path
                );
            }
        } catch (Exception e) {
            LOGGER.error(
                "CHANGE_LISTENER_ERROR: Error checking placeholder status for attributes change {}: {}",
                path,
                e.getMessage(),
                e
            );
        }
        // Most attribute changes don't require special handling for placeholders
        // FileSystem will be notified through normal FUSE operations
    }

    @Override
    public void onError(Exception error, String path) {
        if (path != null) {
            LOGGER.error(
                "CHANGE_LISTENER_PLATFORM_ERROR: Platform monitoring error for path {}: {}",
                path,
                error.getMessage(),
                error
            );
        } else {
            LOGGER.error(
                "CHANGE_LISTENER_PLATFORM_ERROR: General platform monitoring error: {}",
                error.getMessage(),
                error
            );
        }
    }

    @Override
    public void onMonitoringStarted(String watchedPath) {
        LOGGER.info(
            "CHANGE_LISTENER_MONITORING_START: Platform monitoring started for path: {}",
            watchedPath
        );
        LOGGER.info(
            "CHANGE_LISTENER_READY: Change listener is now ready to process file system events"
        );
    }

    @Override
    public void onMonitoringStopped(String watchedPath) {
        LOGGER.info(
            "CHANGE_LISTENER_MONITORING_STOP: Platform monitoring stopped for path: {}",
            watchedPath
        );
        LOGGER.info(
            "CHANGE_LISTENER_SHUTDOWN: Change listener monitoring has been shut down"
        );
    }
}
