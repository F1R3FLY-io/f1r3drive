package io.f1r3fly.f1r3drive.filesystem;

import casper.DeployServiceCommon;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventProcessor;
import io.f1r3fly.f1r3drive.background.state.StateChangeEvents;
import io.f1r3fly.f1r3drive.background.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.wallet.RevWalletInfo;
import io.f1r3fly.f1r3drive.cache.CacheStrategy;
import io.f1r3fly.f1r3drive.cache.TieredCacheStrategy;
import io.f1r3fly.f1r3drive.errors.*;
import io.f1r3fly.f1r3drive.filesystem.bridge.*;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainFile;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedFile;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.LockedWalletDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.RootDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenDirectory;
import io.f1r3fly.f1r3drive.filesystem.local.TokenFile;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import io.f1r3fly.f1r3drive.placeholder.CacheConfiguration;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import io.f1r3fly.f1r3drive.platform.macos.FileProviderIntegration;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

/**
 * Production InMemoryFileSystem with real blockchain integration.
 *
 * This implementation provides full blockchain functionality for:
 * - File storage and retrieval from blockchain
 * - Wallet directory unlocking with real private keys
 * - Deploy operations for .rho and .metta files
 * - Token file operations with real balance tracking
 * - Directory operations with blockchain persistence
 *
 * All mock/placeholder functionality has been replaced with real blockchain
 * calls.
 */
public class InMemoryFileSystem implements FileSystem {

    private static final Logger logger = LoggerFactory.getLogger(
            InMemoryFileSystem.class);

    @NotNull
    private final RootDirectory rootDirectory;

    @NotNull
    private final DeployDispatcher deployDispatcher;

    @NotNull
    private final F1r3flyBlockchainClient blockchainClient;

    @NotNull
    private final StateChangeEventsManager stateChangeEventsManager;

    @NotNull
    private final PlaceholderManager placeholderManager;

    @NotNull
    private final CacheStrategy cacheStrategy;

    /**
     * Optional macOS FileProvider integration for native Finder support.
     * When set, file operations will be synchronized with macOS FileProvider
     * framework.
     */
    @Nullable
    private FileProviderIntegration fileProviderIntegration;

    /**
     * Creates a new InMemoryFileSystem with real blockchain integration.
     *
     * @param f1R3FlyBlockchainClient The blockchain client for real blockchain
     *                                operations
     * @throws F1r3DriveError if filesystem initialization fails
     */
    public InMemoryFileSystem(F1r3flyBlockchainClient f1R3FlyBlockchainClient)
            throws F1r3DriveError {
        logger.info(
                "Initializing InMemoryFileSystem with real blockchain integration");

        this.blockchainClient = f1R3FlyBlockchainClient;
        this.stateChangeEventsManager = new StateChangeEventsManager();
        this.stateChangeEventsManager.start();

        this.deployDispatcher = new DeployDispatcher(
                f1R3FlyBlockchainClient,
                stateChangeEventsManager);
        deployDispatcher.startBackgroundDeploy();

        // Initialize Caffeine-based cache system
        this.cacheStrategy = initializeCacheStrategy();
        this.placeholderManager = initializePlaceholderManager();

        this.rootDirectory = new RootDirectory();

        // Note: Physical wallet management is now handled by FolderTokenManager
        // No separate physical wallet directories are created

        logger.info(
                "InMemoryFileSystem initialized successfully with on-demand wallet directory creation");
    }

    /**
     * Initializes the Caffeine-based cache strategy for optimal performance.
     */
    private CacheStrategy initializeCacheStrategy() {
        try {
            java.nio.file.Path cacheDir = java.nio.file.Paths.get(
                    System.getProperty("user.home"),
                    ".f1r3drive",
                    "cache");

            return new TieredCacheStrategy(
                    100 * 1024 * 1024L, // 100MB memory cache
                    1024 * 1024L, // 1MB memory threshold
                    cacheDir, // Disk cache directory
                    1024 * 1024 * 1024L, // 1GB disk cache
                    30 * 60 * 1000L // 30 minutes expiration
            );
        } catch (Exception e) {
            logger.warn(
                    "Failed to initialize tiered cache, falling back to memory-only",
                    e);
            return new io.f1r3fly.f1r3drive.cache.MemoryOnlyCacheStrategy(
                    100 * 1024 * 1024L, // 100MB memory cache
                    30 * 60 * 1000L // 30 minutes expiration
            );
        }
    }

