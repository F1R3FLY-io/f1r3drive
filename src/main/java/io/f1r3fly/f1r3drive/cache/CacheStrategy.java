package io.f1r3fly.f1r3drive.cache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Unified cache strategy interface for managing both memory and disk cache layers.
 *
 * This interface provides a coordinated approach to caching that eliminates
 * redundancy between Layer 3 (InMemoryFileSystem disk cache) and
 * Layer 6 (PlaceholderManager memory cache).
 *
 * Cache Hierarchy:
 * - L1 (Memory): Fast access, limited size, managed by Caffeine
 * - L2 (Disk): Larger capacity, persistent across restarts
 *
 * @since Phase 3: Architecture Refinement
 */
public interface CacheStrategy {

    /**
     * Cache levels enumeration for tiered caching strategy.
     */
    enum CacheLevel {
        /** L1 Cache: Memory-based, fastest access */
        MEMORY(1),
        /** L2 Cache: Disk-based, larger capacity */
        DISK(2);

        private final int level;

        CacheLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Cache operation result with metadata.
     */
    class CacheResult {
        private final byte[] content;
        private final CacheLevel hitLevel;
        private final long accessTime;
        private final boolean wasPromoted;

        public CacheResult(byte[] content, CacheLevel hitLevel, long accessTime, boolean wasPromoted) {
            this.content = content;
            this.hitLevel = hitLevel;
            this.accessTime = accessTime;
            this.wasPromoted = wasPromoted;
        }

        public byte[] getContent() { return content; }
        public CacheLevel getHitLevel() { return hitLevel; }
        public long getAccessTime() { return accessTime; }
        public boolean wasPromoted() { return wasPromoted; }
    }

    /**
     * Retrieves content from cache hierarchy.
     *
     * Search order:
     * 1. L1 (Memory) - fastest
     * 2. L2 (Disk) - if not in memory, promote to L1
     * 3. Cache miss - return empty
     *
     * @param filePath the file path to retrieve
     * @return CacheResult with content and cache level hit, or empty if not cached
     */
    Optional<CacheResult> get(String filePath);

    /**
     * Stores content in appropriate cache level based on size and access patterns.
     *
     * Storage strategy:
     * - Small files (< threshold): Store in both L1 and L2
     * - Large files: Store in L2 only
     * - Frequently accessed: Promote to L1
     *
     * @param filePath the file path to cache
     * @param content the file content
     * @param metadata optional metadata for caching decisions
     */
    void put(String filePath, byte[] content, CacheMetadata metadata);

    /**
     * Invalidates content from all cache levels.
     *
     * @param filePath the file path to invalidate
     * @return true if content was present and removed
     */
    boolean invalidate(String filePath);

    /**
     * Invalidates all cached content.
     */
    void invalidateAll();

    /**
     * Promotes content from L2 (disk) to L1 (memory) cache.
     *
     * @param filePath the file path to promote
     * @return true if promotion was successful
     */
    boolean promote(String filePath);

    /**
     * Gets unified cache statistics across all levels.
     *
     * @return CacheStatistics aggregated from all cache levels
     */
    UnifiedCacheStatistics getStatistics();

    /**
     * Checks if content exists in any cache level.
     *
     * @param filePath the file path to check
     * @return true if content exists in any cache level
     */
    boolean contains(String filePath);

    /**
     * Gets the disk cache directory path.
     *
     * @return Path to disk cache directory
     */
    Path getDiskCacheDirectory();

    /**
     * Performs cache maintenance operations.
     *
     * This includes:
     * - L1 cache cleanup (handled by Caffeine)
     * - L2 cache cleanup (remove expired/corrupted files)
     * - Cache consistency verification
     */
    void performMaintenance();

    /**
     * Metadata for cache storage decisions.
     */
    class CacheMetadata {
        private final int priority;
        private final boolean persistent;
        private final long expectedAccessFrequency;
        private final String contentType;

        public CacheMetadata(int priority, boolean persistent, long expectedAccessFrequency, String contentType) {
            this.priority = priority;
            this.persistent = persistent;
            this.expectedAccessFrequency = expectedAccessFrequency;
            this.contentType = contentType;
        }

        public static CacheMetadata defaultMetadata() {
            return new CacheMetadata(0, true, 1, "application/octet-stream");
        }

        public static CacheMetadata highPriority() {
            return new CacheMetadata(10, true, 100, "application/octet-stream");
        }

        public int getPriority() { return priority; }
        public boolean isPersistent() { return persistent; }
        public long getExpectedAccessFrequency() { return expectedAccessFrequency; }
        public String getContentType() { return contentType; }
    }

    /**
     * Unified cache statistics across all cache levels.
     */
    class UnifiedCacheStatistics {
        private final long l1Hits;
        private final long l2Hits;
        private final long misses;
        private final long l1Size;
        private final long l2Size;
        private final long promotions;
        private final long evictions;

        public UnifiedCacheStatistics(long l1Hits, long l2Hits, long misses,
                                    long l1Size, long l2Size, long promotions, long evictions) {
            this.l1Hits = l1Hits;
            this.l2Hits = l2Hits;
            this.misses = misses;
            this.l1Size = l1Size;
            this.l2Size = l2Size;
            this.promotions = promotions;
            this.evictions = evictions;
        }

        public long getTotalHits() { return l1Hits + l2Hits; }
        public long getL1Hits() { return l1Hits; }
        public long getL2Hits() { return l2Hits; }
        public long getMisses() { return misses; }
        public long getL1Size() { return l1Size; }
        public long getL2Size() { return l2Size; }
        public long getPromotions() { return promotions; }
        public long getEvictions() { return evictions; }

        public double getHitRatio() {
            long total = getTotalHits() + misses;
            return total > 0 ? (double) getTotalHits() / total : 0.0;
        }

        public double getL1HitRatio() {
            long total = getTotalHits() + misses;
            return total > 0 ? (double) l1Hits / total : 0.0;
        }
    }
}
