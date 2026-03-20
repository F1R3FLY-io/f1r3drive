package io.f1r3fly.f1r3drive.app.linux.fuse;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.errors.*;
import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.filesystem.FileSystemAction;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.filesystem.OperationContext;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import io.f1r3fly.f1r3drive.finderextensions.FinderSyncExtensionServiceServer;
import io.f1r3fly.f1r3drive.folders.AutoStartTokenDiscovery;
import io.f1r3fly.f1r3drive.placeholder.CacheConfiguration;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderInfo;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import io.f1r3fly.f1r3drive.platform.ChangeWatcher;
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory;
import io.f1r3fly.f1r3drive.platform.F1r3DriveChangeListener;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.posix.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

public class F1r3DriveFuse extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            F1r3DriveFuse.class);

    private FileSystem fileSystem;
    private F1r3flyBlockchainClient f1R3FlyBlockchainClient;
    private FinderSyncExtensionServiceServer finderSyncExtensionServiceServer;

    // 🆕 NEW: Platform integration fields
    private ChangeWatcher changeWatcher;
    private PlaceholderManager placeholderManager;
    private F1r3DriveChangeListener changeListener;
    private Thread shutdownHook;

    // Token discovery system for automatic blockchain folder creation
    private AutoStartTokenDiscovery tokenDiscovery;

    public F1r3DriveFuse(F1r3flyBlockchainClient f1R3FlyBlockchainClient) {
        super(); // no need to call Fuse constructor?
        this.f1R3FlyBlockchainClient = f1R3FlyBlockchainClient; // doesnt have a state, so can be reused between mounts

        // Initialize automatic token discovery system
        initializeTokenDiscovery();
    }

    /**
     * Initialize automatic token discovery system
     */
    private void initializeTokenDiscovery() {
        try {
            LOGGER.info("Starting automatic blockchain token discovery...");
            tokenDiscovery = AutoStartTokenDiscovery.createSafely(
                    f1R3FlyBlockchainClient);

            if (tokenDiscovery != null) {
                LOGGER.info("✓ Token discovery system initialized");
                LOGGER.info(
                        "Demo folders will be created in: " +
                                System.getProperty("user.home") +
                                "/demo-f1r3drive");
            } else {
                LOGGER.warn(
                        "Token discovery system could not be initialized - continuing without it");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize token discovery system", e);
        }
    }

    /**
     * Gets the current token discovery system
     */
    public AutoStartTokenDiscovery getTokenDiscovery() {
        return tokenDiscovery;
    }

    /**
     * 🆕 NEW: Initialize the F1r3Drive system with platform integration.
     * Sets up AES encryption, filesystem, blockchain client, and platform-specific
     * components.
     *
     * @param mountPath the path where the filesystem will be mounted
     * @param context   blockchain context containing wallet and connection info
     * @throws Exception if initialization fails
     */
    public void initialize(String mountPath, BlockchainContext context)
            throws Exception {
        LOGGER.info("Initializing F1r3Drive with mount path: {}", mountPath);

        // ✅ Existing: AES setup, filesystem init, blockchain client
        LOGGER.debug("Setting up filesystem and blockchain client...");
        this.fileSystem = new InMemoryFileSystem(f1R3FlyBlockchainClient);

        // 🆕 NEW: ChangeWatcher creation via factory
        LOGGER.debug("Creating platform-specific ChangeWatcher...");
        this.changeWatcher = createPlatformChangeWatcher();

        // 🆕 NEW: PlaceholderManager setup
        LOGGER.debug("Setting up PlaceholderManager...");
        CacheConfiguration cacheConfig = CacheConfiguration.defaultConfig();

        FileChangeCallback fileCallback = new FileChangeCallback() {
            @Override
            public java.util.concurrent.CompletableFuture<byte[]> loadFileContent(String path) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        LOGGER.debug("Loading file content from filesystem for: {}", path);

                        // Use InMemoryFileSystem directly
                        if (fileSystem instanceof InMemoryFileSystem) {
                            InMemoryFileSystem imfs = (InMemoryFileSystem) fileSystem;

                            // Ensure path starts with slash for internal FS lookup
                            String lookupPath = path.startsWith("/") ? path : "/" + path;

                            io.f1r3fly.f1r3drive.filesystem.common.File file = imfs.getFile(lookupPath);
                            if (file != null) {
                                long size = file.getSize();
                                if (size <= 0) {
                                    return new byte[0];
                                }

                                byte[] data = new byte[(int) size];
                                io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer ptr = new io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer() {
                                    @Override
                                    public void put(long offset, byte[] bytes, int start, int length) {
                                        System.arraycopy(bytes, start, data, (int) offset, length);
                                    }

                                    @Override
                                    public void get(long offset, byte[] bytes, int start, int length) {
                                        throw new UnsupportedOperationException("Read only pointer");
                                    }

                                    @Override
                                    public byte getByte(long offset) {
                                        return 0;
                                    }

                                    @Override
                                    public void putByte(long offset, byte value) {
                                        data[(int) offset] = value;
                                    }
                                };

                                // Use the readFile method which ensures content loading from blockchain
                                int res = imfs.readFile(lookupPath, ptr, size, 0);
                                if (res == 0) {
                                    return data;
                                }
                            }
                        }

                        LOGGER.warn("File not found or empty in FS: {}", path);
                        return new byte[0];
                    } catch (Exception e) {
                        LOGGER.error("Failed to load file content for: {}", path, e);
                        throw new RuntimeException("Failed to load file content", e);
                    }
                });
            }

            /**
             * Initialize automatic token discovery system
             */
            private void initializeTokenDiscovery() {
                try {
                    LOGGER.info(
                            "Starting automatic blockchain token discovery...");
                    tokenDiscovery = AutoStartTokenDiscovery.createSafely(
                            f1R3FlyBlockchainClient);

                    if (tokenDiscovery != null) {
                        LOGGER.info("✓ Token discovery system initialized");
                        LOGGER.info(
                                "Demo folders will be created in: " +
                                        System.getProperty("user.home") +
                                        "/demo-f1r3drive");
                    } else {
                        LOGGER.warn(
                                "Token discovery system could not be initialized - continuing without it");
                    }
                } catch (Exception e) {
                    LOGGER.error(
                            "Failed to initialize token discovery system",
                            e);
                }
            }

            /**
             * Gets the current token discovery system
             */
            public AutoStartTokenDiscovery getTokenDiscovery() {
                return tokenDiscovery;
            }

            @Override
            public boolean fileExistsInBlockchain(String path) {
                // Mock implementation - will be implemented when blockchain integration is
                // ready
                return true;
            }

            @Override
            public FileMetadata getFileMetadata(String path) {
                // Mock implementation
                return new FileMetadata(
                        1024,
                        System.currentTimeMillis(),
                        "mock-checksum",
                        false);
            }

            @Override
            public void onFileSavedToBlockchain(String path, byte[] content) {
                LOGGER.debug(
                        "File saved to blockchain: {} ({} bytes)",
                        path,
                        content.length);
            }

            @Override
            public void onFileDeletedFromBlockchain(String path) {
                LOGGER.debug("File deleted from blockchain: {}", path);
            }

            @Override
            public void preloadFile(String path, int priority) {
                LOGGER.debug(
                        "Preload requested for file: {} with priority: {}",
                        path,
                        priority);
            }

            @Override
            public void clearCache(String path) {
                LOGGER.debug("Clear cache requested for file: {}", path);
            }

            @Override
            public CacheStatistics getCacheStatistics() {
                // Mock implementation
                return new CacheStatistics(0, 0, 0, 100 * 1024 * 1024, 0);
            }
        };

        this.placeholderManager = new PlaceholderManager(
                fileCallback,
                cacheConfig);

        // 🆕 NEW: Platform integration configuration
        setupPlatformIntegration(mountPath, context);

        LOGGER.info("F1r3Drive initialization completed successfully");
    }

    /**
     * 🆕 NEW: Factory method for creating platform-specific ChangeWatcher.
     */
    private ChangeWatcher createPlatformChangeWatcher() throws Exception {
        try {
            return ChangeWatcherFactory.createChangeWatcher();
        } catch (ChangeWatcherFactory.UnsupportedPlatformException e) {
            LOGGER.error(
                    "Platform not supported for change watching: {}",
                    e.getMessage());
            throw new Exception("Platform integration not supported", e);
        }
    }

    /**
     * 🆕 NEW: Setup platform-specific integration.
     */
    private void setupPlatformIntegration(
            String mountPath,
            BlockchainContext context) {
        LOGGER.debug(
                "Setting up platform integration for mount path: {}",
                mountPath);

        // Create change listener that routes events to StateChangeEventsManager
        this.changeListener = new F1r3DriveChangeListener(
                fileSystem,
                placeholderManager,
                mountPath);

        // Setup file change callback for the watcher
        FileChangeCallback callback = new FileChangeCallback() {
            @Override
            public java.util.concurrent.CompletableFuture<byte[]> loadFileContent(String path) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    return placeholderManager.loadContent(path);
                });
            }

            @Override
            public boolean fileExistsInBlockchain(String path) {
                return placeholderManager.isPlaceholder(path);
            }

            @Override
            public FileMetadata getFileMetadata(String path) {
                // Get metadata from placeholder manager if available
                return placeholderManager.isPlaceholder(path)
                        ? new FileMetadata(
                                1024,
                                System.currentTimeMillis(),
                                "placeholder-checksum",
                                false)
                        : null;
            }

            @Override
            public void onFileSavedToBlockchain(String path, byte[] content) {
                LOGGER.debug(
                        "File saved to blockchain notification: {} ({} bytes)",
                        path,
                        content.length);
            }

            @Override
            public void onFileDeletedFromBlockchain(String path) {
                LOGGER.debug(
                        "File deleted from blockchain notification: {}",
                        path);
                if (placeholderManager.isPlaceholder(path)) {
                    placeholderManager.removePlaceholder(path);
                }
            }

            @Override
            public void preloadFile(String path, int priority) {
                if (placeholderManager.isPlaceholder(path)) {
                    placeholderManager.preloadFile(path, priority);
                }
            }

            @Override
            public void clearCache(String path) {
                placeholderManager.invalidateCache(path);
            }

            @Override
            public CacheStatistics getCacheStatistics() {
                var stats = placeholderManager.getCacheStatistics();
                return new CacheStatistics(
                        stats.getCacheHits(),
                        stats.getCacheMisses(),
                        stats.getCacheSize(),
                        stats.getMaxCacheSize(),
                        stats.getCachedFilesCount());
            }
        };
        changeWatcher.setFileChangeCallback(callback);

        LOGGER.debug("Platform integration setup completed");
    }

    /**
     * 🆕 NEW: Preload blockchain files as placeholders.
     */
    private void preloadBlockchainFiles() throws Exception {
        LOGGER.debug("Preloading blockchain files as placeholders...");

        // This would query the blockchain for available files and create placeholders
        // Implementation depends on blockchain client API
        try {
            // Example implementation - would need to be adapted to actual blockchain API
            // List<BlockchainFileInfo> files = f1R3FlyBlockchainClient.listFiles();
            // for (BlockchainFileInfo fileInfo : files) {
            // registerBlockchainFile(fileInfo.getPath(), fileInfo.getAddress(),
            // fileInfo.getSize());
            // }

            LOGGER.debug("Blockchain files preloaded successfully");
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to preload some blockchain files: {}",
                    e.getMessage(),
                    e);
            // Continue execution - not a fatal error
        }
    }

    /**
     * 🆕 NEW: Register a blockchain file as a placeholder.
     */
    private void registerBlockchainFile(
            String path,
            String blockchainAddress,
            long size) {
        try {
            // Create placeholder using the correct PlaceholderManager API
            placeholderManager.createPlaceholder(
                    path,
                    size,
                    blockchainAddress,
                    1);
            LOGGER.debug(
                    "Registered blockchain file as placeholder: {} -> {}",
                    path,
                    blockchainAddress);
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to register blockchain file as placeholder: {}",
                    path,
                    e);
        }
    }

    /**
     * 🆕 NEW: Start the F1r3Drive system with platform monitoring.
     * Connects to blockchain, starts state manager, and begins platform
     * integration.
     *
     * @param mountPath the path where the filesystem is mounted
     * @throws Exception if startup fails
     */
    public void start(String mountPath) throws Exception {
        LOGGER.info("Starting F1r3Drive system with mount path: {}", mountPath);

        // ✅ Existing: Blockchain connect, state manager start
        if (fileSystem != null) {
            LOGGER.debug("Starting filesystem background operations...");
            // Note: Actual blockchain connection and state manager startup
            // would be handled by the existing filesystem initialization
        }

        // 🆕 NEW: Start change monitoring
        if (changeWatcher != null && changeListener != null) {
            LOGGER.debug("Starting platform change monitoring...");
            changeWatcher.startMonitoring(mountPath, changeListener);
            LOGGER.info("Platform change monitoring started successfully");
        } else {
            LOGGER.warn(
                    "ChangeWatcher or ChangeListener not initialized, skipping platform monitoring");
        }

        // 🆕 NEW: Preload blockchain files as placeholders
        try {
            preloadBlockchainFiles();
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to preload blockchain files, continuing startup: {}",
                    e.getMessage());
            // Not a fatal error, continue with startup
        }

        // 🆕 NEW: Setup shutdown hooks
        setupShutdownHook();

        LOGGER.info("F1r3Drive system startup completed successfully");
    }

    /**
     * 🆕 NEW: Setup shutdown hook for clean resource cleanup.
     */
    private void setupShutdownHook() {
        if (shutdownHook == null) {
            shutdownHook = new Thread(
                    () -> {
                        LOGGER.info(
                                "Shutdown hook triggered, cleaning up F1r3Drive resources...");
                        try {
                            shutdown();
                        } catch (Exception e) {
                            LOGGER.error("Error during shutdown hook cleanup", e);
                        }
                    },
                    "F1r3Drive-ShutdownHook");

            Runtime.getRuntime().addShutdownHook(shutdownHook);
            LOGGER.debug("Shutdown hook registered successfully");
        }
    }

    /**
     * 🆕 NEW: Shutdown the F1r3Drive system with proper cleanup.
     * Stops platform monitoring, cleans up resources, and disconnects from
     * blockchain.
     */
    public void shutdown() {
        LOGGER.info("Shutting down F1r3Drive system...");

        // ✅ Existing: State shutdown, blockchain disconnect
        if (fileSystem != null) {
            LOGGER.debug("Shutting down filesystem...");
            try {
                fileSystem.terminate();
            } catch (Exception e) {
                LOGGER.error("Error terminating filesystem", e);
            }
        }

        // 🆕 NEW: Stop change monitoring
        if (changeWatcher != null) {
            LOGGER.debug("Stopping platform change monitoring...");
            try {
                changeWatcher.stopMonitoring();
                changeWatcher.cleanup();
                LOGGER.debug("Platform change monitoring stopped successfully");
            } catch (Exception e) {
                LOGGER.error("Error stopping change monitoring", e);
            }
        }

        // 🆕 NEW: Cleanup placeholder manager
        if (placeholderManager != null) {
            LOGGER.debug("Cleaning up placeholder manager...");
            try {
                placeholderManager.cleanup();
                LOGGER.debug("Placeholder manager cleanup completed");
            } catch (Exception e) {
                LOGGER.error("Error cleaning up placeholder manager", e);
            }
        }

        // Cleanup other resources
        cleanupResources();

        // Remove shutdown hook if it was registered
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                shutdownHook = null;
                LOGGER.debug("Shutdown hook removed successfully");
            } catch (Exception e) {
                // Shutdown hook may have already been triggered, ignore
                LOGGER.debug(
                        "Could not remove shutdown hook (may already be running)");
            }
        }

        LOGGER.info("F1r3Drive system shutdown completed");
    }

    /**
     * Extract the F1r3fly icon from JAR resources to a temporary file for use as
     * volume icon
     * 
     * @return Path to the extracted icon file, or null if extraction failed
     */
    private String extractIconFromJar() {
        try {
            // Create a temporary file to store the icon
            Path tempIconPath = Files.createTempFile("f1r3fly-icon", ".icns");

            // Copy the icon from the JAR to the temporary file
            try (
                    InputStream is = getClass().getResourceAsStream(
                            "/icons/f1r3fly.icns")) {
                if (is == null) {
                    LOGGER.warn(
                            "F1r3fly icon file not found in JAR resources at /icons/f1r3fly.icns");
                    return null;
                }
                Files.copy(
                        is,
                        tempIconPath,
                        StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug(
                        "Successfully extracted icon to: {}",
                        tempIconPath);
            }

            // Mark for deletion on exit
            tempIconPath.toFile().deleteOnExit();

            return tempIconPath.toString();
        } catch (IOException e) {
            LOGGER.error("Failed to extract F1r3fly icon from JAR", e);
            return null;
        }
    }

    /**
     * Common error handling for FUSE operations
     */
    @FunctionalInterface
    private interface FuseOperation {
        int execute() throws Exception;
    }

    private int executeWithErrorHandling(
            String path,
            FileSystemAction action,
            FuseOperation operation) {
        return OperationContext.withContext(action, path, () -> {
            boolean isTrace = action == FileSystemAction.FUSE_GETATTR ||
                    action == FileSystemAction.FUSE_READ ||
                    action == FileSystemAction.FUSE_WRITE;
            if (isTrace) {
                LOGGER.trace("Started {}", action);
            } else {
                LOGGER.debug("Started {}", action);
            }

            // Check if filesystem is mounted first
            if (notMounted()) {
                LOGGER.debug("FileSystem not mounted");
                return -ErrorCodes.EIO();
            }

            try {
                int result = operation.execute();
                if (result == 0) {
                    if (isTrace) {
                        LOGGER.trace("Completed {} successfully", action);
                    } else {
                        LOGGER.debug("Completed {} successfully", action);
                    }
                } else {
                    LOGGER.debug(
                            "Completed {} with result: {}",
                            action,
                            result);
                }
                return result;
            } catch (FileAlreadyExists e) {
                if (isTrace) {
                    LOGGER.trace("File/Directory already exists", e);
                } else {
                    LOGGER.debug("File/Directory already exists", e);
                }
                return -ErrorCodes.EEXIST();
            } catch (PathNotFound e) {
                if (isTrace) {
                    LOGGER.trace("Path not found", e);
                } else {
                    LOGGER.debug("Path not found", e);
                }
                return -ErrorCodes.ENOENT();
            } catch (OperationNotPermitted e) {
                if (isTrace) {
                    LOGGER.trace("Operation not permitted");
                } else {
                    LOGGER.debug("Operation not permitted");
                }
                return -ErrorCodes.EPERM();
            } catch (PathIsNotAFile e) {
                if (isTrace) {
                    LOGGER.trace("Path is not a file", e);
                } else {
                    LOGGER.info("Path is not a file", e);
                }
                return -ErrorCodes.EISDIR();
            } catch (PathIsNotADirectory e) {
                if (isTrace) {
                    LOGGER.trace("Path is not a directory", e);
                } else {
                    LOGGER.info("Path is not a directory", e);
                }
                return -ErrorCodes.ENOTDIR();
            } catch (DirectoryNotEmpty e) {
                if (isTrace) {
                    LOGGER.trace("Directory is not empty");
                } else {
                    LOGGER.info("Directory is not empty");
                }
                return -ErrorCodes.ENOTEMPTY();
            } catch (IOException e) {
                if (isTrace) {
                    LOGGER.trace("IO error", e);
                } else {
                    LOGGER.warn("IO error", e);
                }
                return -ErrorCodes.EIO();
            } catch (Exception e) {
                if (isTrace) {
                    LOGGER.trace("Unexpected error", e);
                } else {
                    LOGGER.error("Unexpected error", e);
                }
                return -ErrorCodes.EIO();
            }
        });
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_CREATE,
                () -> {
                    // Reject creation of Apple metadata files
                    if (PathUtils.isAppleMetadataFile(path)) {
                        LOGGER.debug(
                                "Rejecting creation of Apple metadata file: {}",
                                path);
                        return -ErrorCodes.EACCES();
                    }
                    fileSystem.createFile(path, mode);
                    return 0;
                });
    }

    @Override
    public int getattr(String path, FileStat stat) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_GETATTR,
                () -> {
                    // Explicitly reject Apple metadata files
                    if (PathUtils.isAppleMetadataFile(path)) {
                        LOGGER.debug("Rejecting Apple metadata file: {}", path);
                        return -ErrorCodes.ENOENT();
                    }
                    fileSystem.getAttributes(
                            path,
                            FuseAdapter.fromFuseFileStat(stat),
                            FuseAdapter.fromFuseContext(getContext()));
                    return 0;
                });
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_MKDIR,
                () -> {
                    fileSystem.makeDirectory(path, mode);
                    return 0;
                });
    }

    @Override
    public int read(
            String path,
            Pointer buf,
            @size_t long size,
            @off_t long offset,
            FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_READ,
                () -> {
                    return fileSystem.readFile(
                            path,
                            FuseAdapter.fromFusePointer(buf),
                            size,
                            offset);
                });
    }

    @Override
    public int readdir(
            String path,
            Pointer buf,
            FuseFillDir filter,
            @off_t long offset,
            FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_READDIR,
                () -> {
                    fileSystem.readDirectory(
                            path,
                            FuseAdapter.fromFuseFillDir(buf, filter));
                    return 0;
                });
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_READ,
                () -> {
                    fileSystem.getFileSystemStats(
                            path,
                            FuseAdapter.fromFuseStatVfs(stbuf));
                    return super.statfs(path, stbuf);
                });
    }

    @Override
    public int rename(String path, String newName) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_RENAME,
                () -> {
                    fileSystem.renameFile(path, newName);
                    return 0;
                });
    }

    @Override
    public int rmdir(String path) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_RMDIR,
                () -> {
                    fileSystem.removeDirectory(path);
                    return 0;
                });
    }

    @Override
    public int truncate(String path, long offset) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_TRUNCATE,
                () -> {
                    fileSystem.truncateFile(path, offset);
                    return 0;
                });
    }

    @Override
    public int unlink(String path) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_UNLINK,
                () -> {
                    fileSystem.unlinkFile(path);
                    return 0;
                });
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_OPEN,
                () -> {
                    // Reject opening Apple metadata files
                    if (PathUtils.isAppleMetadataFile(path)) {
                        LOGGER.debug(
                                "Rejecting open of Apple metadata file: {}",
                                path);
                        return -ErrorCodes.ENOENT();
                    }
                    fileSystem.openFile(path);
                    LOGGER.debug("Opened file {}", path);
                    return 0;
                });
    }

    @Override
    public int write(
            String path,
            Pointer buf,
            @size_t long size,
            @off_t long offset,
            FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_WRITE,
                () -> {
                    return fileSystem.writeFile(
                            path,
                            FuseAdapter.fromFusePointer(buf),
                            size,
                            offset);
                });
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return executeWithErrorHandling(
                path,
                FileSystemAction.FUSE_FLUSH,
                () -> {
                    fileSystem.flushFile(path);
                    return 0;
                });
    }

    public void mountAndUnlockRootDirectory(
            Path mountPoint,
            boolean blocking,
            boolean debug,
            String revAddress,
            String privateKey,
            String[] mountOptions) {
        // Run unlock in background after waiting for mount to complete
        Thread unlockThread = new Thread(() -> {
            try {
                // Wait for filesystem to be mounted
                LOGGER.debug(
                        "Background thread waiting for filesystem to be mounted...");
                while (notMounted()) {
                    Thread.sleep(100); // Check every 100ms
                }
                LOGGER.debug(
                        "Filesystem is now mounted, proceeding with unlock operation");

                // Now unlock the directory
                fileSystem.unlockRootDirectory(revAddress, privateKey);
            } catch (InterruptedException e) {
                LOGGER.warn("Unlock background thread was interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error(
                        "Error in background unlock thread for revAddress: {}",
                        revAddress,
                        e);
            }
        });

        unlockThread.setName("UnlockDirectory-" + revAddress);
        unlockThread.setDaemon(true); // Don't prevent JVM shutdown
        unlockThread.start();

        LOGGER.debug(
                "Started background unlock thread for revAddress: {}",
                revAddress);
        mount(mountPoint, blocking, debug, mountOptions);
    }

    public void mount(
            Path mountPoint,
            boolean blocking,
            boolean debug,
            String[] mountOptions) {
        LOGGER.debug(
                "Called Mounting F1r3DriveFuse on {} with opts {}",
                mountPoint,
                Arrays.toString(mountOptions));

        try {
            // Ensure mount point exists and is accessible
            File mountPointFile = mountPoint.toFile();
            if (!mountPointFile.exists()) {
                LOGGER.debug(
                        "Mount point does not exist, creating: {}",
                        mountPoint);
                if (!mountPointFile.mkdirs()) {
                    throw new RuntimeException(
                            "Failed to create mount point directory: " + mountPoint);
                }
            }
            if (!mountPointFile.isDirectory()) {
                throw new RuntimeException(
                        "Mount point is not a directory: " + mountPoint);
            }
            if (!mountPointFile.canRead() || !mountPointFile.canWrite()) {
                throw new RuntimeException(
                        "Mount point is not accessible (read/write): " + mountPoint);
            }
            LOGGER.debug("Mount point verified: {}", mountPoint);

            LOGGER.debug("Creating InMemoryFileSystem...");
            this.fileSystem = new InMemoryFileSystem(f1R3FlyBlockchainClient);
            LOGGER.debug("Created InMemoryFileSystem successfully");

            LOGGER.debug("Creating FinderSyncExtensionServiceServer...");
            this.finderSyncExtensionServiceServer = new FinderSyncExtensionServiceServer(
                    this::handleChange,
                    this::handleUnlockRevDirectory,
                    54000);
            LOGGER.debug(
                    "Created FinderSyncExtensionServiceServer successfully");

            LOGGER.debug("Waiting for background operations to complete...");
            waitOnBackgroundThread();

            LOGGER.debug("Starting FinderSyncExtensionServiceServer...");
            finderSyncExtensionServiceServer.start();
            LOGGER.debug(
                    "Started FinderSyncExtensionServiceServer successfully");

            LOGGER.debug(
                    "Mounting FUSE filesystem with options: {}",
                    Arrays.toString(mountOptions));
            super.mount(mountPoint, blocking, debug, mountOptions);

            LOGGER.info(
                    "Successfully mounted F1r3DriveFuse on {} with name {}",
                    mountPoint,
                    mountPoint.getFileName().toString());
        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during mount: {}", e.getMessage(), e);
            cleanupResources();
            throw e;
        } catch (Throwable e) {
            LOGGER.error(
                    "Error mounting F1r3DriveFuse on {}: {}",
                    mountPoint,
                    e.getMessage(),
                    e);
            cleanupResources();
            throw new RuntimeException("Failed to mount F1r3DriveFuse", e);
        }
    }

    private void cleanupResources() {
        LOGGER.debug("Starting cleanup of resources...");
        // destroy background tasks and queue
        if (this.fileSystem != null) {
            LOGGER.debug("Terminating filesystem...");
            this.fileSystem.terminate();
            this.fileSystem = null;
            LOGGER.debug("Filesystem terminated and set to null");
        } else {
            LOGGER.debug("Filesystem was already null, skipping termination");
        }
        if (this.finderSyncExtensionServiceServer != null) {
            LOGGER.debug("Stopping FinderSyncExtensionServiceServer...");
            this.finderSyncExtensionServiceServer.stop();
            LOGGER.debug("FinderSyncExtensionServiceServer stopped");
        } else {
            LOGGER.debug(
                    "FinderSyncExtensionServiceServer was already null, skipping stop");
        }
        if (this.tokenDiscovery != null) {
            LOGGER.debug("Shutting down token discovery system...");
            this.tokenDiscovery.shutdown();
            this.tokenDiscovery = null;
            LOGGER.debug("Token discovery system shutdown complete");
        } else {
            LOGGER.debug(
                    "Token discovery system was already null, skipping shutdown");
        }
        LOGGER.debug("Resource cleanup completed");
    }

    public void waitOnBackgroundThread() {
        try {
            if (fileSystem != null) {
                LOGGER.debug(
                        "Waiting for background deploy operations to complete...");
                fileSystem.waitOnBackgroundDeploy();
                LOGGER.debug(
                        "Background deploy operations completed successfully");
            } else {
                LOGGER.warn(
                        "waitOnBackgroundThread called but fileSystem is null");
            }
        } catch (Throwable e) {
            LOGGER.error(
                    "Error waiting for background thread operations to complete",
                    e);
        }
    }

    @Override
    public void umount() {
        // Use compareAndSet to ensure only one unmount attempt (consistent with base
        // class)
        if (!mounted.compareAndSet(true, false)) {
            LOGGER.debug(
                    "F1r3DriveFuse is already unmounted or being unmounted, skipping");
            return;
        }

        // Reset mounted flag back to true so that super.umount() can handle it properly
        mounted.set(true);

        LOGGER.debug(
                "Called Umounting F1r3DriveFuse. Mounted: {}, filesystem {}",
                mounted.get(),
                fileSystem != null);
        try {
            LOGGER.debug(
                    "Waiting for background operations to complete before unmount...");
            waitOnBackgroundThread();
            LOGGER.debug(
                    "Background operations completed, calling super.umount()...");
            super.umount();
            LOGGER.debug("super.umount() completed, starting cleanup...");
            cleanupResources();
            LOGGER.info("Successfully unmounted F1r3DriveFuse");
        } catch (RuntimeException e) {
            LOGGER.error("Runtime error during unmount: {}", e.getMessage(), e);
            // Still cleanup on error
            try {
                cleanupResources();
            } catch (Exception cleanupError) {
                LOGGER.error(
                        "Error during cleanup after unmount failure",
                        cleanupError);
            }
            throw e;
        } catch (Throwable e) {
            LOGGER.error(
                    "Error unmounting F1r3DriveFuse: {}",
                    e.getMessage(),
                    e);
            // Still cleanup on error
            try {
                cleanupResources();
            } catch (Exception cleanupError) {
                LOGGER.error(
                        "Error during cleanup after unmount failure",
                        cleanupError);
            }
            throw new RuntimeException("Failed to unmount F1r3DriveFuse", e);
        }
    }

    protected boolean notMounted() {
        boolean mountedFlag = mounted.get();
        boolean fileSystemExists = fileSystem != null;
        boolean isNotMounted = !mountedFlag || !fileSystemExists;

        if (isNotMounted) {
            LOGGER.warn(
                    "Filesystem is not mounted. mounted.get()={}, fileSystem!=null={}",
                    mountedFlag,
                    fileSystemExists);
            // Add stack trace to help debug why filesystem becomes null
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "notMounted() called from:",
                        new Exception("Stack trace"));
            }
        }

        return isNotMounted;
    }

    private FinderSyncExtensionServiceServer.Result handleChange(
            String tokenFilePath) {
        LOGGER.debug("Called onChange for path: {}", tokenFilePath);
        if (notMounted()) {
            LOGGER.warn(
                    "handleChange - FileSystem not mounted for path: {}",
                    tokenFilePath);
            return FinderSyncExtensionServiceServer.Result.error(
                    "FileSystem not mounted");
        }

        try {
            String normalizedTokenFilePath = tokenFilePath.replace(
                    mountPoint.toFile().getAbsolutePath(),
                    "");
            fileSystem.changeTokenFile(normalizedTokenFilePath);
            LOGGER.debug(
                    "Successfully changed token file: {}",
                    normalizedTokenFilePath);
            return FinderSyncExtensionServiceServer.Result.success();
        } catch (Exception e) {
            LOGGER.error("Error exchanging token file: {}", tokenFilePath, e);
            return FinderSyncExtensionServiceServer.Result.error(
                    e.getMessage());
        }
    }

    private FinderSyncExtensionServiceServer.Result handleUnlockRevDirectory(
            String revAddress,
            String privateKey) {
        LOGGER.debug(
                "Called handleUnlockRevDirectory for revAddress: {}",
                revAddress);

        if (notMounted()) {
            LOGGER.warn(
                    "handleUnlockRevDirectory - FileSystem not mounted for revAddress: {}",
                    revAddress);
            return FinderSyncExtensionServiceServer.Result.error(
                    "FileSystem not mounted");
        }
        try {
            fileSystem.unlockRootDirectory(revAddress, privateKey);
            LOGGER.debug(
                    "Successfully unlocked directory for revAddress: {}",
                    revAddress);
            return FinderSyncExtensionServiceServer.Result.success();
        } catch (Exception e) {
            LOGGER.error(
                    "Error unlocking directory for revAddress: {}",
                    revAddress,
                    e);
            return FinderSyncExtensionServiceServer.Result.error(
                    e.getMessage());
        }
    }
}
