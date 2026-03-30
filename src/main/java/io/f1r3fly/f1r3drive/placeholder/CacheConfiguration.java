package io.f1r3fly.f1r3drive.placeholder;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class for PlaceholderManager cache management.
 * Provides comprehensive settings for cache behavior, eviction policies,
 * loading priorities, and resource limits.
 */
public class CacheConfiguration {

    // Default configuration values
    private static final long DEFAULT_MAX_CACHE_SIZE = 512 * 1024 * 1024; // 512MB
    private static final long DEFAULT_MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long DEFAULT_CACHE_MAX_AGE_MS =
        TimeUnit.HOURS.toMillis(24); // 24 hours
    private static final long DEFAULT_FAILED_PLACEHOLDER_MAX_AGE_MS =
        TimeUnit.HOURS.toMillis(1); // 1 hour
    private static final long DEFAULT_CLEANUP_INTERVAL_MS =
        TimeUnit.MINUTES.toMillis(10); // 10 minutes
    private static final int DEFAULT_LOADING_THREADS = 4;
    private static final int DEFAULT_LOAD_TIMEOUT_MS = 30000; // 30 seconds
    private static final int DEFAULT_MAX_LOAD_ATTEMPTS = 3;

    // Caffeine-specific defaults
    private static final int DEFAULT_CAFFEINE_INITIAL_CAPACITY = 1000;
    private static final double DEFAULT_CACHE_UTILIZATION_THRESHOLD = 0.8;
    private static final int DEFAULT_PRELOAD_BATCH_SIZE = 50;

    // Cache size limits
    private final long maxCacheSize;
    private final long maxFileSize;
    private final int maxCachedFiles;

    // Cache expiration settings
    private final long cacheMaxAgeMs;
    private final long failedPlaceholderMaxAgeMs;

    // Loading configuration
    private final int loadingThreads;
    private final int loadTimeoutMs;
    private final int maxLoadAttempts;

    // Maintenance settings
    private final long cleanupIntervalMs;
    private final EvictionPolicy evictionPolicy;
    private final boolean enablePreloading;
    private final boolean enableStatistics;

    // Performance tuning
    private final double cacheUtilizationThreshold;
    private final int preloadBatchSize;

    // Phase 3: Cache strategy selection
    private final CacheStrategyType cacheStrategyType;
    private final boolean enableDiskCache;
    private final String diskCacheDirectory;
    private final long diskCacheMultiplier;

    /**
     * Cache strategy types available in Phase 3.
     */
    public enum CacheStrategyType {
        /** Memory-only cache using Caffeine (fastest, no persistence) */
        MEMORY_ONLY,

        /** Tiered cache with L1 memory + L2 disk (balanced performance + persistence) */
        TIERED,

        /** Legacy Caffeine cache (Phase 2 compatibility) */
        LEGACY_CAFFEINE,
    }

    // Caffeine-specific settings
    private final int caffeineInitialCapacity;
    private final boolean enableCaffeineStats;
    private final boolean enableWeakKeys;
    private final boolean enableSoftValues;

    /**
     * Creates a CacheConfiguration with all parameters.
     */
    private CacheConfiguration(Builder builder) {
        this.maxCacheSize = builder.maxCacheSize;
        this.maxFileSize = builder.maxFileSize;
        this.maxCachedFiles = builder.maxCachedFiles;
        this.cacheMaxAgeMs = builder.cacheMaxAgeMs;
        this.failedPlaceholderMaxAgeMs = builder.failedPlaceholderMaxAgeMs;
        this.loadingThreads = builder.loadingThreads;
        this.loadTimeoutMs = builder.loadTimeoutMs;
        this.maxLoadAttempts = builder.maxLoadAttempts;
        this.cleanupIntervalMs = builder.cleanupIntervalMs;
        this.evictionPolicy = builder.evictionPolicy;
        this.enablePreloading = builder.enablePreloading;
        this.enableStatistics = builder.enableStatistics;
        this.cacheUtilizationThreshold = builder.cacheUtilizationThreshold;
        this.preloadBatchSize = builder.preloadBatchSize;
        this.caffeineInitialCapacity = builder.caffeineInitialCapacity;
        this.enableCaffeineStats = builder.enableCaffeineStats;
        this.enableWeakKeys = builder.enableWeakKeys;
        this.enableSoftValues = builder.enableSoftValues;

        // Phase 3: Cache strategy configuration
        this.cacheStrategyType = builder.cacheStrategyType;
        this.enableDiskCache = builder.enableDiskCache;
        this.diskCacheDirectory = builder.diskCacheDirectory;
        this.diskCacheMultiplier = builder.diskCacheMultiplier;
    }