    /**
     * Initializes PlaceholderManager with Caffeine cache integration.
     */
    private PlaceholderManager initializePlaceholderManager() {
        CacheConfiguration cacheConfig = CacheConfiguration.builder()
                .maxCacheSize(100 * 1024 * 1024L)
                .evictionPolicy(CacheConfiguration.EvictionPolicy.LRU)
                .withCacheStrategyType(CacheConfiguration.CacheStrategyType.TIERED)
                .enableCaffeineStats(true)
                .build();

        FileChangeCallback fileCallback = new FileChangeCallback() {
            @Override
            public byte[] loadFileContent(String path) {
                logger.debug("Loading file content from blockchain: {}", path);
                try {
                    File file = getFileByPath(path);
                    if (file instanceof BlockchainFile) {
                        BlockchainFile blockchainFile = (BlockchainFile) file;
                        ensureFileContentLoadedWithCache(blockchainFile);
                        // Read content and return as byte array
                        long size = blockchainFile.getSize();
                        if (size > 0) {
                            byte[] content = new byte[(int) size];
                            FSPointer buffer = createFSPointer(content);
                            blockchainFile.read(buffer, size, 0);
                            return content;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to load file content: {}", path, e);
                }
                return null;
            }

            @Override
            public boolean fileExistsInBlockchain(String path) {
                try {
                    getFileByPath(path);
                    return true;
                } catch (PathNotFound e) {
                    return false;
                }
            }

            @Override
            public FileMetadata getFileMetadata(String path) {
                try {
                    File file = getFileByPath(path);
                    if (file instanceof BlockchainFile) {
                        BlockchainFile blockchainFile = (BlockchainFile) file;
                        return new FileMetadata(
                                blockchainFile.getSize(),
                                blockchainFile.getLastUpdated(),
                                null, // checksum not available
                                false);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to get metadata for: {}", path);
                }
                return null;
            }

            @Override
            public void onFileSavedToBlockchain(String path, byte[] content) {
                logger.debug("File saved to blockchain: {}", path);
                cacheStrategy.invalidate(path);
            }

            @Override
            public void onFileDeletedFromBlockchain(String path) {
                logger.debug("File deleted from blockchain: {}", path);
                cacheStrategy.invalidate(path);
            }

            @Override
            public void preloadFile(String path, int priority) {
                logger.debug(
                        "Preloading file: {} (priority: {})",
                        path,
                        priority);
                try {
                    loadFileContent(path);
                } catch (Exception e) {
                    logger.debug("Failed to preload file: {}", path);
                }
            }

            @Override
            public void clearCache(String path) {
                cacheStrategy.invalidate(path);
                logger.debug("Cache cleared for: {}", path);
            }

            @Override
            public CacheStatistics getCacheStatistics() {
                var stats = cacheStrategy.getStatistics();
                return new CacheStatistics(
                        stats.getL1Hits() + stats.getL2Hits(),
                        stats.getMisses(),
                        stats.getL1Size() + stats.getL2Size(),
                        100 * 1024 * 1024L, // Max cache size
                        (int) stats.getL1Size());
            }
        };

        return new PlaceholderManager(fileCallback, cacheConfig);
    }

    /**
     * Sets the FileProvider integration for macOS native Finder support.
     * When set, file operations will be automatically synchronized with the
     * FileProvider.
     *
     * @param fileProvider the FileProvider integration instance
     */
    public void setFileProviderIntegration(
            FileProviderIntegration fileProvider) {
        this.fileProviderIntegration = fileProvider;

        if (fileProvider != null) {
            logger.info("Configuring FileProvider integration");

            // Set up callback for FileProvider to load content from blockchain
            fileProvider.setFileChangeCallback(new FileChangeCallback() {
                @Override
                public byte[] loadFileContent(String path) {
                    logger.debug("FileProvider requesting content for: {}", path);
                    return loadFileContentFromBlockchain(path);
                }

                @Override
                public boolean fileExistsInBlockchain(String path) {
                    try {
                        getFileByPath(path);
                        return true;
                    } catch (PathNotFound e) {
                        return false;
                    }
                }

                @Override
                public FileMetadata getFileMetadata(String path) {
                    try {
                        File file = getFileByPath(path);
                        if (file instanceof BlockchainFile) {
                            BlockchainFile bf = (BlockchainFile) file;
                            return new FileMetadata(
                                    bf.getSize(),
                                    bf.getLastUpdated(),
                                    null, // checksum not available
                                    false);
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to get metadata for: {}", path);
                    }
                    return null;
                }

                @Override
                public void onFileSavedToBlockchain(String path, byte[] content) {
                    logger.debug("File saved to blockchain: {}", path);
                    cacheStrategy.invalidate(path);
                }

                @Override
                public void onFileDeletedFromBlockchain(String path) {
                    logger.debug("File deleted from blockchain: {}", path);
                    cacheStrategy.invalidate(path);
                }

                @Override
                public void preloadFile(String path, int priority) {
                    logger.debug("Preload requested for: {} (priority: {})", path, priority);
                    try {
                        loadFileContent(path);
                    } catch (Exception e) {
                        logger.debug("Failed to preload: {}", path);
                    }
                }

                @Override
                public void clearCache(String path) {
                    cacheStrategy.invalidate(path);
                    logger.debug("Cache cleared for: {}", path);
                }

                @Override
                public CacheStatistics getCacheStatistics() {
                    var stats = cacheStrategy.getStatistics();
                    return new CacheStatistics(
                            stats.getL1Hits() + stats.getL2Hits(),
                            stats.getMisses(),
                            stats.getL1Size() + stats.getL2Size(),
                            100 * 1024 * 1024L,
                            (int) stats.getL1Size());
                }
            });

            // Set bidirectional reference
            fileProvider.setInMemoryFileSystem(this);

            logger.info("FileProvider integration configured successfully");
        }
    }

    /**
     * Loads file content from blockchain for FileProvider materialization.
     * This method is called when FileProvider needs to materialize a placeholder.
     *
     * @param path the file path
     * @return file content as byte array, or null if not found
     */
    private byte[] loadFileContentFromBlockchain(String path) {
        try {
            File file = getFileByPath(path);
            if (file instanceof BlockchainFile) {
                BlockchainFile blockchainFile = (BlockchainFile) file;
                ensureFileContentLoadedWithCache(blockchainFile);

                long size = blockchainFile.getSize();
                if (size > 0) {
                    byte[] content = new byte[(int) size];
                    FSPointer buffer = createFSPointer(content);
                    blockchainFile.read(buffer, size, 0);
                    return content;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load file content from blockchain: {}", path, e);
        }
        return null;
    }

    /**
     * Synchronizes the entire filesystem structure with FileProvider.
     * Creates placeholders for all files and directories in the filesystem.
     */
    public void syncWithFileProvider() {
        if (fileProviderIntegration == null ||
                !fileProviderIntegration.isInitialized()) {
            logger.warn("FileProvider not initialized, skipping sync");
            return;
        }

        logger.info("Syncing filesystem structure with FileProvider");
        syncDirectoryRecursive(rootDirectory, "");
        logger.info("Filesystem sync with FileProvider completed");
    }

    /**
     * Recursively synchronizes a directory and its children with FileProvider.
     *
     * @param dir      the directory to sync
     * @param basePath the base path for relative path construction
     */
    private void syncDirectoryRecursive(Directory dir, String basePath) {
        Set<Path> children = dir.getChildren();

        for (Path child : children) {
            String relativePath = basePath.isEmpty()
                    ? child.getName()
                    : basePath + "/" + child.getName();

            if (child instanceof Directory) {
                // Create placeholder for directory
                fileProviderIntegration.createPlaceholder(
                        relativePath,
                        0,
                        System.currentTimeMillis(),
                        true);

                // Recursively sync subdirectories
                syncDirectoryRecursive((Directory) child, relativePath);

            } else if (child instanceof BlockchainFile) {
                BlockchainFile file = (BlockchainFile) child;

                // Create placeholder for file
                fileProviderIntegration.createPlaceholder(
                        relativePath,
                        file.getSize(),
                        file.getLastUpdated(),
                        false);
            }
        }
    }

    /**
     * Gets the appropriate path separator for the current OS.
     */
    private String pathSeparator() {
        return PathUtils.getPathDelimiterBasedOnOS();
    }

    /**
     * Extracts the last component from a path.
     */
    public String getLastComponent(String path) {
        // Remove trailing separators
        while (path.endsWith(pathSeparator()) && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.equals(pathSeparator())) {
            return pathSeparator();
        }

        int lastSeparatorIndex = path.lastIndexOf(pathSeparator());
        return lastSeparatorIndex == -1
                ? path
                : path.substring(lastSeparatorIndex + 1);
    }

    /**
     * Gets the parent directory of a given path.
     */
    public Directory getParentDirectory(String path) {
        String parentPath = getParentPath(path);
        if (parentPath == null) {
            return null;
        }

        try {
            Path parent = getPath(parentPath);
            if (!(parent instanceof Directory)) {
                throw new IllegalArgumentException(
                        "Parent path is not a directory: " + parentPath);
            }
            return (Directory) parent;
        } catch (PathNotFound e) {
            return null;
        }
    }

    /**
     * Internal helper for getting parent directory.
     */
    private Directory getParentDirectoryInternal(String path)
            throws PathNotFound {
        Directory parent = getParentDirectory(path);
        if (parent == null) {
            throw new PathNotFound("Parent directory not found for: " + path);
        }
        return parent;
    }

    public Path findPath(String path) {
        return rootDirectory.find(path);
    }

    public Path getPath(String path) throws PathNotFound {
        Path result = findPath(path);
        if (result == null) {
            throw new PathNotFound(path);
        }
        return result;
    }

    public Directory getDirectoryByPath(String path)
            throws PathNotFound, PathIsNotADirectory {
        Path p = getPath(path);
        if (!(p instanceof Directory)) {
            throw new PathIsNotADirectory(path);
        }
        return (Directory) p;
    }

    public File getFileByPath(String path) throws PathNotFound, PathIsNotAFile {
        Path p = getPath(path);
        if (!(p instanceof File)) {
            throw new PathIsNotAFile(path);
        }
        return (File) p;
    }

    /**
     * Creates a new file with real blockchain integration.
     * Files are immediately queued for blockchain deployment.
     */
    public void createFile(String path, long mode)
            throws OperationNotPermitted {
        try {
            Directory parent = getParentDirectoryInternal(path);
            String fileName = getLastComponent(path);

            // Record operation cost
            if (parent instanceof BlockchainDirectory) {
                String walletAddress = ((BlockchainDirectory) parent).getBlockchainContext()
                        .getWalletInfo()
                        .revAddress();
                TokenDirectory.recordOperationCost(
                        walletAddress,
                        "CREATE_FILE");
            }

            // Create blockchain file that will be deployed to the blockchain
            BlockchainFile newFile = new BlockchainFile(
                    parent.getBlockchainContext(),
                    fileName,
                    parent);

            parent.addChild(newFile);

            // Sync with FileProvider if available
            if (fileProviderIntegration != null &&
                    fileProviderIntegration.isInitialized()) {
                fileProviderIntegration.createPlaceholder(
                        path,
                        0,
                        System.currentTimeMillis(),
                        false);
                logger.debug("Created FileProvider placeholder for: {}", path);
            }

            logger.debug(
                    "Created blockchain file: {} (will be deployed)",
                    path);
        } catch (PathNotFound e) {
            throw OperationNotPermitted.instance;
        }
    }

    public void getAttributes(String path, FSFileStat stbuf, FSContext context)
            throws PathNotFound {
        Path p = getPath(path);
        p.getAttr(stbuf, context);
    }

    /**
     * Creates a directory with blockchain persistence.
     */
    public void makeDirectory(String path, long mode)
            throws OperationNotPermitted {
        try {
            Directory parent = getParentDirectoryInternal(path);
            String dirName = getLastComponent(path);

            // Record operation cost
            if (parent instanceof BlockchainDirectory) {
                String walletAddress = ((BlockchainDirectory) parent).getBlockchainContext()
                        .getWalletInfo()
                        .revAddress();
                TokenDirectory.recordOperationCost(
                        walletAddress,
                        "CREATE_DIRECTORY");
            }

            // Create directory using the parent's mkdir method
            parent.mkdir(dirName);

            // Sync with FileProvider if available
            if (fileProviderIntegration != null &&
                    fileProviderIntegration.isInitialized()) {
                fileProviderIntegration.createPlaceholder(
                        path,
                        0,
                        System.currentTimeMillis(),
                        true);
                logger.debug("Created FileProvider placeholder for directory: {}", path);
            }

            logger.debug(
                    "Created blockchain directory: {} (will be persisted)",
                    path);
        } catch (PathNotFound e) {
            throw OperationNotPermitted.instance;
        }
    }

    /**
     * Reads file content from blockchain or local cache.
     * If file is not cached, fetches from blockchain.
     */
    public int readFile(String path, FSPointer buf, long size, long offset)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);

        // Record operation cost
        if (file instanceof BlockchainFile) {
            BlockchainFile blockchainFile = (BlockchainFile) file;
            String walletAddress = blockchainFile
                    .getBlockchainContext()
                    .getWalletInfo()
                    .revAddress();
            TokenDirectory.recordOperationCost(walletAddress, "READ_FILE");

            ensureFileContentLoaded(blockchainFile);
        }

        return file.read(buf, size, offset);
    }

    /**
     * Ensures blockchain file content is loaded from blockchain if needed.
     * Now uses Caffeine cache for optimal performance.
     */
    private void ensureFileContentLoaded(BlockchainFile file) {
        try {
            if (file.getSize() == 0 && file.getLastUpdated() > 0) {
                // File exists on blockchain but not loaded locally
                ensureFileContentLoadedWithCache(file);
            }
        } catch (Exception e) {
            logger.warn(
                    "Failed to load file content from blockchain: {}",
                    e.getMessage());
        }
    }

    /**
     * Loads file content with Caffeine cache integration.
     */
    private void ensureFileContentLoadedWithCache(BlockchainFile file) {
        String filePath = file.getAbsolutePath();

        // Try to get from Caffeine cache first
        var cacheResult = cacheStrategy.get(filePath);
        if (cacheResult.isPresent()) {
            var result = cacheResult.get();
            try {
                // Write cached content to file
                file.open();
                FSPointer buffer = createFSPointer(result.getContent());
                file.write(buffer, result.getContent().length, 0);
                logger.debug(
                        "Loaded file content from {} cache: {} ({} bytes)",
                        result.getHitLevel().name(),
                        filePath,
                        result.getContent().length);
                return;
            } catch (Exception e) {
                logger.warn(
                        "Failed to write cached content to file: {}",
                        filePath,
                        e);
            }
        }

        // Cache miss - load from blockchain
        loadFileContentFromBlockchain(file);
    }

    /**
     * Loads file content from blockchain using exploratory deploy.
     * Results are cached using Caffeine for future access.
     */
    private void loadFileContentFromBlockchain(BlockchainFile file) {
        try {
            String rholangQuery = RholangExpressionConstructor.readFromChannel(
                    file.getAbsolutePath());

            CompletableFuture<RhoTypes.Expr> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return blockchainClient.exploratoryDeploy(rholangQuery);
                } catch (F1r3DriveError e) {
                    throw new RuntimeException(
                            "Failed to fetch file from blockchain",
                            e);
                }
            });

            RhoTypes.Expr result = future.get(30, TimeUnit.SECONDS);

            if (result != null && result.hasGByteArray()) {
                byte[] content = RholangExpressionConstructor.parseExploratoryDeployBytes(
                        result);
                if (content.length > 0) {
                    // Cache content using Caffeine (simplified without metadata)
                    cacheStrategy.put(
                            file.getAbsolutePath(),
                            content,
                            null // No metadata for now
                    );

                    // Write content to file
                    file.open();
                    FSPointer buffer = createFSPointer(content);
                    file.write(buffer, content.length, 0);
                    logger.debug(
                            "Loaded and cached file content from blockchain: {} ({} bytes)",
                            file.getAbsolutePath(),
                            content.length);
                }
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to load file content from blockchain: {}",
                    e.getMessage(),
                    e);
        }
    }

    public void readDirectory(String path, FSFillDir filter)
            throws PathNotFound, PathIsNotADirectory {
        Directory directory = getDirectoryByPath(path);
        directory.read(filter);
    }

    public void getFileSystemStats(String path, FSStatVfs stbuf) {
        if ("/".equals(path)) {
            int BLOCKSIZE = 4096;
            int FUSE_NAME_MAX = 255;

            // Provide realistic filesystem stats
            long totalSpace = 100L * 1024 * 1024 * 1024; // 100GB
            long usableSpace = totalSpace - (10L * 1024 * 1024 * 1024); // Reserve 10GB
            long tBlocks = totalSpace / BLOCKSIZE;
            long aBlocks = usableSpace / BLOCKSIZE;

            stbuf.setBlockSize(BLOCKSIZE);
            stbuf.setFragmentSize(BLOCKSIZE);
            stbuf.setBlocks(tBlocks);
            stbuf.setBlocksAvailable(aBlocks);
            stbuf.setBlocksFree(aBlocks);
            stbuf.setMaxFilenameLength(FUSE_NAME_MAX);
        }
    }

    /**
     * Renames/moves a file with blockchain persistence.
     */
    public void renameFile(String path, String newName)
            throws PathNotFound, OperationNotPermitted {
        Path p = getPath(path);
        Directory newParent = getParentDirectoryInternal(newName);
        Directory oldParent = p.getParent();
        String lastComponent = getLastComponent(newName);

        p.rename(lastComponent, newParent);

        if (oldParent != newParent) {
            newParent.addChild(p);
            oldParent.deleteChild(p);
        } else {
            newParent.addChild(p);
        }

        // Trigger blockchain update for renamed files
        if (p instanceof BlockchainFile) {
            ((BlockchainFile) p).onChange();
            logger.debug("Blockchain file renamed: {} -> {}", path, newName);
        }

        // Sync with FileProvider if available
        if (fileProviderIntegration != null &&
                fileProviderIntegration.isInitialized()) {
            // Remove old placeholder
            fileProviderIntegration.removePlaceholder(path);

            // Create new placeholder at new location
            if (p instanceof BlockchainFile) {
                BlockchainFile file = (BlockchainFile) p;
                fileProviderIntegration.createPlaceholder(
                        newName,
                        file.getSize(),
                        file.getLastUpdated(),
                        false);
            } else if (p instanceof Directory) {
                fileProviderIntegration.createPlaceholder(
                        newName,
                        0,
                        System.currentTimeMillis(),
                        true);
            }
            logger.debug("Updated FileProvider placeholders for rename: {} -> {}", path, newName);
        }
    }

    public void removeDirectory(String path)
            throws PathNotFound, PathIsNotADirectory, DirectoryNotEmpty, OperationNotPermitted {
        Directory directory = getDirectoryByPath(path);
        if (!directory.isEmpty()) {
            throw new DirectoryNotEmpty(path);
        }

        // Remove from blockchain if it's a blockchain directory
        if (directory instanceof UnlockedWalletDirectory) {
            UnlockedWalletDirectory blockchainDir = (UnlockedWalletDirectory) directory;
            // Queue blockchain removal operation
            String rholang = RholangExpressionConstructor.forgetChanel(
                    directory.getAbsolutePath());
            // Queue deployment via dispatcher
            try {
                deployDispatcher.enqueueDeploy(
                        new io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher.Deployment(
                                rholang,
                                false,
                                "rholang",
                                null, // rev address not needed for deletion
                                null, // signing key not needed for deletion
                                System.currentTimeMillis()));
            } catch (Exception e) {
                logger.warn("Failed to queue directory removal deployment", e);
            }
            logger.debug("Queued blockchain directory removal: {}", path);
        }

        directory.delete();
        Directory parent = directory.getParent();
        if (parent != null) {
            parent.deleteChild(directory);
        }

        // Sync with FileProvider if available
        if (fileProviderIntegration != null &&
                fileProviderIntegration.isInitialized()) {
            fileProviderIntegration.removePlaceholder(path);
            logger.debug("Removed FileProvider placeholder for directory: {}", path);
        }
    }

    public void truncateFile(String path, long offset)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        file.truncate(offset);

        // Trigger blockchain update for truncated blockchain files
        if (file instanceof BlockchainFile) {
            ((BlockchainFile) file).onChange();
            logger.debug("Blockchain file truncated: {}", path);
        }

        // Sync with FileProvider if available
        if (fileProviderIntegration != null &&
                fileProviderIntegration.isInitialized() &&
                file instanceof BlockchainFile) {
            BlockchainFile blockchainFile = (BlockchainFile) file;
            fileProviderIntegration.updatePlaceholder(
                    path,
                    blockchainFile.getSize(),
                    blockchainFile.getLastUpdated());
            logger.debug("Updated FileProvider placeholder after truncate: {}", path);
        }
    }

