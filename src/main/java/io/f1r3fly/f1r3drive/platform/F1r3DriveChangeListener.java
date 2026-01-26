package io.f1r3fly.f1r3drive.platform;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of ChangeListener that routes platform file system events
 * to the appropriate components (FileSystem and PlaceholderManager).
 *
 * This class acts as a bridge between platform-specific file monitoring
 * and the F1r3Drive internal filesystem management.
 */
public class F1r3DriveChangeListener implements ChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            F1r3DriveChangeListener.class);

    private final FileSystem fileSystem;
    private final PlaceholderManager placeholderManager;
    private final String mountPath;

    /**
     * Creates a new F1r3DriveChangeListener.
     *
     * @param fileSystem         the filesystem to notify of changes
     * @param placeholderManager the placeholder manager for lazy loading
     * @param mountPath          the root path of the mounted drive (for path
     *                           resolution)
     */
    public F1r3DriveChangeListener(
            FileSystem fileSystem,
            PlaceholderManager placeholderManager,
            String mountPath) {
        this.fileSystem = fileSystem;
        this.placeholderManager = placeholderManager;
        this.mountPath = mountPath;
    }

    /**
     * Resolves the relative path within the F1r3Drive filesystem.
     * Removes the mount path prefix.
     */
    private String resolveRelativePath(String absolutePath) {
        if (mountPath == null || absolutePath == null) {
            return absolutePath;
        }

        String cleanMountPath = mountPath.endsWith("/") ? mountPath.substring(0, mountPath.length() - 1) : mountPath;

        if (absolutePath.startsWith(cleanMountPath)) {
            String relative = absolutePath.substring(cleanMountPath.length());
            // Ensure leading slash for InMemoryFileSystem
            if (!relative.startsWith("/")) {
                relative = "/" + relative;
            }
            return relative;
        }
        return absolutePath;
    }

    /**
     * Checks if the file path should be ignored.
     * Use this to filter out system files, temporary files, and other noise.
     */
    private boolean shouldIgnore(String path) {
        if (path == null) {
            return true;
        }

        // Get just the filename
        String fileName = Paths.get(path).getFileName().toString();

        // System files
        if (fileName.equals(".DS_Store"))
            return true;
        if (fileName.equals(".Trash"))
            return true;
        if (fileName.equals(".Trashes"))
            return true;

        // AppleDouble / Resource fork files
        if (fileName.startsWith("._"))
            return true;

        // Sync temporary files (iCloud, etc.)
        if (fileName.contains(".nosync"))
            return true;

        // Office temporary files
        if (fileName.startsWith("~$"))
            return true;

        // Ignore .tokens directory and its contents
        if (path.contains("/.tokens/") || fileName.equals(".tokens"))
            return true;

        return false;
    }

    @Override
    public void onFileCreated(String path) {
        if (shouldIgnore(path)) {
            LOGGER.debug("CHANGE_LISTENER_IGNORE: Ignoring created file: {}", path);
            return;
        }

        LOGGER.info("CHANGE_LISTENER_CREATE: File created - {}", path);
        try {
            String relativePath = resolveRelativePath(path);

            // Check if this is a placeholder file that needs to be loaded
            if (placeholderManager.isPlaceholder(relativePath)) {
                LOGGER.info(
                        "CHANGE_LISTENER_PLACEHOLDER: Created file is a placeholder, triggering load: {}",
                        relativePath);
                placeholderManager.loadContent(relativePath);
                LOGGER.debug(
                        "CHANGE_LISTENER_LOAD_SUCCESS: Placeholder content loaded for: {}",
                        relativePath);
            } else {
                LOGGER.info(
                        "CHANGE_LISTENER_SYNC: New file created, sinking to blockchain: {}",
                        relativePath);

                // Determine if directory or file
                Path p = Paths.get(path);
                if (Files.isDirectory(p)) {
                    // mode 0755
                    fileSystem.makeDirectory(relativePath, 0755L);
                } else {
                    // Create file in FileSystem (queues deployment)
                    // mode 0644
                    fileSystem.createFile(relativePath, 0644L);
                    // Also write content if any
                    if (Files.size(p) > 0) {
                        byte[] content = Files.readAllBytes(p);
                        io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer ptr = wrapBytes(content);
                        fileSystem.writeFile(relativePath, ptr, content.length, 0);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(
                    "CHANGE_LISTENER_ERROR: Error handling file creation for {}: {}",
                    path,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onFileModified(String path) {
        if (shouldIgnore(path)) {
            LOGGER.debug("CHANGE_LISTENER_IGNORE: Ignoring modified file: {}", path);
            return;
        }

        LOGGER.info("CHANGE_LISTENER_MODIFY: File modified - {}", path);
        try {
            String relativePath = resolveRelativePath(path);

            // If it's a placeholder file, it might need to be reloaded
            if (placeholderManager.isPlaceholder(relativePath)) {
                LOGGER.info(
                        "CHANGE_LISTENER_CACHE_INVALIDATE: Modified file is a placeholder, invalidating cache: {}",
                        relativePath);
                placeholderManager.invalidateCache(relativePath);
            } else {
                LOGGER.info(
                        "CHANGE_LISTENER_SYNC: File modified, sinking to blockchain: {}",
                        relativePath);
                Path p = Paths.get(path);
                if (!Files.isDirectory(p)) {
                    byte[] content = Files.readAllBytes(p);
                    io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer ptr = wrapBytes(content);
                    fileSystem.writeFile(relativePath, ptr, content.length, 0);
                }
            }
        } catch (Exception e) {
            LOGGER.error(
                    "CHANGE_LISTENER_ERROR: Error handling file modification for {}: {}",
                    path,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onFileDeleted(String path) {
        if (shouldIgnore(path)) {
            LOGGER.debug("CHANGE_LISTENER_IGNORE: Ignoring deleted file: {}", path);
            return;
        }

        LOGGER.info("CHANGE_LISTENER_DELETE: File deleted - {}", path);
        try {
            String relativePath = resolveRelativePath(path);

            // Remove from placeholder manager if it was a placeholder
            if (placeholderManager.isPlaceholder(relativePath)) {
                LOGGER.info(
                        "CHANGE_LISTENER_PLACEHOLDER_CLEANUP: Deleted file was a placeholder, cleaning up: {}",
                        relativePath);
                placeholderManager.removePlaceholder(relativePath);
            } else {
                LOGGER.info(
                        "CHANGE_LISTENER_SYNC: File deleted, syncing to blockchain: {}",
                        relativePath);
                try {
                    fileSystem.unlinkFile(relativePath);
                } catch (Exception e) {
                    // Might be a directory
                    fileSystem.removeDirectory(relativePath);
                }
            }
        } catch (Exception e) {
            LOGGER.error(
                    "CHANGE_LISTENER_ERROR: Error handling file deletion for {}: {}",
                    path,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onFileMoved(String oldPath, String newPath) {
        // If either old or new path is ignored, we treat it with caution
        // But mainly if the NEW path is ignored, we shouldn't create it in blockchain
        // If OLD path was ignored, it likely wasn't in blockchain anyway

        if (shouldIgnore(newPath)) {
            LOGGER.debug("CHANGE_LISTENER_IGNORE: Ignoring move to ignored path: {}", newPath);
            return;
        }

        LOGGER.info(
                "CHANGE_LISTENER_MOVE: File moved - {} -> {}",
                oldPath,
                newPath);
        try {
            String oldRelative = resolveRelativePath(oldPath);
            String newRelative = resolveRelativePath(newPath);

            // Handle placeholder move
            if (placeholderManager.isPlaceholder(oldRelative)) {
                LOGGER.info(
                        "CHANGE_LISTENER_PLACEHOLDER_MOVE: Moved file was a placeholder, updating: {} -> {}",
                        oldRelative,
                        newRelative);
                placeholderManager.movePlaceholder(oldRelative, newRelative);
            } else {
                LOGGER.info(
                        "CHANGE_LISTENER_SYNC: File moved, syncing to blockchain: {} -> {}",
                        oldRelative,
                        newRelative);
                fileSystem.renameFile(oldRelative, newRelative);
            }
        } catch (Exception e) {
            LOGGER.error(
                    "CHANGE_LISTENER_ERROR: Error handling file move {} -> {}: {}",
                    oldPath,
                    newPath,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onFileAccessed(String path) {
        if (shouldIgnore(path)) {
            return;
        }

        LOGGER.debug("CHANGE_LISTENER_ACCESS: File accessed - {}", path);
        try {
            // If it's a placeholder file, trigger loading
            String relativePath = resolveRelativePath(path);
            if (placeholderManager.isPlaceholder(relativePath)) {
                LOGGER.info(
                        "CHANGE_LISTENER_LAZY_LOAD: Accessed file is a placeholder, ensuring loaded: {}",
                        relativePath);
                placeholderManager.ensureLoaded(relativePath);
            }
        } catch (Exception e) {
            LOGGER.error(
                    "CHANGE_LISTENER_ERROR: Error handling file access for {}: {}",
                    path,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onFileAttributesChanged(String path) {
        if (shouldIgnore(path)) {
            return;
        }

        LOGGER.debug(
                "CHANGE_LISTENER_ATTRIBUTES: File attributes changed - {}",
                path);
    }

    @Override
    public void onError(Exception error, String path) {
        if (path != null) {
            LOGGER.error(
                    "CHANGE_LISTENER_PLATFORM_ERROR: Platform monitoring error for path {}: {}",
                    path,
                    error.getMessage(),
                    error);
        } else {
            LOGGER.error(
                    "CHANGE_LISTENER_PLATFORM_ERROR: General platform monitoring error: {}",
                    error.getMessage(),
                    error);
        }
    }

    @Override
    public void onMonitoringStarted(String watchedPath) {
        LOGGER.info(
                "CHANGE_LISTENER_MONITORING_START: Platform monitoring started for path: {}",
                watchedPath);
        LOGGER.info(
                "CHANGE_LISTENER_READY: Change listener is now ready to process file system events");
    }

    @Override
    public void onMonitoringStopped(String watchedPath) {
        LOGGER.info(
                "CHANGE_LISTENER_MONITORING_STOP: Platform monitoring stopped for path: {}",
                watchedPath);
        LOGGER.info(
                "CHANGE_LISTENER_SHUTDOWN: Change listener monitoring has been shut down");
    }

    // Helper to wrap byte array in FSPointer
    private io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer wrapBytes(byte[] data) {
        return new io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer() {
            @Override
            public void put(long offset, byte[] bytes, int start, int length) {
                throw new UnsupportedOperationException("ReadOnly pointer");
            }

            @Override
            public void get(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(data, (int) offset, bytes, start, length);
            }

            @Override
            public byte getByte(long offset) {
                return data[(int) offset];
            }

            @Override
            public void putByte(long offset, byte value) {
                throw new UnsupportedOperationException("ReadOnly pointer");
            }
        };
    }
}