    /**
     * Creates a default configuration suitable for most use cases.
     *
     * @return default cache configuration
     */
    public static CacheConfiguration defaultConfig() {
        return new Builder().build();
    }

    /**
     * Creates a memory-only cache configuration for high-performance scenarios.
     */
    public static CacheConfiguration memoryOnlyConfig() {
        return new Builder()
            .withCacheStrategyType(CacheStrategyType.MEMORY_ONLY)
            .withEnableDiskCache(false)
            .build();
    }

    /**
     * Creates a tiered cache configuration for balanced performance and persistence.
     */
    public static CacheConfiguration tieredConfig() {
        return new Builder()
            .withCacheStrategyType(CacheStrategyType.TIERED)
            .withEnableDiskCache(true)
            .withDiskCacheMultiplier(10)
            .build();
    }

    /**
     * Creates a configuration optimized for low memory environments.
     *
     * @return low memory configuration
     */
    public static CacheConfiguration lowMemoryConfig() {
        return new Builder()
            .maxCacheSize(128 * 1024 * 1024) // 128MB
            .maxFileSize(50 * 1024 * 1024) // 50MB
            .maxCachedFiles(100)
            .loadingThreads(2)
            .enablePreloading(false)
            .build();
    }

    /**
     * Creates a configuration optimized for high performance environments.
     *
     * @return high performance configuration
     */
    public static CacheConfiguration highPerformanceConfig() {
        return new Builder()
            .maxCacheSize(2L * 1024 * 1024 * 1024) // 2GB
            .maxFileSize(500 * 1024 * 1024) // 500MB
            .maxCachedFiles(10000)
            .loadingThreads(8)
            .enablePreloading(true)
            .preloadBatchSize(20)
            .cleanupIntervalMs(TimeUnit.MINUTES.toMillis(5))
            .build();
    }

    /**
     * Creates a builder for custom configuration.
     *
     * @return configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters for all configuration values

    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public int getMaxCachedFiles() {
        return maxCachedFiles;
    }

    public long getCacheMaxAgeMs() {
        return cacheMaxAgeMs;
    }

    public long getFailedPlaceholderMaxAgeMs() {
        return failedPlaceholderMaxAgeMs;
    }

    public int getLoadingThreads() {
        return loadingThreads;
    }

    public int getLoadTimeoutMs() {
        return loadTimeoutMs;
    }

    public int getMaxLoadAttempts() {
        return maxLoadAttempts;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public boolean isPreloadingEnabled() {
        return enablePreloading;
    }

    public boolean isStatisticsEnabled() {
        return enableStatistics;
    }

    public double getCacheUtilizationThreshold() {
        return cacheUtilizationThreshold;
    }

    public int getPreloadBatchSize() {
        return preloadBatchSize;
    }

    /**
     * Validates the configuration parameters.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException(
                "Max cache size must be positive"
            );
        }
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException(
                "Max file size must be positive"
            );
        }
        if (maxFileSize > maxCacheSize) {
            throw new IllegalArgumentException(
                "Max file size cannot exceed max cache size"
            );
        }
        if (loadingThreads <= 0) {
            throw new IllegalArgumentException(
                "Loading threads must be positive"
            );
        }
        if (loadTimeoutMs <= 0) {
            throw new IllegalArgumentException("Load timeout must be positive");
        }
        if (maxLoadAttempts <= 0) {
            throw new IllegalArgumentException(
                "Max load attempts must be positive"
            );
        }
        if (cleanupIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "Cleanup interval must be positive"
            );
        }
        if (
            cacheUtilizationThreshold < 0.0 || cacheUtilizationThreshold > 1.0
        ) {
            throw new IllegalArgumentException(
                "Cache utilization threshold must be between 0.0 and 1.0"
            );
        }
        if (preloadBatchSize <= 0) {
            throw new IllegalArgumentException(
                "Preload batch size must be positive"
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CacheConfiguration{" +
                "maxCacheSize=%d, maxFileSize=%d, maxCachedFiles=%d, " +
                "cacheMaxAgeMs=%d, loadingThreads=%d, loadTimeoutMs=%d, " +
                "evictionPolicy=%s, preloading=%s, statistics=%s}",
            maxCacheSize,
            maxFileSize,
            maxCachedFiles,
            cacheMaxAgeMs,
            loadingThreads,
            loadTimeoutMs,
            evictionPolicy,
            enablePreloading,
            enableStatistics
        );
    }

    /**
     * Cache eviction policy enumeration.
     */
    public enum EvictionPolicy {
        /**
         * Least Recently Used - evict files that haven't been accessed recently.
         */
        LRU("Least Recently Used"),