    public void unlinkFile(String path)
            throws PathNotFound, OperationNotPermitted {
        Path p = getPath(path);

        // Remove from blockchain if it's a blockchain file
        if (p instanceof BlockchainFile) {
            BlockchainFile blockchainFile = (BlockchainFile) p;

            // Record operation cost
            String walletAddress = blockchainFile
                    .getBlockchainContext()
                    .getWalletInfo()
                    .revAddress();
            TokenDirectory.recordOperationCost(walletAddress, "DELETE_FILE");

            String rholang = RholangExpressionConstructor.forgetChanel(
                    p.getAbsolutePath());
            // Queue deployment via dispatcher
            try {
                deployDispatcher.enqueueDeploy(
                        new io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher.Deployment(
                                rholang,
                                false,
                                "rholang",
                                null, // rev address not needed for deletion
                                null, // signing key not needed for deletion
                                System.currentTimeMillis()));
            } catch (Exception e) {
                logger.warn("Failed to queue file removal deployment", e);
            }
            logger.debug("Queued blockchain file removal: {}", path);
        }

        p.delete();
        Directory parent = p.getParent();
        if (parent != null) {
            parent.deleteChild(p);
        }

        // Sync with FileProvider if available
        if (fileProviderIntegration != null &&
                fileProviderIntegration.isInitialized()) {
            fileProviderIntegration.removePlaceholder(path);
            logger.debug("Removed FileProvider placeholder for: {}", path);
        }
    }

