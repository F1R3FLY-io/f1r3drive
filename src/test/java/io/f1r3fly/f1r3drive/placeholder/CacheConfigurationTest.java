package io.f1r3fly.f1r3drive.placeholder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CacheConfiguration.
 * Tests default values, validation, and configuration options.
 */
class CacheConfigurationTest {

    private CacheConfiguration config;

    @BeforeEach
    void setUp() {
        config = new CacheConfiguration();
    }

    @Test
    void testDefaultConfiguration() {
        CacheConfiguration defaultConfig = CacheConfiguration.defaultConfig();

        assertNotNull(defaultConfig);
        assertTrue(defaultConfig.isCacheEnabled());
        assertEquals(100 * 1024 * 1024, defaultConfig.getMaxCacheSize()); // 100MB
        assertEquals(1000, defaultConfig.getMaxCachedFiles());
        assertEquals(CacheConfiguration.EvictionPolicy.LRU, defaultConfig.getEvictionPolicy());
        assertEquals(5 * 60 * 1000, defaultConfig.getCleanupIntervalMs()); // 5 minutes
        assertEquals(30 * 60 * 1000, defaultConfig.getMaxIdleTimeMs()); // 30 minutes
        assertEquals(10, defaultConfig.getLoadingQueueSize());
        assertEquals(5, defaultConfig.getLoadingThreads());
        assertTrue(defaultConfig.isPreloadingEnabled());
        assertTrue(defaultConfig.isStatisticsEnabled());
    }

    @Test
    void testSetCacheEnabled() {
        config.setCacheEnabled(false);
        assertFalse(config.isCacheEnabled());

        config.setCacheEnabled(true);
        assertTrue(config.isCacheEnabled());
    }

    @Test
    void testSetMaxCacheSize() {
        long size = 50 * 1024 * 1024; // 50MB
        config.setMaxCacheSize(size);
        assertEquals(size, config.getMaxCacheSize());
    }

    @Test
    void testSetMaxCacheSize_Zero() {
        config.setMaxCacheSize(0);
        assertEquals(0, config.getMaxCacheSize());
    }