        /**
         * Least Frequently Used - evict files that are accessed least often.
         */
        LFU("Least Frequently Used"),

        /**
         * First In, First Out - evict files in order they were cached.
         */
        FIFO("First In, First Out"),

        /**
         * Size-based - evict largest files first to free maximum space.
         */
        SIZE_BASED("Size-based eviction");

        private final String description;

        EvictionPolicy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Builder class for creating CacheConfiguration instances.
     */
    // Caffeine-specific getters
    public int getCaffeineInitialCapacity() {
        return caffeineInitialCapacity;
    }

    public boolean isEnableCaffeineStats() {
        return enableCaffeineStats;
    }

    public boolean isEnableWeakKeys() {
        return enableWeakKeys;
    }

    public boolean isEnableSoftValues() {
        return enableSoftValues;
    }

    // Phase 3: Cache strategy configuration getters
    public CacheStrategyType getCacheStrategyType() {
        return cacheStrategyType;
    }

    public boolean isEnableDiskCache() {
        return enableDiskCache;
    }

    public String getDiskCacheDirectory() {
        return diskCacheDirectory;
    }

    public long getDiskCacheMultiplier() {
        return diskCacheMultiplier;
    }

    /**
     * Creates a builder from existing configuration for modification.
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.maxCacheSize = this.maxCacheSize;
        builder.maxFileSize = this.maxFileSize;
        builder.maxCachedFiles = this.maxCachedFiles;
        builder.cacheMaxAgeMs = this.cacheMaxAgeMs;
        builder.failedPlaceholderMaxAgeMs = this.failedPlaceholderMaxAgeMs;
        builder.loadingThreads = this.loadingThreads;
        builder.loadTimeoutMs = this.loadTimeoutMs;
        builder.maxLoadAttempts = this.maxLoadAttempts;
        builder.cleanupIntervalMs = this.cleanupIntervalMs;
        builder.evictionPolicy = this.evictionPolicy;
        builder.enablePreloading = this.enablePreloading;
        builder.enableStatistics = this.enableStatistics;
        builder.cacheUtilizationThreshold = this.cacheUtilizationThreshold;
        builder.preloadBatchSize = this.preloadBatchSize;
        builder.caffeineInitialCapacity = this.caffeineInitialCapacity;
        builder.enableCaffeineStats = this.enableCaffeineStats;
        builder.enableWeakKeys = this.enableWeakKeys;
        builder.enableSoftValues = this.enableSoftValues;
        builder.cacheStrategyType = this.cacheStrategyType;
        builder.enableDiskCache = this.enableDiskCache;
        builder.diskCacheDirectory = this.diskCacheDirectory;
        builder.diskCacheMultiplier = this.diskCacheMultiplier;
        return builder;
    }

    /**
     * Builder class for creating CacheConfiguration instances.
     */
    public static class Builder {

        private long maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
        private long maxFileSize = DEFAULT_MAX_FILE_SIZE;
        private int maxCachedFiles = Integer.MAX_VALUE;
        private long cacheMaxAgeMs = DEFAULT_CACHE_MAX_AGE_MS;
        private long failedPlaceholderMaxAgeMs =
            DEFAULT_FAILED_PLACEHOLDER_MAX_AGE_MS;
        private int loadingThreads = DEFAULT_LOADING_THREADS;
        private int loadTimeoutMs = DEFAULT_LOAD_TIMEOUT_MS;
        private int maxLoadAttempts = DEFAULT_MAX_LOAD_ATTEMPTS;
        private long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;
        private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
        private boolean enablePreloading = true;
        private boolean enableStatistics = true;
        private double cacheUtilizationThreshold =
            DEFAULT_CACHE_UTILIZATION_THRESHOLD;
        private int preloadBatchSize = DEFAULT_PRELOAD_BATCH_SIZE;

