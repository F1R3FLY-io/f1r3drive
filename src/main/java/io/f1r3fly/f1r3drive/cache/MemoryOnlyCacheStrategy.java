package io.f1r3fly.f1r3drive.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory-only cache strategy using only Caffeine cache in RAM.
 *
 * This strategy provides:
 * - Pure in-memory caching with no disk persistence
 * - Fast access times (~1ms) for all cached content
 * - Automatic LRU eviction when memory limits are reached
 * - No file I/O overhead, suitable for high-performance scenarios
 *
 * Use cases:
 * - When disk I/O should be avoided
 * - Temporary caching scenarios
 * - High-frequency access patterns where speed > persistence
 * - Development/testing environments
 *
 * @since Phase 3: Architecture Refinement
 */
public class MemoryOnlyCacheStrategy implements CacheStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryOnlyCacheStrategy.class);

    // Configuration
    private final long maxCacheSize;
    private final long expireAfterAccessMs;

    // Memory cache (Caffeine)
    private final Cache<String, byte[]> memoryCache;

    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);

    /**
     * Creates a memory-only cache strategy.
     *
     * @param maxCacheSize maximum cache size in bytes
     * @param expireAfterAccessMs expiration time after last access
     */
    public MemoryOnlyCacheStrategy(long maxCacheSize, long expireAfterAccessMs) {
        this.maxCacheSize = maxCacheSize;
        this.expireAfterAccessMs = expireAfterAccessMs;

        // Initialize Caffeine cache
        this.memoryCache = Caffeine.newBuilder()
            .maximumWeight(maxCacheSize)
            .weigher((String key, byte[] value) -> value.length)
            .expireAfterAccess(expireAfterAccessMs, TimeUnit.MILLISECONDS)
            .removalListener((RemovalListener<String, byte[]>) (key, value, cause) -> {
                if (value != null) {
                    totalEvictions.incrementAndGet();
                    LOGGER.debug("Memory cache eviction: path={}, size={}, cause={}",
                        key, value.length, cause);
                }
            })
            .recordStats()
            .build();

        LOGGER.info("MemoryOnlyCacheStrategy initialized: maxSize={}MB, expireAfter={}ms",
            maxCacheSize / (1024 * 1024), expireAfterAccessMs);
    }

    @Override
    public Optional<CacheResult> get(String filePath) {
        long startTime = System.currentTimeMillis();

        byte[] content = memoryCache.getIfPresent(filePath);
        if (content != null) {
            cacheHits.incrementAndGet();
            LOGGER.debug("Memory cache hit: {} (size={})", filePath, content.length);
            return Optional.of(new CacheResult(content, CacheLevel.MEMORY, startTime, false));
        }

        // Cache miss
        cacheMisses.incrementAndGet();
        LOGGER.debug("Memory cache miss: {}", filePath);
        return Optional.empty();
    }

    @Override
    public void put(String filePath, byte[] content, CacheMetadata metadata) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        long contentSize = content.length;

        // Check if content fits in cache
        if (contentSize > maxCacheSize) {
            LOGGER.warn("Content too large for memory cache: {} (size={}MB, maxSize={}MB)",
                filePath, contentSize / (1024 * 1024), maxCacheSize / (1024 * 1024));
            return;
        }

        // Store in memory cache
        memoryCache.put(filePath, content);
        LOGGER.debug("Content cached in memory: {} (size={})", filePath, contentSize);
    }

    @Override
    public boolean invalidate(String filePath) {
        boolean existed = memoryCache.getIfPresent(filePath) != null;
        if (existed) {
            memoryCache.invalidate(filePath);
            LOGGER.debug("Invalidated from memory cache: {}", filePath);
        }
        return existed;
    }

    @Override
    public void invalidateAll() {
        long sizeBefore = memoryCache.estimatedSize();
        memoryCache.invalidateAll();
        LOGGER.info("Invalidated all memory cache content: {} items removed", sizeBefore);
    }

    @Override
    public boolean promote(String filePath) {
        // In memory-only strategy, promotion is a no-op since everything is already in memory
        return memoryCache.getIfPresent(filePath) != null;
    }

    @Override
    public UnifiedCacheStatistics getStatistics() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = memoryCache.stats();

        return new UnifiedCacheStatistics(
            cacheHits.get(),           // l1Hits (memory hits)
            0,                         // l2Hits (no disk cache)
            cacheMisses.get(),         // misses
            memoryCache.estimatedSize(), // l1Size
            0,                         // l2Size (no disk cache)
            0,                         // promotions (no L2 to promote from)
            totalEvictions.get()       // evictions
        );
    }

    @Override
    public boolean contains(String filePath) {
        return memoryCache.getIfPresent(filePath) != null;
    }

    @Override
    public Path getDiskCacheDirectory() {
        // No disk cache in memory-only strategy
        return Paths.get("/dev/null"); // Placeholder path
    }

    @Override
    public void performMaintenance() {
        LOGGER.debug("Performing memory cache maintenance...");

        // Trigger Caffeine's built-in cleanup
        memoryCache.cleanUp();

        UnifiedCacheStatistics stats = getStatistics();
        LOGGER.debug("Memory cache maintenance completed. Size: {}, Hit ratio: {:.2f}%",
            stats.getL1Size(), stats.getHitRatio() * 100);
    }

    /**
     * Gets the current memory usage statistics.
     *
     * @return memory usage information
     */
    public MemoryUsageStats getMemoryUsage() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = memoryCache.stats();

        return new MemoryUsageStats(
            memoryCache.estimatedSize(),
            maxCacheSize,
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount()
        );
    }

    /**
     * Memory usage statistics for monitoring.
     */
    public static class MemoryUsageStats {
        private final long currentSize;
        private final long maxSize;
        private final long hits;
        private final long misses;
        private final long evictions;

        public MemoryUsageStats(long currentSize, long maxSize, long hits, long misses, long evictions) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
        }

        public long getCurrentSize() { return currentSize; }
        public long getMaxSize() { return maxSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getEvictions() { return evictions; }

        public double getUtilization() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }

        public double getHitRatio() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "MemoryUsage{size=%d/%d (%.1f%%), hits=%d, misses=%d, hitRatio=%.2f%%, evictions=%d}",
                currentSize, maxSize, getUtilization() * 100,
                hits, misses, getHitRatio() * 100, evictions
            );
        }
    }
}
