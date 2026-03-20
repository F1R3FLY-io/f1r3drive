package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderInfo;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderState;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration with macOS File Provider Framework.
 * Creates virtual filesystem in Finder, supports placeholder files for lazy
 * loading,
 * and provides seamless integration with macOS applications.
 *
 * This class bridges F1r3Drive with macOS File Provider Framework to create
 * a native macOS experience where files appear in Finder and can be accessed
 * by any macOS application while being lazily loaded from the blockchain.
 */
public class FileProviderIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FileProviderIntegration.class);

    // Native library loading
    static {
        try {
            NativeLibraryLoader.loadFileProviderLibrary();
            LOGGER.info("Successfully loaded native File Provider library");
        } catch (UnsatisfiedLinkError e) {
            LOGGER.warn(
                    "Failed to load native File Provider library: {}. File Provider integration will be disabled.",
                    e.getMessage());
            LOGGER.warn(
                    "Platform info: {}",
                    NativeLibraryLoader.getPlatformInfo());
        }
    }

    // File Provider Framework constants
    public static final int NSFileProviderItemTypeData = 0;
    public static final int NSFileProviderItemTypeFolder = 1;
    public static final int NSFileProviderItemTypeSymbolicLink = 2;

    public static final int NSFileProviderMaterializationPolicyOnDemand = 0;
    public static final int NSFileProviderMaterializationPolicyAlways = 1;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Core components
    private FileChangeCallback fileChangeCallback;
    private InMemoryFileSystem inMemoryFileSystem;
    private long nativeProviderRef = 0; // Native NSFileProviderExtension reference

    // Placeholder and materialization tracking
    private final Map<String, PlaceholderInfo> placeholders = new ConcurrentHashMap<>();
    private final Set<String> materializingFiles = new HashSet<>();

    // Fixed thread pool for materialization operations to prevent OutOfMemoryError
    private volatile ExecutorService materializationExecutor;

    // Configuration
    private String domainIdentifier;
    private String displayName = "F1r3Drive";
    private String rootPath;

    public String getRootPath() {
        return rootPath;
    }

    private int defaultMaterializationPolicy = NSFileProviderMaterializationPolicyOnDemand;

    /**
     * Creates a new FileProviderIntegration instance.
     */
    public FileProviderIntegration() {
        // Generate unique domain identifier
        this.domainIdentifier = "io.f1r3fly.f1r3drive.domain." + System.currentTimeMillis();
        LOGGER.debug(
                "FileProviderIntegration created with domain: {}",
                domainIdentifier);
    }

    /**
     * Creates a FileProviderIntegration with custom configuration.
     *
     * @param domainIdentifier custom domain identifier
     * @param displayName      display name for the File Provider
     */
    public FileProviderIntegration(
            String domainIdentifier,
            String displayName) {
        this.domainIdentifier = domainIdentifier;
        this.displayName = displayName;
        LOGGER.debug(
                "FileProviderIntegration created with domain: {}, display name: {}",
                domainIdentifier,
                displayName);
    }

    /**
     * Initializes the File Provider integration.
     *
     * @param rootPath the root path for the File Provider domain
     * @throws Exception if initialization fails
     */
    public synchronized void initialize(String rootPath) throws Exception {
        if (isInitialized.get()) {
            throw new IllegalStateException(
                    "FileProviderIntegration is already initialized");
        }

        if (isShutdown.get()) {
            throw new IllegalStateException(
                    "FileProviderIntegration has been shut down");
        }

        if (!isNativeLibraryAvailable()) {
            throw new Exception(
                    "Native File Provider library is not available");
        }

        this.rootPath = rootPath;

        LOGGER.info(
                "Initializing File Provider integration for path: {}",
                rootPath);

        try {
            // Create native File Provider extension
            nativeProviderRef = nativeCreateProvider(
                    domainIdentifier,
                    displayName,
                    rootPath);

            if (nativeProviderRef == 0) {
                throw new Exception(
                        "Failed to create native File Provider extension");
            }

            // Register the domain with the system
            if (!nativeRegisterDomain(nativeProviderRef)) {
                throw new Exception(
                        "Failed to register File Provider domain with system");
            }

            // Initialize fixed thread pool for materialization operations
            // This prevents OutOfMemoryError from unbounded thread creation
            materializationExecutor = Executors.newFixedThreadPool(8, r -> {
                Thread thread = new Thread(
                        r,
                        "FileProvider-Materialize-" + System.currentTimeMillis());
                thread.setDaemon(true);
                return thread;
            });

            isInitialized.set(true);
            LOGGER.info("File Provider integration initialized successfully");
        } catch (Exception e) {
            cleanup();
            throw new Exception(
                    "Failed to initialize File Provider integration: " +
                            e.getMessage(),
                    e);
        }
    }

    /**
     * Shuts down the File Provider integration.
     */
    public synchronized void shutdown() {
        if (isShutdown.get()) {
            return;
        }

        LOGGER.info("Shutting down File Provider integration");

        isShutdown.set(true);
        isInitialized.set(false);

        // Unregister domain from system
        if (nativeProviderRef != 0) {
            try {
                nativeUnregisterDomain(nativeProviderRef);
            } catch (Exception e) {
                LOGGER.error("Error unregistering File Provider domain", e);
            }
        }

        cleanup();
        LOGGER.info("File Provider integration shut down");
    }

    /**
     * Resolves a relative path to an absolute path within the root directory.
     * 
     * @param relativePath the relative path (e.g., "/my/file.txt" or "my/file.txt")
     * @return the absolute path on the filesystem
     */
    private String resolvePath(String relativePath) {
        if (rootPath == null) {
            return relativePath;
        }
        // Remove leading slash if present to avoid treating it as absolute path
        String cleanPath = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;
        return new java.io.File(rootPath, cleanPath).getAbsolutePath();
    }

    /**
     * Creates a placeholder file in the File Provider domain.
     *
     * @param relativePath relative path within the domain
     * @param size         file size in bytes
     * @param lastModified last modification timestamp
     * @param isDirectory  true if this is a directory placeholder
     * @return true if placeholder was created successfully
     */
    public boolean createPlaceholder(
            String relativePath,
            long size,
            long lastModified,
            boolean isDirectory) {
        lock.writeLock().lock();
        try {
            if (!isInitialized.get()) {
                LOGGER.error(
                        "Cannot create placeholder: File Provider not initialized");
                return false;
            }

            LOGGER.debug(
                    "Creating placeholder: path={}, size={}, isDir={}",
                    relativePath,
                    size,
                    isDirectory);

            int itemType = isDirectory
                    ? NSFileProviderItemTypeFolder
                    : NSFileProviderItemTypeData;

            String fullPath = resolvePath(relativePath);

            if (isDirectory) {
                try {
                    Files.createDirectories(Paths.get(fullPath));
                    PlaceholderInfo info = new PlaceholderInfo(
                            relativePath,
                            size,
                            lastModified,
                            isDirectory,
                            defaultMaterializationPolicy,
                            1);
                    placeholders.put(relativePath, info);

                    LOGGER.debug(
                            "Directory placeholder created successfully: {}",
                            relativePath);
                    return true;
                } catch (IOException e) {
                    LOGGER.error("Failed to create directory: {}", fullPath, e);
                    return false;
                }
            }

            if (nativeCreatePlaceholder(
                    nativeProviderRef,
                    fullPath,
                    size,
                    lastModified,
                    itemType,
                    defaultMaterializationPolicy)) {
                PlaceholderInfo info = new PlaceholderInfo(
                        relativePath,
                        size,
                        lastModified,
                        isDirectory,
                        defaultMaterializationPolicy,
                        1);
                placeholders.put(relativePath, info);

                LOGGER.debug(
                        "Placeholder created successfully: {}",
                        relativePath);
                return true;
            } else {
                LOGGER.error(
                        "Failed to create native placeholder for: {}",
                        relativePath);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error(
                    "Error creating placeholder for path: {}",
                    relativePath,
                    e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Registers an existing file as a materialized placeholder.
     * This is used when a file is created locally (e.g. by the user) and we want to
     * register it with the File Provider system without overwriting it with a stub.
     *
     * @param relativePath relative path within the domain
     * @param size         file size in bytes
     * @param lastModified last modification timestamp
     * @param isDirectory  true if this is a directory
     * @return true if registered successfully
     */
    public boolean registerMaterializedFile(
            String relativePath,
            long size,
            long lastModified,
            boolean isDirectory) {
        lock.writeLock().lock();
        try {
            if (!isInitialized.get()) {
                LOGGER.error("Cannot register materialized file: File Provider not initialized");
                return false;
            }

            LOGGER.debug(
                    "Registering materialized file: path={}, size={}, isDir={}",
                    relativePath,
                    size,
                    isDirectory);

            PlaceholderInfo info = new PlaceholderInfo(
                    relativePath,
                    size,
                    lastModified,
                    isDirectory,
                    defaultMaterializationPolicy);
            info.isMaterialized = true; // Mark as already materialized
            placeholders.put(relativePath, info);

            LOGGER.debug("Materialized file registered successfully: {}", relativePath);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error registering materialized file: {}", relativePath, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Materializes a placeholder file by loading its content.
     *
     * @param relativePath relative path of the file to materialize
     * @return true if materialization was successful
     */
    public boolean materializePlaceholder(String relativePath) {
        lock.writeLock().lock();
        try {
            if (!isInitialized.get()) {
                LOGGER.error(
                        "Cannot materialize placeholder: File Provider not initialized");
                return false;
            }

            PlaceholderInfo placeholder = placeholders.get(relativePath);
            if (placeholder == null) {
                LOGGER.error("No placeholder found for path: {}", relativePath);
                return false;
            }

            if (placeholder.isLoaded()) {
                LOGGER.debug(
                        "Placeholder already materialized: {}",
                        relativePath);
                return true;
            }

            if (materializingFiles.contains(relativePath)) {
                LOGGER.debug(
                        "Materialization already in progress for: {}",
                        relativePath);
                return false;
            }

            LOGGER.info("Materializing placeholder asynchronously: {}", relativePath);
            materializingFiles.add(relativePath);

            if (fileChangeCallback != null) {
                fileChangeCallback.loadFileContent(relativePath).thenAcceptAsync(content -> {
                    try {
                        if (content == null && !placeholder.isDirectory()) {
                            LOGGER.error("Failed to load content for file: {}", relativePath);
                            materializingFiles.remove(relativePath);
                            return;
                        }

                        // PUSH content to native File Provider directly from background thread
                        if (nativeMaterializePlaceholder(
                                nativeProviderRef,
                                resolvePath(relativePath),
                                content)) {
                            placeholder.setState(PlaceholderState.LOADED);
                            LOGGER.info("Successfully pushed blockchain content to disk for: {}", relativePath);
                        } else {
                            LOGGER.error("Failed to push content to native layer for: {}", relativePath);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Async materialization error for {}", relativePath, e);
                    } finally {
                        materializingFiles.remove(relativePath);
                    }
                }, materializationExecutor);
                
                return true; // Request accepted and processing in background
            }
            
            materializingFiles.remove(relativePath);
            return false;
        } catch (Exception e) {
            LOGGER.error(
                    "Error materializing placeholder: {}",
                    relativePath,
                    e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates an existing placeholder with new metadata.
     *
     * @param relativePath relative path of the placeholder
     * @param size         new file size
     * @param lastModified new last modification timestamp
     * @return true if update was successful
     */
    public boolean updatePlaceholder(
            String relativePath,
            long size,
            long lastModified) {
        lock.writeLock().lock();
        try {
            if (!isInitialized.get()) {
                return false;
            }

            PlaceholderInfo placeholder = placeholders.get(relativePath);
            if (placeholder == null) {
                return false;
            }

            if (nativeUpdatePlaceholder(
                    nativeProviderRef,
                    resolvePath(relativePath),
                    size,
                    lastModified)) {
                // Update our tracking information
                PlaceholderInfo updated = new PlaceholderInfo(
                        relativePath,
                        size,
                        lastModified,
                        placeholder.isDirectory(),
                        placeholder.getMaterializationPolicy());
                updated.isMaterialized = placeholder.isMaterialized;
                placeholders.put(relativePath, updated);

                LOGGER.debug("Placeholder updated: {}", relativePath);
                return true;
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Error updating placeholder: {}", relativePath, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a placeholder from the File Provider domain.
     *
     * @param relativePath relative path of the placeholder to remove
     * @return true if removal was successful
     */
    public boolean removePlaceholder(String relativePath) {
        lock.writeLock().lock();
        try {
            if (!isInitialized.get()) {
                return false;
            }

            if (nativeRemovePlaceholder(nativeProviderRef, resolvePath(relativePath))) {
                placeholders.remove(relativePath);
                materializingFiles.remove(relativePath);
                LOGGER.debug("Placeholder removed: {}", relativePath);
                return true;
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Error removing placeholder: {}", relativePath, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets the file change callback for content loading.
     *
     * @param callback the callback to handle file loading requests
     */
    public void setFileChangeCallback(FileChangeCallback callback) {
        this.fileChangeCallback = callback;
    }

    /**
     * Sets the in-memory filesystem for synchronization.
     *
     * @param fileSystem the in-memory filesystem
     */
    public void setInMemoryFileSystem(InMemoryFileSystem fileSystem) {
        this.inMemoryFileSystem = fileSystem;
    }

    /**
     * Gets the domain identifier for this File Provider.
     *
     * @return domain identifier
     */
    public String getDomainIdentifier() {
        return domainIdentifier;
    }

    /**
     * Gets the display name for this File Provider.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if the File Provider integration is initialized and ready.
     *
     * @return true if initialized and ready
     */
    public boolean isInitialized() {
        return isInitialized.get() && !isShutdown.get();
    }

    /**
     * Gets information about all current placeholders.
     *
     * @return map of placeholder paths to their information
     */
    public Map<String, String> getPlaceholderInfo() {
        lock.readLock().lock();
        try {
            Map<String, String> info = new ConcurrentHashMap<>();

            for (Map.Entry<String, PlaceholderInfo> entry : placeholders.entrySet()) {
                PlaceholderInfo placeholder = entry.getValue();
                String status = placeholder.isMaterialized
                        ? "materialized"
                        : "placeholder";
                if (materializingFiles.contains(entry.getKey())) {
                    status = "materializing";
                }
                info.put(entry.getKey(), status);
            }

            return info;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Callback method called from native code when a placeholder needs to be
     * materialized.
     * This method is called from the native File Provider extension.
     *
     * @param relativePath the path of the file that needs materialization
     * @return true if materialization request was handled
     */
    private boolean onMaterializationRequest(String relativePath) {
        LOGGER.debug("Materialization requested for: {}", relativePath);

        // Use fixed thread pool instead of creating new threads to prevent
        // OutOfMemoryError
        if (materializationExecutor != null &&
                !materializationExecutor.isShutdown()) {
            materializationExecutor.submit(() -> {
                try {
                    materializePlaceholder(relativePath);
                } catch (Exception e) {
                    LOGGER.error(
                            "Error in background materialization for: {}",
                            relativePath,
                            e);
                }
            });
            return true;
        } else {
            LOGGER.warn(
                    "Materialization executor is not available for: {}",
                    relativePath);
            return false;
        }
    }

    /**
     * Callback method called from native code when a file is evicted from the
     * system.
     *
     * @param relativePath the path of the evicted file
     */
    private void onFileEvicted(String relativePath) {
        lock.writeLock().lock();
        try {
            PlaceholderInfo placeholder = placeholders.get(relativePath);
            if (placeholder != null) {
                placeholder.isMaterialized = false;
                LOGGER.debug(
                        "File evicted, converted back to placeholder: {}",
                        relativePath);
            }

            // Clear from cache if using FileChangeCallback
            if (fileChangeCallback != null) {
                fileChangeCallback.clearCache(relativePath);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cleans up native resources.
     */
    private void cleanup() {
        // Shutdown materialization executor
        if (materializationExecutor != null &&
                !materializationExecutor.isShutdown()) {
            materializationExecutor.shutdown();
            try {
                // Wait for tasks to complete gracefully
                if (!materializationExecutor.awaitTermination(
                        30,
                        TimeUnit.SECONDS)) {
                    LOGGER.warn(
                            "Materialization tasks did not complete in time, forcing shutdown");
                    materializationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                materializationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            materializationExecutor = null;
        }

        if (nativeProviderRef != 0) {
            nativeCleanup(nativeProviderRef);
            nativeProviderRef = 0;
        }

        placeholders.clear();
        materializingFiles.clear();
    }

    // Native method declarations - these are implemented in
    // libf1r3drive-fileprovider.dylib

    /**
     * Checks if the native File Provider library is available.
     *
     * @return true if native library is available
     */
    private native boolean isNativeLibraryAvailable();

    /**
     * Creates a native File Provider extension.
     *
     * @param domainIdentifier the domain identifier
     * @param displayName      the display name
     * @param rootPath         the root path
     * @return native provider reference or 0 on failure
     */
    private native long nativeCreateProvider(
            String domainIdentifier,
            String displayName,
            String rootPath);

    /**
     * Registers the File Provider domain with the system.
     *
     * @param providerRef the native provider reference
     * @return true if registration was successful
     */
    private native boolean nativeRegisterDomain(long providerRef);

    /**
     * Unregisters the File Provider domain from the system.
     *
     * @param providerRef the native provider reference
     */
    private native void nativeUnregisterDomain(long providerRef);

    /**
     * Creates a placeholder file in the native File Provider.
     *
     * @param providerRef           the native provider reference
     * @param relativePath          the relative path
     * @param size                  the file size
     * @param lastModified          the last modification timestamp
     * @param itemType              the item type (file/directory)
     * @param materializationPolicy the materialization policy
     * @return true if placeholder was created
     */
    private native boolean nativeCreatePlaceholder(
            long providerRef,
            String relativePath,
            long size,
            long lastModified,
            int itemType,
            int materializationPolicy);

    /**
     * Materializes a placeholder with content.
     *
     * @param providerRef  the native provider reference
     * @param relativePath the relative path
     * @param content      the file content (may be null for directories)
     * @return true if materialization was successful
     */
    private native boolean nativeMaterializePlaceholder(
            long providerRef,
            String relativePath,
            byte[] content);

    /**
     * Updates placeholder metadata.
     *
     * @param providerRef  the native provider reference
     * @param relativePath the relative path
     * @param size         the new file size
     * @param lastModified the new last modification timestamp
     * @return true if update was successful
     */
    private native boolean nativeUpdatePlaceholder(
            long providerRef,
            String relativePath,
            long size,
            long lastModified);

    /**
     * Removes a placeholder from the File Provider.
     *
     * @param providerRef  the native provider reference
     * @param relativePath the relative path
     * @return true if removal was successful
     */
    private native boolean nativeRemovePlaceholder(
            long providerRef,
            String relativePath);

    /**
     * Cleans up native resources.
     *
     * @param providerRef the native provider reference
     */
    private native void nativeCleanup(long providerRef);

    /**
     * Gets version information about the native File Provider implementation.
     *
     * @return version string
     */
    public native String nativeGetVersion();
}
