package io.f1r3fly.f1r3drive.placeholder;

import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Central component for managing lazy loading of files from blockchain.
 * Provides content caching with various eviction policies, loading priority support,
 * and comprehensive cache usage statistics.
 *
 * This class manages the lifecycle of placeholder files, coordinates on-demand loading
 * from blockchain, and maintains an intelligent cache to optimize performance.
 */
public class PlaceholderManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceholderManager.class);

    // Core components
    private final CacheConfiguration config;
    private final FileChangeCallback fileChangeCallback;
    private final Map<String, PlaceholderInfo> placeholders = new ConcurrentHashMap<>();
    private final Map<String, byte[]> contentCache = new ConcurrentHashMap<>();
    private final Set<String> loadingFiles = ConcurrentHashMap.newKeySet();

    // Priority loading queue
    private final PriorityBlockingQueue<LoadingTask> loadingQueue = new PriorityBlockingQueue<>();
    private final ExecutorService loadingExecutor;
    private final ScheduledExecutorService cleanupExecutor;

    // Cache statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalLoads = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    private final AtomicLong currentCacheSize = new AtomicLong(0);

    // State management
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a PlaceholderManager with default configuration.
     *
     * @param fileChangeCallback callback for loading content from blockchain
     */
    public PlaceholderManager(FileChangeCallback fileChangeCallback) {
        this(fileChangeCallback, CacheConfiguration.defaultConfig());
    }

    /**
     * Creates a PlaceholderManager with custom configuration.
     *
     * @param fileChangeCallback callback for loading content from blockchain
     * @param config cache configuration
     */
    public PlaceholderManager(FileChangeCallback fileChangeCallback, CacheConfiguration config) {
        if (fileChangeCallback == null) {
            throw new IllegalArgumentException("FileChangeCallback cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("CacheConfiguration cannot be null");
        }

        this.fileChangeCallback = fileChangeCallback;
        this.config = config;

        // Create thread pools
        this.loadingExecutor = Executors.newFixedThreadPool(
            config.getLoadingThreads(),
            r -> {
                Thread t = new Thread(r, "PlaceholderLoader");
                t.setDaemon(true);
                return t;
            }
        );

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "PlaceholderCleanup");
                t.setDaemon(true);
                return t;
            }
        );

        // Start background tasks
        startBackgroundTasks();

        LOGGER.info("PlaceholderManager created with config: {}", config);
    }

    /**
     * Creates a placeholder file entry.
     *
     * @param path the file path
     * @param size expected file size in bytes
     * @param checksum expected file checksum (optional)
     * @param priority loading priority (higher = more priority)
     * @return true if placeholder was created successfully
     */
    public boolean createPlaceholder(String path, long size, String checksum, int priority) {
        if (isShutdown.get()) {
            return false;
        }

        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        lock.writeLock().lock();
        try {
            PlaceholderInfo existing = placeholders.get(path);
            if (existing != null && existing.getState() != PlaceholderState.FAILED) {
                LOGGER.debug("Placeholder already exists for path: {}", path);
                return true;
            }

            PlaceholderInfo placeholder = new PlaceholderInfo(
                path, size, checksum, priority, System.currentTimeMillis()
            );

            placeholders.put(path, placeholder);
            LOGGER.debug("Created placeholder: path={}, size={}, priority={}", path, size, priority);

            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads content for a file on-demand.
     *
     * @param path the file path to load
     * @return file content or null if loading failed
     */
    public byte[] loadContent(String path) {
        if (isShutdown.get()) {
            return null;
        }

        // Check cache first
        byte[] cached = contentCache.get(path);
        if (cached != null) {
            cacheHits.incrementAndGet();
            updateAccessTime(path);
            LOGGER.debug("Cache hit for path: {}", path);
            return cached;
        }

        cacheMisses.incrementAndGet();

        // Check if already loading
        if (loadingFiles.contains(path)) {
            LOGGER.debug("File already being loaded: {}", path);
            return waitForLoading(path);
        }

        // Start loading
        return loadContentSync(path);
    }

    /**
     * Preloads a file with specified priority.
     *
     * @param path the file path to preload
     * @param priority loading priority
     */
    public void preloadFile(String path, int priority) {
        if (isShutdown.get()) {
            return;
        }

        if (contentCache.containsKey(path)) {
            LOGGER.debug("File already cached, skipping preload: {}", path);
            return;
        }

        PlaceholderInfo placeholder = placeholders.get(path);
        if (placeholder == null) {
            LOGGER.warn("No placeholder found for preload: {}", path);
            return;
        }

        LoadingTask task = new LoadingTask(path, priority, false);
        loadingQueue.offer(task);

        LOGGER.debug("Queued preload task: path={}, priority={}", path, priority);
    }

    /**
     * Clears cached content for a specific file.
     *
     * @param path the file path to clear from cache
     * @return true if file was in cache and removed
     */
    public boolean clearCache(String path) {
        lock.writeLock().lock();
        try {
            byte[] removed = contentCache.remove(path);
            if (removed != null) {
                currentCacheSize.addAndGet(-removed.length);
                LOGGER.debug("Cleared cache for path: {}", path);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cached content.
     */
    public void clearAllCache() {
        lock.writeLock().lock();
        try {
            contentCache.clear();
            currentCacheSize.set(0);
            LOGGER.info("Cleared all cache content");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets cache usage statistics.
     *
     * @return cache statistics
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            cacheHits.get(),
            cacheMisses.get(),
            currentCacheSize.get(),
            config.getMaxCacheSize(),
            contentCache.size(),
            totalLoads.get(),
            totalEvictions.get(),
            placeholders.size(),
            loadingQueue.size()
        );
    }

    /**
     * Gets information about a specific placeholder.
     *
     * @param path the file path
     * @return placeholder information or null if not found
     */
    public PlaceholderInfo getPlaceholderInfo(String path) {
        return placeholders.get(path);
    }

    /**
     * Gets all placeholder paths.
     *
     * @return set of all placeholder paths
     */
    public Set<String> getAllPlaceholderPaths() {
        return new HashSet<>(placeholders.keySet());
    }

    /**
     * Updates the state of a placeholder.
     *
     * @param path the file path
     * @param newState the new state
     */
    public void updatePlaceholderState(String path, PlaceholderState newState) {
        lock.writeLock().lock();
        try {
            PlaceholderInfo placeholder = placeholders.get(path);
            if (placeholder != null) {
                placeholder.setState(newState);
                placeholder.setLastAccessed(System.currentTimeMillis());
                LOGGER.debug("Updated placeholder state: path={}, state={}", path, newState);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a placeholder.
     *
     * @param path the file path to remove
     * @return true if placeholder was removed
     */
    public boolean removePlaceholder(String path) {
        lock.writeLock().lock();
        try {
            PlaceholderInfo removed = placeholders.remove(path);
            if (removed != null) {
                clearCache(path);
                loadingFiles.remove(path);
                LOGGER.debug("Removed placeholder: {}", path);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a file is currently being loaded.
     *
     * @param path the file path
     * @return true if currently loading
     */
    public boolean isLoading(String path) {
        return loadingFiles.contains(path);
    }

    /**
     * Gets the current configuration.
     *
     * @return cache configuration
     */
    public CacheConfiguration getConfiguration() {
        return config;
    }

    /**
     * Shuts down the placeholder manager and cleans up resources.
     */
    public void cleanup() {
        if (isShutdown.compareAndSet(false, true)) {
            LOGGER.info("Shutting down PlaceholderManager");

            // Shutdown executors
            loadingExecutor.shutdown();
            cleanupExecutor.shutdown();

            try {
                if (!loadingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    loadingExecutor.shutdownNow();
                }
                if (!cleanupExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                loadingExecutor.shutdownNow();
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Clear data structures
            placeholders.clear();
            contentCache.clear();
            loadingFiles.clear();
            loadingQueue.clear();

            LOGGER.info("PlaceholderManager shutdown completed");
        }
    }

    /**
     * Loads content synchronously.
     *
     * @param path the file path to load
     * @return loaded content or null
     */
    private byte[] loadContentSync(String path) {
        loadingFiles.add(path);

        try {
            updatePlaceholderState(path, PlaceholderState.LOADING);

            LOGGER.debug("Loading content from blockchain: {}", path);
            byte[] content = fileChangeCallback.loadFileContent(path);

            if (content != null) {
                totalLoads.incrementAndGet();
                cacheContent(path, content);
                updatePlaceholderState(path, PlaceholderState.LOADED);
                LOGGER.debug("Successfully loaded content: path={}, size={}", path, content.length);
                return content;
            } else {
                updatePlaceholderState(path, PlaceholderState.FAILED);
                LOGGER.warn("Failed to load content from blockchain: {}", path);
                return null;
            }

        } catch (Exception e) {
            updatePlaceholderState(path, PlaceholderState.FAILED);
            LOGGER.error("Error loading content for path: {}", path, e);
            return null;
        } finally {
            loadingFiles.remove(path);
        }
    }

    /**
     * Waits for an already loading file to complete.
     *
     * @param path the file path
     * @return loaded content or null
     */
    private byte[] waitForLoading(String path) {
        int maxWaitMs = config.getLoadTimeoutMs();
        int waitInterval = 100;
        int totalWait = 0;

        while (loadingFiles.contains(path) && totalWait < maxWaitMs) {
            try {
                Thread.sleep(waitInterval);
                totalWait += waitInterval;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        // Check cache after waiting
        return contentCache.get(path);
    }

    /**
     * Caches content with eviction if necessary.
     *
     * @param path the file path
     * @param content the content to cache
     */
    private void cacheContent(String path, byte[] content) {
        if (content.length > config.getMaxFileSize()) {
            LOGGER.debug("File too large to cache: path={}, size={}", path, content.length);
            return;
        }

        lock.writeLock().lock();
        try {
            // Check if we need to evict
            while (currentCacheSize.get() + content.length > config.getMaxCacheSize() && !contentCache.isEmpty()) {
                evictLeastRecentlyUsed();
            }

            contentCache.put(path, content);
            currentCacheSize.addAndGet(content.length);
            updateAccessTime(path);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evicts least recently used content.
     */
    private void evictLeastRecentlyUsed() {
        String lruPath = null;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<String, PlaceholderInfo> entry : placeholders.entrySet()) {
            if (contentCache.containsKey(entry.getKey())) {
                long lastAccess = entry.getValue().getLastAccessed();
                if (lastAccess < oldestAccess) {
                    oldestAccess = lastAccess;
                    lruPath = entry.getKey();
                }
            }
        }

        if (lruPath != null) {
            byte[] evicted = contentCache.remove(lruPath);
            if (evicted != null) {
                currentCacheSize.addAndGet(-evicted.length);
                totalEvictions.incrementAndGet();
                LOGGER.debug("Evicted LRU content: {}", lruPath);
            }
        }
    }

    /**
     * Updates access time for a placeholder.
     *
     * @param path the file path
     */
    private void updateAccessTime(String path) {
        PlaceholderInfo placeholder = placeholders.get(path);
        if (placeholder != null) {
            placeholder.setLastAccessed(System.currentTimeMillis());
        }
    }

    /**
     * Starts background maintenance tasks.
     */
    private void startBackgroundTasks() {
        // Background loading task processor
        loadingExecutor.submit(this::processLoadingQueue);

        // Periodic cleanup task
        cleanupExecutor.scheduleWithFixedDelay(
            this::performCleanup,
            config.getCleanupIntervalMs(),
            config.getCleanupIntervalMs(),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Processes the loading queue in background.
     */
    private void processLoadingQueue() {
        while (!isShutdown.get() && !Thread.currentThread().isInterrupted()) {
            try {
                LoadingTask task = loadingQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processLoadingTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error processing loading queue", e);
            }
        }
    }

    /**
     * Processes a single loading task.
     *
     * @param task the loading task
     */
    private void processLoadingTask(LoadingTask task) {
        if (contentCache.containsKey(task.path)) {
            return; // Already cached
        }

        loadContentSync(task.path);
    }

    /**
     * Performs periodic cleanup tasks.
     */
    private void performCleanup() {
        try {
            long now = System.currentTimeMillis();
            long maxAge = config.getCacheMaxAgeMs();

            lock.writeLock().lock();
            try {
                // Remove expired cached content
                contentCache.entrySet().removeIf(entry -> {
                    PlaceholderInfo info = placeholders.get(entry.getKey());
                    if (info != null && (now - info.getLastAccessed()) > maxAge) {
                        currentCacheSize.addAndGet(-entry.getValue().length);
                        totalEvictions.incrementAndGet();
                        return true;
                    }
                    return false;
                });

                // Clean up failed placeholders older than threshold
                placeholders.entrySet().removeIf(entry -> {
                    PlaceholderInfo info = entry.getValue();
                    return info.getState() == PlaceholderState.FAILED &&
                           (now - info.getCreated()) > config.getFailedPlaceholderMaxAgeMs();
                });

            } finally {
                lock.writeLock().unlock();
            }

            LOGGER.debug("Cleanup completed. Cache size: {}, Placeholders: {}",
                        contentCache.size(), placeholders.size());

        } catch (Exception e) {
            LOGGER.error("Error during cleanup", e);
        }
    }

    /**
     * Loading task for priority queue.
     */
    private static class LoadingTask implements Comparable<LoadingTask> {
        final String path;
        final int priority;
        final boolean urgent;
        final long timestamp;

        LoadingTask(String path, int priority, boolean urgent) {
            this.path = path;
            this.priority = priority;
            this.urgent = urgent;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public int compareTo(LoadingTask other) {
            // Urgent tasks first
            if (this.urgent != other.urgent) {
                return this.urgent ? -1 : 1;
            }

            // Then by priority (higher priority first)
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            // Finally by timestamp (older first)
            return Long.compare(this.timestamp, other.timestamp);
        }
    }

    /**
     * Cache statistics container.
     */
    public static class CacheStatistics {
        private final long cacheHits;
        private final long cacheMisses;
        private final long cacheSize;
        private final long maxCacheSize;
        private final int cachedFilesCount;
        private final long totalLoads;
        private final long totalEvictions;
        private final int placeholderCount;
        private final int queuedLoads;

        public CacheStatistics(long cacheHits, long cacheMisses, long cacheSize, long maxCacheSize,
                              int cachedFilesCount, long totalLoads, long totalEvictions,
                              int placeholderCount, int queuedLoads) {
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheSize = cacheSize;
            this.maxCacheSize = maxCacheSize;
            this.cachedFilesCount = cachedFilesCount;
            this.totalLoads = totalLoads;
            this.totalEvictions = totalEvictions;
            this.placeholderCount = placeholderCount;
            this.queuedLoads = queuedLoads;
        }

        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getCacheSize() { return cacheSize; }
        public long getMaxCacheSize() { return maxCacheSize; }
        public int getCachedFilesCount() { return cachedFilesCount; }
        public long getTotalLoads() { return totalLoads; }
        public long getTotalEvictions() { return totalEvictions; }
        public int getPlaceholderCount() { return placeholderCount; }
        public int getQueuedLoads() { return queuedLoads; }

        public double getHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }

        public double getCacheUtilization() {
            return maxCacheSize > 0 ? (double) cacheSize / maxCacheSize : 0.0;
        }

        @Override
        public String toString() {
            return String.format("CacheStatistics{hits=%d, misses=%d, hitRatio=%.2f%%, " +
                               "cacheSize=%d/%d (%.1f%%), files=%d, loads=%d, evictions=%d, " +
                               "placeholders=%d, queued=%d}",
                cacheHits, cacheMisses, getHitRatio() * 100,
                cacheSize, maxCacheSize, getCacheUtilization() * 100,
                cachedFilesCount, totalLoads, totalEvictions,
                placeholderCount, queuedLoads);
        }
    }
}