        // Caffeine-specific builder fields
        // Caffeine-specific settings
        private int caffeineInitialCapacity = DEFAULT_CAFFEINE_INITIAL_CAPACITY;
        private boolean enableCaffeineStats = true;
        private boolean enableWeakKeys = false;
        private boolean enableSoftValues = false;

        // Phase 3: Cache strategy settings
        private CacheStrategyType cacheStrategyType = CacheStrategyType.TIERED;
        private boolean enableDiskCache = true;
        private String diskCacheDirectory = null; // Auto-detect if null
        private long diskCacheMultiplier = 10; // L2 cache = 10x L1 cache

        public Builder maxCacheSize(long maxCacheSize) {
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public Builder maxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder maxCachedFiles(int maxCachedFiles) {
            this.maxCachedFiles = maxCachedFiles;
            return this;
        }

        public Builder cacheMaxAge(long duration, TimeUnit unit) {
            this.cacheMaxAgeMs = unit.toMillis(duration);
            return this;
        }

        public Builder cacheMaxAgeMs(long cacheMaxAgeMs) {
            this.cacheMaxAgeMs = cacheMaxAgeMs;
            return this;
        }

        public Builder failedPlaceholderMaxAge(long duration, TimeUnit unit) {
            this.failedPlaceholderMaxAgeMs = unit.toMillis(duration);
            return this;
        }

        public Builder failedPlaceholderMaxAgeMs(
            long failedPlaceholderMaxAgeMs
        ) {
            this.failedPlaceholderMaxAgeMs = failedPlaceholderMaxAgeMs;
            return this;
        }

        public Builder loadingThreads(int loadingThreads) {
            this.loadingThreads = loadingThreads;
            return this;
        }

        public Builder loadTimeoutMs(int loadTimeoutMs) {
            this.loadTimeoutMs = loadTimeoutMs;
            return this;
        }

        public Builder maxLoadAttempts(int maxLoadAttempts) {
            this.maxLoadAttempts = maxLoadAttempts;
            return this;
        }

        public Builder cleanupInterval(long duration, TimeUnit unit) {
            this.cleanupIntervalMs = unit.toMillis(duration);
            return this;
        }

        public Builder cleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
            return this;
        }

        public Builder evictionPolicy(EvictionPolicy evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
            return this;
        }

        public Builder enablePreloading(boolean enablePreloading) {
            this.enablePreloading = enablePreloading;
            return this;
        }

        public Builder enableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
            return this;
        }

        public Builder cacheUtilizationThreshold(double threshold) {
            this.cacheUtilizationThreshold = threshold;
            return this;
        }

        public Builder preloadBatchSize(int batchSize) {
            this.preloadBatchSize = batchSize;
            return this;
        }

        public Builder caffeineInitialCapacity(int capacity) {
            this.caffeineInitialCapacity = capacity;
            return this;
        }

        public Builder enableCaffeineStats(boolean enableStats) {
            this.enableCaffeineStats = enableStats;
            return this;
        }

        public Builder enableWeakKeys(boolean enableWeakKeys) {
            this.enableWeakKeys = enableWeakKeys;
            return this;
        }

        public Builder enableSoftValues(boolean enableSoftValues) {
            this.enableSoftValues = enableSoftValues;
            return this;
        }

        // Phase 3: Cache strategy builder methods
        public Builder withCacheStrategyType(CacheStrategyType strategyType) {
            this.cacheStrategyType = strategyType;
            return this;
        }

        public Builder withEnableDiskCache(boolean enableDiskCache) {
            this.enableDiskCache = enableDiskCache;
            return this;
        }

        public Builder withDiskCacheDirectory(String diskCacheDirectory) {
            this.diskCacheDirectory = diskCacheDirectory;
            return this;
        }

        public Builder withDiskCacheMultiplier(long diskCacheMultiplier) {
            this.diskCacheMultiplier = diskCacheMultiplier;
            return this;
        }

        /**
         * Builds and validates the configuration.
         *
         * @return new CacheConfiguration instance
         * @throws IllegalArgumentException if configuration is invalid
         */
        public CacheConfiguration build() {
            CacheConfiguration config = new CacheConfiguration(this);
            config.validate();
            return config;
        }
    }
}
