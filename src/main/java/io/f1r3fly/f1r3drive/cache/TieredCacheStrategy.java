package io.f1r3fly.f1r3drive.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tiered cache strategy implementation with L1 memory cache and L2 disk cache.
 *
 * This implementation provides:
 * - L1 Cache: Caffeine-based memory cache for fast access
 * - L2 Cache: File-based disk cache for larger capacity and persistence
 * - Automatic promotion from L2 to L1 on access
 * - Unified cache statistics and management
 *
 * Cache Storage Strategy:
 * - Files < memoryThreshold: Stored in both L1 and L2
 * - Files >= memoryThreshold: Stored in L2 only
 * - Hot files: Automatically promoted to L1
 *
 * Tiered cache implementation with memory and disk layers
 */
public class TieredCacheStrategy implements CacheStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        TieredCacheStrategy.class
    );

    // Configuration
    private final long maxMemoryCacheSize;
    private final long memoryThreshold;
    private final Path diskCacheDirectory;
    private final long maxDiskCacheSize;

    // L1 Cache: Memory (Caffeine)
    private final Cache<String, byte[]> memoryCache;

    // L2 Cache: Disk management
    private final DiskCacheManager diskCacheManager;

    // Statistics
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong promotions = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);

    /**
     * Creates a TieredCacheStrategy with specified configuration.
     *
     * @param maxMemoryCacheSize maximum size for L1 memory cache in bytes
     * @param memoryThreshold files larger than this are stored in L2 only
     * @param diskCacheDirectory directory for L2 disk cache
     * @param maxDiskCacheSize maximum size for L2 disk cache in bytes
     * @param expireAfterAccessMs expiration time for memory cache entries
     */
    public TieredCacheStrategy(
        long maxMemoryCacheSize,
        long memoryThreshold,
        Path diskCacheDirectory,
        long maxDiskCacheSize,
        long expireAfterAccessMs
    ) {
        this.maxMemoryCacheSize = maxMemoryCacheSize;
        this.memoryThreshold = memoryThreshold;
        this.diskCacheDirectory = diskCacheDirectory;
        this.maxDiskCacheSize = maxDiskCacheSize;

        // Initialize disk cache directory
        initializeDiskCache();

        // Initialize L1 memory cache with Caffeine
        this.memoryCache = Caffeine.newBuilder()
            .maximumWeight(maxMemoryCacheSize)
            .weigher((String key, byte[] value) -> value.length)
            .expireAfterAccess(expireAfterAccessMs, TimeUnit.MILLISECONDS)
            .removalListener(
                (RemovalListener<String, byte[]>) (key, value, cause) -> {
                    if (value != null) {
                        totalEvictions.incrementAndGet();
                        LOGGER.debug(
                            "L1 cache eviction: path={}, size={}, cause={}",
                            key,
                            value.length,
                            cause
                        );
                    }
                }
            )
            .recordStats()
            .build();

        // Initialize L2 disk cache manager
        this.diskCacheManager = new DiskCacheManager(
            diskCacheDirectory,
            maxDiskCacheSize
        );

        LOGGER.info(
            "TieredCacheStrategy initialized: L1={}MB, L2={}MB, threshold={}KB",
            maxMemoryCacheSize / (1024 * 1024),
            maxDiskCacheSize / (1024 * 1024),
            memoryThreshold / 1024
        );
    }

    @Override
    public Optional<CacheResult> get(String filePath) {
        long startTime = System.currentTimeMillis();

        // Try L1 (memory) cache first
        byte[] l1Content = memoryCache.getIfPresent(filePath);
        if (l1Content != null) {
            l1Hits.incrementAndGet();
            LOGGER.debug("L1 cache hit: {}", filePath);
            return Optional.of(
                new CacheResult(l1Content, CacheLevel.MEMORY, startTime, false)
            );
        }

        // Try L2 (disk) cache
        Optional<byte[]> l2Content = diskCacheManager.get(filePath);
        if (l2Content.isPresent()) {
            l2Hits.incrementAndGet();
            byte[] content = l2Content.get();

            // Promote to L1 if content is small enough
            boolean promoted = false;
            if (content.length < memoryThreshold) {
                memoryCache.put(filePath, content);
                promotions.incrementAndGet();
                promoted = true;
                LOGGER.debug(
                    "L2 cache hit with L1 promotion: {} (size={})",
                    filePath,
                    content.length
                );
            } else {
                LOGGER.debug(
                    "L2 cache hit (no promotion, size too large): {} (size={})",
                    filePath,
                    content.length
                );
            }

            return Optional.of(
                new CacheResult(content, CacheLevel.DISK, startTime, promoted)
            );
        }

        // Cache miss
        cacheMisses.incrementAndGet();
        LOGGER.debug("Cache miss: {}", filePath);
        return Optional.empty();
    }

    @Override
    public void put(String filePath, byte[] content, CacheMetadata metadata) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        long contentSize = content.length;
        LOGGER.debug("Caching content: {} (size={})", filePath, contentSize);

        // Always store in L2 (disk) for persistence
        try {
            diskCacheManager.put(filePath, content);
        } catch (IOException e) {
            LOGGER.warn("Failed to cache content in L2: {}", filePath, e);
        }

        // Store in L1 (memory) if content is small enough or high priority
        if (contentSize < memoryThreshold || metadata.getPriority() >= 10) {
            memoryCache.put(filePath, content);
            LOGGER.debug(
                "Content cached in both L1 and L2: {} (size={})",
                filePath,
                contentSize
            );
        } else {
            LOGGER.debug(
                "Content cached in L2 only: {} (size={})",
                filePath,
                contentSize
            );
        }
    }

    @Override
    public boolean invalidate(String filePath) {
        boolean l1Removed = memoryCache.getIfPresent(filePath) != null;
        boolean l2Removed = diskCacheManager.invalidate(filePath);

        if (l1Removed) {
            memoryCache.invalidate(filePath);
        }

        boolean removed = l1Removed || l2Removed;
        if (removed) {
            LOGGER.debug(
                "Invalidated from cache: {} (L1={}, L2={})",
                filePath,
                l1Removed,
                l2Removed
            );
        }

        return removed;
    }

    @Override
    public void invalidateAll() {
        memoryCache.invalidateAll();
        diskCacheManager.invalidateAll();
        LOGGER.info("Invalidated all cache content (L1 + L2)");
    }

    @Override
    public boolean promote(String filePath) {
        // Check if already in L1
        if (memoryCache.getIfPresent(filePath) != null) {
            return true; // Already promoted
        }

        // Try to load from L2 and promote
        Optional<byte[]> content = diskCacheManager.get(filePath);
        if (content.isPresent() && content.get().length < memoryThreshold) {
            memoryCache.put(filePath, content.get());
            promotions.incrementAndGet();
            LOGGER.debug(
                "Promoted to L1: {} (size={})",
                filePath,
                content.get().length
            );
            return true;
        }

        return false;
    }

    @Override
    public UnifiedCacheStatistics getStatistics() {
        return new UnifiedCacheStatistics(
            l1Hits.get(),
            l2Hits.get(),
            cacheMisses.get(),
            memoryCache.estimatedSize(),
            diskCacheManager.estimatedSize(),
            promotions.get(),
            totalEvictions.get()
        );
    }

    @Override
    public boolean contains(String filePath) {
        return (
            memoryCache.getIfPresent(filePath) != null ||
            diskCacheManager.contains(filePath)
        );
    }

    @Override
    public Path getDiskCacheDirectory() {
        return diskCacheDirectory;
    }

    @Override
    public void performMaintenance() {
        LOGGER.debug("Performing cache maintenance...");

        // L1 maintenance is handled by Caffeine automatically
        memoryCache.cleanUp();

        // L2 maintenance
        diskCacheManager.performMaintenance();

        LOGGER.debug("Cache maintenance completed");
    }

    private void initializeDiskCache() {
        try {
            Files.createDirectories(diskCacheDirectory);
            LOGGER.info(
                "Disk cache directory initialized: {}",
                diskCacheDirectory
            );
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to initialize disk cache directory: " +
                    diskCacheDirectory,
                e
            );
        }
    }

    /**
     * Disk cache manager for L2 cache operations.
     */
    private static class DiskCacheManager {

        private static final Logger LOGGER = LoggerFactory.getLogger(
            DiskCacheManager.class
        );
        private static final String CACHE_FILE_EXTENSION = ".cache";

        private final Path cacheDirectory;
        private final long maxCacheSize;

        public DiskCacheManager(Path cacheDirectory, long maxCacheSize) {
            this.cacheDirectory = cacheDirectory;
            this.maxCacheSize = maxCacheSize;
        }

        public Optional<byte[]> get(String filePath) {
            Path cacheFile = getCacheFilePath(filePath);

            if (!Files.exists(cacheFile)) {
                return Optional.empty();
            }

            try {
                byte[] content = Files.readAllBytes(cacheFile);
                // Update access time
                Files.setLastModifiedTime(
                    cacheFile,
                    java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis()
                    )
                );
                return Optional.of(content);
            } catch (IOException e) {
                LOGGER.warn("Failed to read from disk cache: {}", cacheFile, e);
                return Optional.empty();
            }
        }

        public void put(String filePath, byte[] content) throws IOException {
            Path cacheFile = getCacheFilePath(filePath);
            Files.createDirectories(cacheFile.getParent());
            Files.write(
                cacheFile,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }

        public boolean invalidate(String filePath) {
            Path cacheFile = getCacheFilePath(filePath);
            try {
                return Files.deleteIfExists(cacheFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete cache file: {}", cacheFile, e);
                return false;
            }
        }

        public void invalidateAll() {
            try {
                Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path ->
                        path.toString().endsWith(CACHE_FILE_EXTENSION)
                    )
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            LOGGER.warn(
                                "Failed to delete cache file: {}",
                                path,
                                e
                            );
                        }
                    });
            } catch (IOException e) {
                LOGGER.warn(
                    "Failed to clear disk cache directory: {}",
                    cacheDirectory,
                    e
                );
            }
        }

        public boolean contains(String filePath) {
            return Files.exists(getCacheFilePath(filePath));
        }

        public long estimatedSize() {
            try {
                return Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path ->
                        path.toString().endsWith(CACHE_FILE_EXTENSION)
                    )
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            } catch (IOException e) {
                LOGGER.warn("Failed to calculate disk cache size", e);
                return 0;
            }
        }

        public void performMaintenance() {
            // TODO: Implement LRU cleanup based on file access times if cache exceeds maxCacheSize
            LOGGER.debug(
                "L2 cache maintenance - current size: {} bytes",
                estimatedSize()
            );
        }

        private Path getCacheFilePath(String filePath) {
            // Convert file path to safe cache file name
            String safeName = filePath
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_");
            return cacheDirectory.resolve(safeName + CACHE_FILE_EXTENSION);
        }
    }
}