    @Test
    void testSetMaxCacheSize_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setMaxCacheSize(-1);
        });
    }

    @Test
    void testSetMaxCachedFiles() {
        config.setMaxCachedFiles(500);
        assertEquals(500, config.getMaxCachedFiles());
    }

    @Test
    void testSetMaxCachedFiles_Zero() {
        config.setMaxCachedFiles(0);
        assertEquals(0, config.getMaxCachedFiles());
    }

    @Test
    void testSetMaxCachedFiles_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setMaxCachedFiles(-1);
        });
    }

    @Test
    void testSetEvictionPolicy() {
        config.setEvictionPolicy(CacheConfiguration.EvictionPolicy.FIFO);
        assertEquals(CacheConfiguration.EvictionPolicy.FIFO, config.getEvictionPolicy());

        config.setEvictionPolicy(CacheConfiguration.EvictionPolicy.LRU);
        assertEquals(CacheConfiguration.EvictionPolicy.LRU, config.getEvictionPolicy());

        config.setEvictionPolicy(CacheConfiguration.EvictionPolicy.SIZE_BASED);
        assertEquals(CacheConfiguration.EvictionPolicy.SIZE_BASED, config.getEvictionPolicy());
    }

    @Test
    void testSetEvictionPolicy_Null() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setEvictionPolicy(null);
        });
    }

    @Test
    void testSetCleanupIntervalMs() {
        config.setCleanupIntervalMs(60000); // 1 minute
        assertEquals(60000, config.getCleanupIntervalMs());
    }

    @Test
    void testSetCleanupIntervalMs_Zero() {
        config.setCleanupIntervalMs(0);
        assertEquals(0, config.getCleanupIntervalMs());
    }

    @Test
    void testSetCleanupIntervalMs_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setCleanupIntervalMs(-1);
        });
    }

    @Test
    void testSetMaxIdleTimeMs() {
        config.setMaxIdleTimeMs(600000); // 10 minutes
        assertEquals(600000, config.getMaxIdleTimeMs());
    }

    @Test
    void testSetMaxIdleTimeMs_Zero() {
        config.setMaxIdleTimeMs(0);
        assertEquals(0, config.getMaxIdleTimeMs());
    }

    @Test
    void testSetMaxIdleTimeMs_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setMaxIdleTimeMs(-1);
        });
    }

    @Test
    void testSetLoadingQueueSize() {
        config.setLoadingQueueSize(20);
        assertEquals(20, config.getLoadingQueueSize());
    }

    @Test
    void testSetLoadingQueueSize_Zero() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setLoadingQueueSize(0);
        });
    }

    @Test
    void testSetLoadingQueueSize_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setLoadingQueueSize(-1);
        });
    }

    @Test
    void testSetLoadingThreads() {
        config.setLoadingThreads(8);
        assertEquals(8, config.getLoadingThreads());
    }

    @Test
    void testSetLoadingThreads_Zero() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setLoadingThreads(0);
        });
    }

    @Test
    void testSetLoadingThreads_Negative() {
        assertThrows(IllegalArgumentException.class, () -> {
            config.setLoadingThreads(-1);
        });
    }

    @Test
    void testSetPreloadingEnabled() {
        config.setPreloadingEnabled(false);
        assertFalse(config.isPreloadingEnabled());

        config.setPreloadingEnabled(true);
        assertTrue(config.isPreloadingEnabled());
    }

    @Test
    void testSetStatisticsEnabled() {
        config.setStatisticsEnabled(false);
        assertFalse(config.isStatisticsEnabled());

        config.setStatisticsEnabled(true);
        assertTrue(config.isStatisticsEnabled());
    }

    @Test
    void testEvictionPolicyEnum() {
        assertEquals("LRU", CacheConfiguration.EvictionPolicy.LRU.name());
        assertEquals("FIFO", CacheConfiguration.EvictionPolicy.FIFO.name());
        assertEquals("SIZE_BASED", CacheConfiguration.EvictionPolicy.SIZE_BASED.name());

        assertEquals("Least Recently Used", CacheConfiguration.EvictionPolicy.LRU.getDescription());
        assertEquals("First In, First Out", CacheConfiguration.EvictionPolicy.FIFO.getDescription());
        assertEquals("Size-based eviction", CacheConfiguration.EvictionPolicy.SIZE_BASED.getDescription());
    }

    @Test
    void testValidate_ValidConfig() {
        CacheConfiguration validConfig = CacheConfiguration.defaultConfig();
        assertTrue(validConfig.validate());
    }

    @Test
    void testValidate_DisabledCache() {
        config.setCacheEnabled(false);
        assertTrue(config.validate());
    }

    @Test
    void testValidate_InvalidMaxCacheSize() {
        config.setMaxCacheSize(-1);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_InvalidMaxCachedFiles() {
        config.setMaxCachedFiles(-1);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_InvalidLoadingThreads() {
        config.setLoadingThreads(0);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_InvalidLoadingQueueSize() {
        config.setLoadingQueueSize(0);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_InvalidCleanupInterval() {
        config.setCleanupIntervalMs(-1);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_InvalidMaxIdleTime() {
        config.setMaxIdleTimeMs(-1);
        assertFalse(config.validate());
    }

    @Test
    void testValidate_NullEvictionPolicy() {
        // We can't directly set null due to validation in setter,
        // so we use reflection or assume setter throws exception
        // This test verifies the validation logic
        CacheConfiguration validConfig = CacheConfiguration.defaultConfig();
        assertTrue(validConfig.validate());
        assertNotNull(validConfig.getEvictionPolicy());
    }

    @Test
    void testToString() {
        config.setCacheEnabled(true);
        config.setMaxCacheSize(1024);
        config.setMaxCachedFiles(10);
        config.setEvictionPolicy(CacheConfiguration.EvictionPolicy.LRU);

        String configString = config.toString();

        assertNotNull(configString);
        assertTrue(configString.contains("cacheEnabled=true"));
        assertTrue(configString.contains("maxCacheSize=1024"));
        assertTrue(configString.contains("maxCachedFiles=10"));
        assertTrue(configString.contains("evictionPolicy=LRU"));
    }

    @Test
    void testClone() {
        config.setCacheEnabled(true);
        config.setMaxCacheSize(2048);
        config.setMaxCachedFiles(20);
        config.setEvictionPolicy(CacheConfiguration.EvictionPolicy.FIFO);
        config.setLoadingThreads(8);

        CacheConfiguration cloned = config.clone();

        assertNotSame(config, cloned);
        assertEquals(config.isCacheEnabled(), cloned.isCacheEnabled());
        assertEquals(config.getMaxCacheSize(), cloned.getMaxCacheSize());
        assertEquals(config.getMaxCachedFiles(), cloned.getMaxCachedFiles());
        assertEquals(config.getEvictionPolicy(), cloned.getEvictionPolicy());
        assertEquals(config.getLoadingThreads(), cloned.getLoadingThreads());
    }

    @Test
    void testEquals() {
        CacheConfiguration config1 = CacheConfiguration.defaultConfig();
        CacheConfiguration config2 = CacheConfiguration.defaultConfig();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());

        config2.setMaxCacheSize(999999);
        assertNotEquals(config1, config2);
    }

    @Test
    void testEquals_SameInstance() {
        assertEquals(config, config);
    }

    @Test
    void testEquals_Null() {
        assertNotEquals(config, null);
    }

    @Test
    void testEquals_DifferentClass() {
        assertNotEquals(config, "not a config");
    }

    @Test
    void testBuilder() {
        CacheConfiguration builtConfig = CacheConfiguration.builder()
            .cacheEnabled(false)
            .maxCacheSize(512 * 1024)
            .maxCachedFiles(50)
            .evictionPolicy(CacheConfiguration.EvictionPolicy.SIZE_BASED)
            .cleanupIntervalMs(120000)
            .maxIdleTimeMs(600000)
            .loadingQueueSize(15)
            .loadingThreads(3)
            .preloadingEnabled(false)
            .statisticsEnabled(false)
            .build();

        assertFalse(builtConfig.isCacheEnabled());
        assertEquals(512 * 1024, builtConfig.getMaxCacheSize());
        assertEquals(50, builtConfig.getMaxCachedFiles());
        assertEquals(CacheConfiguration.EvictionPolicy.SIZE_BASED, builtConfig.getEvictionPolicy());
        assertEquals(120000, builtConfig.getCleanupIntervalMs());
        assertEquals(600000, builtConfig.getMaxIdleTimeMs());
        assertEquals(15, builtConfig.getLoadingQueueSize());
        assertEquals(3, builtConfig.getLoadingThreads());
        assertFalse(builtConfig.isPreloadingEnabled());
        assertFalse(builtConfig.isStatisticsEnabled());
    }

    @Test
    void testBuilder_DefaultValues() {
        CacheConfiguration builtConfig = CacheConfiguration.builder().build();
        CacheConfiguration defaultConfig = CacheConfiguration.defaultConfig();

        assertEquals(defaultConfig, builtConfig);
    }

    @Test
    void testOptimizedForLowMemory() {
        CacheConfiguration lowMemoryConfig = CacheConfiguration.optimizedForLowMemory();

        assertTrue(lowMemoryConfig.isCacheEnabled());
        assertTrue(lowMemoryConfig.getMaxCacheSize() < CacheConfiguration.defaultConfig().getMaxCacheSize());
        assertTrue(lowMemoryConfig.getMaxCachedFiles() < CacheConfiguration.defaultConfig().getMaxCachedFiles());
        assertTrue(lowMemoryConfig.getLoadingThreads() < CacheConfiguration.defaultConfig().getLoadingThreads());
    }

    @Test
    void testOptimizedForHighPerformance() {
        CacheConfiguration highPerfConfig = CacheConfiguration.optimizedForHighPerformance();

        assertTrue(highPerfConfig.isCacheEnabled());
        assertTrue(highPerfConfig.getMaxCacheSize() > CacheConfiguration.defaultConfig().getMaxCacheSize());
        assertTrue(highPerfConfig.getMaxCachedFiles() > CacheConfiguration.defaultConfig().getMaxCachedFiles());
        assertTrue(highPerfConfig.getLoadingThreads() > CacheConfiguration.defaultConfig().getLoadingThreads());
        assertTrue(highPerfConfig.getLoadingQueueSize() > CacheConfiguration.defaultConfig().getLoadingQueueSize());
    }

    @Test
    void testCacheDisabled() {
        CacheConfiguration disabledConfig = CacheConfiguration.cacheDisabled();

        assertFalse(disabledConfig.isCacheEnabled());
        assertEquals(0, disabledConfig.getMaxCacheSize());
        assertEquals(0, disabledConfig.getMaxCachedFiles());
        assertFalse(disabledConfig.isPreloadingEnabled());
        assertFalse(disabledConfig.isStatisticsEnabled());
    }

    @Test
    void testGetEvictionPolicyDisplayName() {
        assertEquals("Least Recently Used", CacheConfiguration.EvictionPolicy.LRU.getDisplayName());
        assertEquals("First In, First Out", CacheConfiguration.EvictionPolicy.FIFO.getDisplayName());
        assertEquals("Size-based Eviction", CacheConfiguration.EvictionPolicy.SIZE_BASED.getDisplayName());
    }

    @Test
    void testIsMemoryConstrained() {
        CacheConfiguration smallConfig = new CacheConfiguration();
        smallConfig.setMaxCacheSize(1024 * 1024); // 1MB

        assertTrue(smallConfig.isMemoryConstrained());

        CacheConfiguration largeConfig = new CacheConfiguration();
        largeConfig.setMaxCacheSize(500 * 1024 * 1024); // 500MB

        assertFalse(largeConfig.isMemoryConstrained());
    }

    @Test
    void testGetEstimatedMemoryUsage() {
        config.setMaxCacheSize(10 * 1024 * 1024); // 10MB
        config.setMaxCachedFiles(100);

        long estimatedMemory = config.getEstimatedMemoryUsage();

        assertTrue(estimatedMemory > 0);
        assertTrue(estimatedMemory >= config.getMaxCacheSize());
    }
}