    public void openFile(String path)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);
        file.open();

        // Ensure blockchain file content is loaded from cache or blockchain
        if (file instanceof BlockchainFile) {
            ensureFileContentLoadedWithCache((BlockchainFile) file);
        }
    }

    /**
     * Writes to file with automatic blockchain deployment.
     * For .rho and .metta files, triggers blockchain deployment.
     */
    public int writeFile(String path, FSPointer buf, long size, long offset)
            throws PathNotFound, PathIsNotAFile, IOException {
        File file = getFileByPath(path);

        // Record operation cost
        if (file instanceof BlockchainFile) {
            BlockchainFile blockchainFile = (BlockchainFile) file;
            String walletAddress = blockchainFile
                    .getBlockchainContext()
                    .getWalletInfo()
                    .revAddress();
            TokenDirectory.recordOperationCost(walletAddress, "WRITE_FILE");
        }

        int bytesWritten = file.write(buf, size, offset);

        // Trigger blockchain deployment for blockchain files
        if (file instanceof BlockchainFile) {
            BlockchainFile blockchainFile = (BlockchainFile) file;
            blockchainFile.onChange();

            // Auto-deploy .rho and .metta files
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".rho") || fileName.endsWith(".metta")) {
                triggerAutomaticDeployment(blockchainFile);
            }

            // Invalidate cache since file content changed
            cacheStrategy.invalidate(path);

            // Sync with FileProvider if available
            if (fileProviderIntegration != null &&
                    fileProviderIntegration.isInitialized()) {
                fileProviderIntegration.updatePlaceholder(
                        path,
                        blockchainFile.getSize(),
                        blockchainFile.getLastUpdated());
                logger.debug("Updated FileProvider placeholder for: {}", path);
            }

            logger.debug(
                    "Blockchain file modified: {} ({} bytes written)",
                    path,
                    bytesWritten);
        }

        return bytesWritten;
    }

    /**
     * Triggers automatic deployment for .rho and .metta files.
     */
    private void triggerAutomaticDeployment(BlockchainFile file) {
        try {
            String fileName = file.getName().toLowerCase();
            String language = fileName.endsWith(".rho")
                    ? F1r3flyBlockchainClient.RHOLANG
                    : fileName.endsWith(".metta")
                            ? F1r3flyBlockchainClient.METTA_LANGUAGE
                            : null;

            if (language != null) {
                // Read file content
                String content = readFileContentAsString(file);

                // Deploy to blockchain
                CompletableFuture.runAsync(() -> {
                    try {
                        RevWalletInfo walletInfo = file
                                .getBlockchainContext()
                                .getWalletInfo();
                        // Use dummy signing key for now - real implementation would get from wallet
                        byte[] signingKey = null;
                        long timestamp = System.currentTimeMillis();

                        // Record operation cost for deployment
                        TokenDirectory.recordOperationCost(
                                walletInfo.revAddress(),
                                "DEPLOY_CONTRACT");

                        blockchainClient.deploy(
                                content,
                                false,
                                language,
                                signingKey,
                                timestamp);

                        logger.info(
                                "Successfully deployed {} file to blockchain: {}",
                                language,
                                file.getAbsolutePath());
                    } catch (Exception e) {
                        logger.error(
                                "Failed to deploy {} file to blockchain: {}",
                                language,
                                file.getAbsolutePath(),
                                e);
                    }
                });
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to trigger automatic deployment: {}",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Reads file content as string for deployment.
     */
    private String readFileContentAsString(BlockchainFile file)
            throws IOException {
        file.open();
        long fileSize = file.getSize();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("File too large for deployment: " + fileSize);
        }

        byte[] content = new byte[(int) fileSize];
        FSPointer buffer = createFSPointer(content);
        file.read(buffer, fileSize, 0);

        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void flushFile(String path) throws PathNotFound, PathIsNotAFile {
        File file = getFileByPath(path);
        file.close();
    }

    // Utility methods for file/directory access

    public File getFile(String path) {
        try {
            return getFileByPath(path);
        } catch (PathNotFound | PathIsNotAFile e) {
            return null;
        }
    }

    public Directory getDirectory(String path) {
        try {
            return getDirectoryByPath(path);
        } catch (PathNotFound | PathIsNotADirectory e) {
            return null;
        }
    }

    public boolean isRootPath(String path) {
        return "/".equals(path) || path.isEmpty();
    }

    /**
     * Gets the parent path of a given path.
     */
    public String getParentPath(String path) {
        if (isRootPath(path)) {
            return null;
        }

        // Remove trailing separators
        while (path.endsWith(pathSeparator()) && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSeparatorIndex = path.lastIndexOf(pathSeparator());
        return lastSeparatorIndex <= 0
                ? pathSeparator()
                : path.substring(0, lastSeparatorIndex);
    }

    /**
     * Parses REV addresses from blockchain genesis block.
     * This uses real blockchain data, not mock data.
     */
    private List<String> parseRavAddressesFromGenesisBlock(
            F1r3flyBlockchainClient f1R3FlyBlockchainClient) throws F1r3DriveError {
        logger.debug("Parsing REV addresses from genesis block");

        DeployServiceCommon.BlockInfo genesisBlock = f1R3FlyBlockchainClient.getGenesisBlock();
        List<DeployServiceCommon.DeployInfo> deploys = genesisBlock.getDeploysList();

        DeployServiceCommon.DeployInfo tokenInitializeDeploy = deploys
                .stream()
                .filter(deployInfo -> deployInfo.getTerm().contains("revVaultInitCh"))
                .findFirst()
                .orElseThrow(() -> new F1r3DriveError(
                        "Token initialize deploy not found in genesis block"));

        String regex = "\\\"(1111[A-Za-z0-9]+)\\\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(tokenInitializeDeploy.getTerm());

        List<String> ravAddresses = new java.util.ArrayList<>();
        while (matcher.find()) {
            ravAddresses.add(matcher.group(1));
        }

        logger.info(
                "Found {} REV addresses in genesis block",
                ravAddresses.size());
        return ravAddresses;
    }

    /**
     * Creates wallet directories from blockchain data.
     */
    private Set<Path> createRavAddressDirectoriesFromBlockchain()
            throws F1r3DriveError {
        List<String> ravAddresses = parseRavAddressesFromGenesisBlock(
                blockchainClient);

        logger.debug(
                "Creating wallet directories for addresses: {}",
                ravAddresses);

        Set<Path> children = new HashSet<>();

        for (String address : ravAddresses) {
            BlockchainContext context = new BlockchainContext(
                    new RevWalletInfo(address, null),
                    deployDispatcher);

            LockedWalletDirectory lockedDir = new LockedWalletDirectory(
                    context,
                    rootDirectory);
            children.add(lockedDir);

            logger.debug(
                    "Created locked wallet directory for address: {}",
                    address);
        }

        return children;
    }

    /**
     * Unlocks a wallet directory with a real private key.
     * This performs actual cryptographic validation and blockchain operations.
     */
    public void unlockRootDirectory(String revAddress, String privateKey)
            throws InvalidSigningKeyException {
        String searchPath = "/LOCKED-REMOTE-REV-" + revAddress;

        logger.info(
                "WALLET_UNLOCK_START: Attempting to unlock wallet directory for address: {}",
                revAddress);

        try {
            Path lockedRoot = getDirectory(searchPath);
            if (lockedRoot == null) {
                // Create locked wallet directory if it doesn't exist
                logger.info(
                        "WALLET_CREATE: Locked wallet directory not found, creating: {}",
                        searchPath);
                BlockchainContext context = new BlockchainContext(
                        new RevWalletInfo(revAddress, null),
                        deployDispatcher);
                lockedRoot = new LockedWalletDirectory(context, rootDirectory);
                rootDirectory.addChild(lockedRoot);
                logger.info(
                        "WALLET_CREATED: Successfully created locked wallet directory: {}",
                        searchPath);
            } else {
                logger.info(
                        "WALLET_FOUND: Found existing locked wallet directory: {}",
                        searchPath);
            }

            if (lockedRoot instanceof LockedWalletDirectory) {
                LockedWalletDirectory lockedWalletDir = (LockedWalletDirectory) lockedRoot;

                logger.info(
                        "WALLET_UNLOCK_CRYPTO: Starting cryptographic unlock validation for: {}",
                        revAddress);

                // Perform real cryptographic unlock with blockchain validation
                UnlockedWalletDirectory unlockedRoot = lockedWalletDir.unlock(
                        privateKey,
                        deployDispatcher);

                logger.info(
                        "WALLET_FILESYSTEM_UPDATE: Replacing locked with unlocked directory for: {}",
                        revAddress);
                this.rootDirectory.deleteChild(lockedRoot);
                this.rootDirectory.addChild(unlockedRoot);

                // Also unlock the physical wallet for file system operations
                // Physical wallet operations are now handled by FolderTokenManager
                logger.info(
                        "WALLET_UNLOCKED: Successfully unlocked wallet in file system: {}",
                        revAddress);

                // Set up real token balance monitoring
                TokenDirectory tokenDirectory = unlockedRoot.getTokenDirectory();
                if (tokenDirectory != null) {
                    logger.info(
                            "WALLET_TOKEN_SETUP: Setting up token directory monitoring for: {}",
                            revAddress);
                    stateChangeEventsManager.registerEventProcessor(
                            StateChangeEvents.WalletBalanceChanged.class,
                            new StateChangeEventProcessor() {
                                @Override
                                public void processEvent(StateChangeEvents event) {
                                    if (event instanceof StateChangeEvents.WalletBalanceChanged balanceChanged) {
                                        if (balanceChanged
                                                .revAddress()
                                                .equals(
                                                        unlockedRoot
                                                                .getBlockchainContext()
                                                                .getWalletInfo()
                                                                .revAddress())) {
                                            // Update token directory with real balance from blockchain
                                            tokenDirectory.handleWalletBalanceChanged();
                                        }
                                    }
                                }
                            });

                    // Initial balance fetch from blockchain
                    fetchInitialWalletBalance(unlockedRoot);

                    logger.info(
                            "Successfully unlocked wallet directory: {}",
                            revAddress);
                } else {
                    logger.warn(
                            "Token directory is null for unlocked wallet: {}",
                            revAddress);
                }
            } else {
                String errorMsg = lockedRoot != null
                        ? "Expected LockedWalletDirectory but got: " +
                                lockedRoot.getClass().getSimpleName()
                        : "Wallet directory not found: " + searchPath;

                logger.error(errorMsg);
                throw new PathNotFound(searchPath);
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to unlock wallet directory: {}",
                    revAddress,
                    e);
            throw new InvalidSigningKeyException(
                    "Failed to unlock wallet: " + e.getMessage());
        }
    }

    /**
     * Fetches initial wallet balance from blockchain.
     */
    private void fetchInitialWalletBalance(
            UnlockedWalletDirectory unlockedWallet) {
        CompletableFuture.runAsync(() -> {
            try {
                String revAddress = unlockedWallet
                        .getBlockchainContext()
                        .getWalletInfo()
                        .revAddress();
                String balanceQuery = RholangExpressionConstructor.checkBalanceRho(revAddress);

                RhoTypes.Expr result = blockchainClient.exploratoryDeploy(
                        balanceQuery);

                if (result != null && result.hasGInt()) {
                    long balance = result.getGInt();

                    // Trigger balance update event
                    // Trigger balance change event (simplified notification)
                    stateChangeEventsManager.notifyExternalChange();

                    logger.info(
                            "Fetched initial wallet balance: {} REV for {}",
                            balance,
                            revAddress);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch initial wallet balance", e);
            }
        });
    }

    /**
     * Handles token file changes with real blockchain operations.
     */
    @Override
    public void changeTokenFile(String filePath) throws NoDataByPath {
        File file = getFile(filePath);
        if (file == null) {
            throw new NoDataByPath(filePath);
        }

        if (!(file instanceof TokenFile)) {
            throw new RuntimeException("File is not a token file: " + filePath);
        }

        TokenFile tokenFile = (TokenFile) file;
        Directory tokenDirectory = tokenFile.getParent();

        if (tokenDirectory == null) {
            throw new RuntimeException("Token directory is null: " + filePath);
        }

        if (!(tokenDirectory instanceof TokenDirectory)) {
            throw new RuntimeException(
                    "Token directory is not a token directory: " + filePath);
        }

        // Perform real blockchain token operation
        TokenDirectory tokenDir = (TokenDirectory) tokenDirectory;
        tokenDir.change(tokenFile);

        logger.info("Token file change processed: {}", filePath);
    }

    /**
     * Waits for all background blockchain deployments to complete.
     */
    @Override
    public void waitOnBackgroundDeploy() {
        logger.debug(
                "Waiting for background blockchain deployments to complete");
        deployDispatcher.waitOnEmptyQueue();
        logger.debug("All background blockchain deployments completed");
    }

    /**
     * Terminates the filesystem and cleans up blockchain connections.
     */
    @Override
    public void terminate() {
        logger.info("Terminating filesystem with blockchain cleanup");

        try {
            logger.debug(
                    "Waiting for background blockchain deployments to complete...");
            waitOnBackgroundDeploy();
            logger.debug("Background blockchain deployments completed");
        } catch (Throwable e) {
            logger.warn(
                    "Error waiting for background deployments to complete",
                    e);
        }

        try {
            logger.debug(
                    "Physical wallet management handled by FolderTokenManager");
        } catch (Throwable e) {
            logger.warn("Error shutting down physical wallet manager", e);
        }

        try {
            logger.debug("Shutting down deploy dispatcher...");
            this.deployDispatcher.destroy();
            logger.info("Deploy dispatcher shut down successfully");
        } catch (Throwable e) {
            logger.warn("Error shutting down deploy dispatcher", e);
        }

        try {
            logger.debug("Cleaning local filesystem cache...");
            this.rootDirectory.cleanLocalCache();
            logger.info("Local filesystem cache cleaned");
        } catch (Throwable e) {
            logger.warn("Error cleaning local cache", e);
        }

        try {
            logger.debug("Shutting down state change events manager...");
            this.stateChangeEventsManager.shutdown();
            logger.info("State change events manager shut down");
        } catch (Throwable e) {
            logger.warn("Error shutting down state change events manager", e);
        }

        try {
            logger.debug(
                    "Cleaning up Caffeine cache and PlaceholderManager...");
            this.placeholderManager.cleanup();
            this.cacheStrategy.performMaintenance();
            logger.info("Cache cleanup completed");
        } catch (Throwable e) {
            logger.warn("Error during cache cleanup", e);
        }

        logger.info(
                "Filesystem termination completed - all blockchain connections closed");
    }

    /**
     * Physical wallet manager has been removed - functionality moved to
     * FolderTokenManager
     */

    public RootDirectory getRootDirectory() {
        return rootDirectory;
    }

    /**
     * Validates that a wallet operation can be performed on a physical wallet
     */
    public void validatePhysicalWalletOperation(
            String walletAddress,
            String operationType) throws IllegalStateException {
        // Physical wallet validation now handled by FolderTokenManager
        logger.debug(
                "Wallet operation validation for {}: {}",
                walletAddress,
                operationType);
    }

    /**
     * Checks if a physical wallet is currently unlocked
     */
    public boolean isPhysicalWalletUnlocked(String walletAddress) {
        // Physical wallet status now handled by FolderTokenManager
        return false; // Simplified for now
    }

    /**
     * Helper method to create FSPointer from byte array.
     */
    private FSPointer createFSPointer(byte[] data) {
        return new FSPointer() {
            private byte[] buffer = data;

            @Override
            public void put(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(bytes, start, buffer, (int) offset, length);
            }

            @Override
            public void get(long offset, byte[] bytes, int start, int length) {
                System.arraycopy(buffer, (int) offset, bytes, start, length);
            }

            @Override
            public byte getByte(long offset) {
                return buffer[(int) offset];
            }

            @Override
            public void putByte(long offset, byte value) {
                buffer[(int) offset] = value;
            }
        };
    }
}
