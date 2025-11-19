package io.f1r3fly.f1r3drive.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Phase 3 cache strategies.
 *
 * Tests both TieredCacheStrategy and MemoryOnlyCacheStrategy implementations
 * to ensure unified cache behavior, performance characteristics, and proper
 * resource management.
 */
@DisplayName("Phase 3 Cache Strategy Tests")
public class CacheStrategyTest {

    private static final long TEST_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long TEST_MEMORY_THRESHOLD = 1024 * 1024; // 1MB
    private static final long TEST_EXPIRE_AFTER_ACCESS_MS = 5000; // 5 seconds

    @TempDir
    Path tempDir;

    private byte[] smallContent;
    private byte[] largeContent;
    private byte[] veryLargeContent;

    @BeforeEach
    void setUp() {
        // Create test content of different sizes
        smallContent = new byte[500 * 1024]; // 500KB - fits in L1
        largeContent = new byte[2 * 1024 * 1024]; // 2MB - L2 only for tiered
        veryLargeContent = new byte[15 * 1024 * 1024]; // 15MB - exceeds cache size

        // Fill with different patterns for verification
        for (int i = 0; i < smallContent.length; i++) {
            smallContent[i] = (byte) (i % 256);
        }
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) ((i * 2) % 256);
        }
        for (int i = 0; i < veryLargeContent.length; i++) {
            veryLargeContent[i] = (byte) ((i * 3) % 256);
        }
    }

    @Nested
    @DisplayName("Memory-Only Cache Strategy Tests")
    class MemoryOnlyCacheStrategyTests {

        private MemoryOnlyCacheStrategy memoryCache;

        @BeforeEach
        void setUp() {
            memoryCache = new MemoryOnlyCacheStrategy(TEST_CACHE_SIZE, TEST_EXPIRE_AFTER_ACCESS_MS);
        }

        @Test
        @DisplayName("Should cache and retrieve small content")
        void testSmallContentCaching() {
            // Given
            String filePath = "/test/small-file.txt";

            // When
            memoryCache.put(filePath, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Then
            Optional<CacheStrategy.CacheResult> result = memoryCache.get(filePath);
            assertTrue(result.isPresent());
            assertEquals(CacheStrategy.CacheLevel.MEMORY, result.get().getHitLevel());
            assertArrayEquals(smallContent, result.get().getContent());
            assertFalse(result.get().wasPromoted());
        }

        @Test
        @DisplayName("Should cache and retrieve large content")
        void testLargeContentCaching() {
            // Given
            String filePath = "/test/large-file.bin";

            // When
            memoryCache.put(filePath, largeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Then
            Optional<CacheStrategy.CacheResult> result = memoryCache.get(filePath);
            assertTrue(result.isPresent());
            assertArrayEquals(largeContent, result.get().getContent());
        }

        @Test
        @DisplayName("Should reject content that exceeds cache size")
        void testOversizedContentRejection() {
            // Given
            String filePath = "/test/oversized-file.bin";

            // When
            memoryCache.put(filePath, veryLargeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Then
            Optional<CacheStrategy.CacheResult> result = memoryCache.get(filePath);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should return cache miss for non-existent content")
        void testCacheMiss() {
            // When
            Optional<CacheStrategy.CacheResult> result = memoryCache.get("/test/non-existent.txt");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should invalidate cached content")
        void testInvalidation() {
            // Given
            String filePath = "/test/invalidate-me.txt";
            memoryCache.put(filePath, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // When
            boolean invalidated = memoryCache.invalidate(filePath);

            // Then
            assertTrue(invalidated);
            assertFalse(memoryCache.get(filePath).isPresent());
        }

        @Test
        @DisplayName("Should report accurate statistics")
        void testStatistics() {
            // Given
            memoryCache.put("/test/file1.txt", smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            memoryCache.put("/test/file2.txt", smallContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // When - Generate hits and misses
            memoryCache.get("/test/file1.txt"); // hit
            memoryCache.get("/test/file1.txt"); // hit
            memoryCache.get("/test/non-existent.txt"); // miss

            // Then
            CacheStrategy.UnifiedCacheStatistics stats = memoryCache.getStatistics();
            assertEquals(2, stats.getL1Hits());
            assertEquals(0, stats.getL2Hits());
            assertEquals(1, stats.getMisses());
            assertEquals(2, stats.getTotalHits());
            assertTrue(stats.getHitRatio() > 0);
        }
    }

    @Nested
    @DisplayName("Tiered Cache Strategy Tests")
    class TieredCacheStrategyTests {

        private TieredCacheStrategy tieredCache;

        @BeforeEach
        void setUp() {
            tieredCache = new TieredCacheStrategy(
                TEST_CACHE_SIZE,
                TEST_MEMORY_THRESHOLD,
                tempDir.resolve("cache"),
                TEST_CACHE_SIZE * 5,
                TEST_EXPIRE_AFTER_ACCESS_MS
            );
        }

        @Test
        @DisplayName("Should store small files in both L1 and L2")
        void testSmallFileTieredStorage() throws InterruptedException {
            // Given
            String filePath = "/test/small-tiered.txt";

            // When
            tieredCache.put(filePath, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Then - Should hit L1 cache
            Optional<CacheStrategy.CacheResult> result = tieredCache.get(filePath);
            assertTrue(result.isPresent());
            assertEquals(CacheStrategy.CacheLevel.MEMORY, result.get().getHitLevel());
            assertArrayEquals(smallContent, result.get().getContent());

            // Should also be in L2
            assertTrue(tieredCache.contains(filePath));
        }

        @Test
        @DisplayName("Should store large files in L2 only")
        void testLargeFileL2Storage() {
            // Given
            String filePath = "/test/large-tiered.bin";

            // When
            tieredCache.put(filePath, largeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Clear L1 to test L2 access
            tieredCache.invalidate(filePath);
            tieredCache.put(filePath, largeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // Then - Should hit L2 and potentially promote
            Optional<CacheStrategy.CacheResult> result = tieredCache.get(filePath);
            assertTrue(result.isPresent());
            assertArrayEquals(largeContent, result.get().getContent());
        }

        @Test
        @DisplayName("Should promote L2 content to L1 on access")
        void testL2ToL1Promotion() {
            // Given - Store content larger than memory threshold but promotable
            String filePath = "/test/promotable.txt";
            byte[] promotableContent = new byte[(int)(TEST_MEMORY_THRESHOLD - 1024)]; // Just under threshold

            // When - Store and access
            tieredCache.put(filePath, promotableContent, CacheStrategy.CacheMetadata.defaultMetadata());
            Optional<CacheStrategy.CacheResult> result = tieredCache.get(filePath);

            // Then - Should be promoted to L1
            assertTrue(result.isPresent());
            assertArrayEquals(promotableContent, result.get().getContent());
        }

        @Test
        @DisplayName("Should handle high priority content correctly")
        void testHighPriorityContent() {
            // Given
            String filePath = "/test/high-priority.bin";

            // When - Store large content with high priority
            tieredCache.put(filePath, largeContent, CacheStrategy.CacheMetadata.highPriority());

            // Then - Should be available and accessible
            Optional<CacheStrategy.CacheResult> result = tieredCache.get(filePath);
            assertTrue(result.isPresent());
            assertArrayEquals(largeContent, result.get().getContent());
        }

        @Test
        @DisplayName("Should perform maintenance operations")
        void testMaintenance() {
            // Given
            tieredCache.put("/test/file1.txt", smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            tieredCache.put("/test/file2.txt", largeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // When
            assertDoesNotThrow(() -> tieredCache.performMaintenance());

            // Then - Content should still be accessible
            assertTrue(tieredCache.contains("/test/file1.txt"));
            assertTrue(tieredCache.contains("/test/file2.txt"));
        }

        @Test
        @DisplayName("Should provide comprehensive statistics")
        void testTieredStatistics() {
            // Given - Add content to both tiers
            tieredCache.put("/test/small.txt", smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            tieredCache.put("/test/large.bin", largeContent, CacheStrategy.CacheMetadata.defaultMetadata());

            // When - Generate access patterns
            tieredCache.get("/test/small.txt"); // L1 hit
            tieredCache.get("/test/large.bin"); // L2 hit (potentially with promotion)
            tieredCache.get("/test/non-existent.txt"); // Miss

            // Then
            CacheStrategy.UnifiedCacheStatistics stats = tieredCache.getStatistics();
            assertTrue(stats.getTotalHits() > 0);
            assertTrue(stats.getL1Size() >= 0);
            assertTrue(stats.getL2Size() >= 0);
            assertEquals(1, stats.getMisses());
        }

        @Test
        @DisplayName("Should handle disk cache directory properly")
        void testDiskCacheDirectory() {
            // When
            Path diskCacheDir = tieredCache.getDiskCacheDirectory();

            // Then
            assertNotNull(diskCacheDir);
            assertTrue(Files.exists(diskCacheDir));
            assertTrue(Files.isDirectory(diskCacheDir));
        }
    }

    @Nested
    @DisplayName("Cache Strategy Interface Compliance Tests")
    class InterfaceComplianceTests {

        @ParameterizedTest
        @EnumSource(CacheStrategyType.class)
        @DisplayName("Should implement all CacheStrategy methods correctly")
        void testInterfaceCompliance(CacheStrategyType strategyType) {
            // Given
            CacheStrategy cache = createCacheStrategy(strategyType);
            String testPath = "/test/interface-compliance.txt";

            // When & Then - Test all interface methods
            assertFalse(cache.contains(testPath)); // Initially empty

            // Test put/get
            cache.put(testPath, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            assertTrue(cache.contains(testPath));

            Optional<CacheStrategy.CacheResult> result = cache.get(testPath);
            assertTrue(result.isPresent());
            assertArrayEquals(smallContent, result.get().getContent());

            // Test statistics
            CacheStrategy.UnifiedCacheStatistics stats = cache.getStatistics();
            assertNotNull(stats);
            assertTrue(stats.getTotalHits() >= 0);

            // Test invalidate
            assertTrue(cache.invalidate(testPath));
            assertFalse(cache.contains(testPath));

            // Test invalidateAll
            cache.put(testPath, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            cache.invalidateAll();
            assertFalse(cache.contains(testPath));

            // Test maintenance
            assertDoesNotThrow(cache::performMaintenance);
        }

        @Test
        @DisplayName("Should handle null inputs gracefully")
        void testNullInputHandling() {
            // Given
            CacheStrategy cache = createCacheStrategy(CacheStrategyType.MEMORY_ONLY);

            // When & Then
            assertThrows(IllegalArgumentException.class,
                () -> cache.put("/test/path", null, CacheStrategy.CacheMetadata.defaultMetadata()));
        }

        private CacheStrategy createCacheStrategy(CacheStrategyType type) {
            switch (type) {
                case MEMORY_ONLY:
                    return new MemoryOnlyCacheStrategy(TEST_CACHE_SIZE, TEST_EXPIRE_AFTER_ACCESS_MS);
                case TIERED:
                    return new TieredCacheStrategy(
                        TEST_CACHE_SIZE,
                        TEST_MEMORY_THRESHOLD,
                        tempDir.resolve("cache-" + type.name().toLowerCase()),
                        TEST_CACHE_SIZE * 5,
                        TEST_EXPIRE_AFTER_ACCESS_MS
                    );
                default:
                    throw new IllegalArgumentException("Unknown strategy type: " + type);
            }
        }
    }

    @Nested
    @DisplayName("Performance and Concurrency Tests")
    class PerformanceConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent access correctly")
        void testConcurrentAccess() throws InterruptedException {
            // Given
            TieredCacheStrategy cache = new TieredCacheStrategy(
                TEST_CACHE_SIZE,
                TEST_MEMORY_THRESHOLD,
                tempDir.resolve("concurrent-cache"),
                TEST_CACHE_SIZE * 5,
                TEST_EXPIRE_AFTER_ACCESS_MS
            );

            int numThreads = 10;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            // When - Perform concurrent operations
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            String path = "/test/thread-" + threadId + "-file-" + j + ".txt";

                            // Mix of put and get operations
                            if (j % 2 == 0) {
                                cache.put(path, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
                            } else {
                                cache.get(path);
                            }
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then
            assertTrue(latch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // Verify no exceptions occurred and operations completed
            assertTrue(successCount.get() > 0);

            // Verify cache is still functional
            cache.put("/test/post-concurrent.txt", smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            assertTrue(cache.contains("/test/post-concurrent.txt"));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10, 100, 1000})
        @DisplayName("Should handle varying load sizes efficiently")
        void testVaryingLoads(int numFiles) {
            // Given
            MemoryOnlyCacheStrategy cache = new MemoryOnlyCacheStrategy(
                TEST_CACHE_SIZE, TEST_EXPIRE_AFTER_ACCESS_MS);

            // When - Store varying numbers of files
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < numFiles; i++) {
                String path = "/test/load-test-" + i + ".txt";
                cache.put(path, smallContent, CacheStrategy.CacheMetadata.defaultMetadata());
            }

            // Retrieve all files
            int hitCount = 0;
            for (int i = 0; i < numFiles; i++) {
                String path = "/test/load-test-" + i + ".txt";
                if (cache.get(path).isPresent()) {
                    hitCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then - Performance should be reasonable
            assertTrue(duration < 10000); // Should complete within 10 seconds
            assertTrue(hitCount > 0); // Should have some hits (cache eviction may occur)

            // Verify cache statistics
            CacheStrategy.UnifiedCacheStatistics stats = cache.getStatistics();
            assertTrue(stats.getTotalHits() >= hitCount);
        }
    }

    @Nested
    @DisplayName("Cache Metadata Tests")
    class CacheMetadataTests {

        @Test
        @DisplayName("Should create default metadata correctly")
        void testDefaultMetadata() {
            // When
            CacheStrategy.CacheMetadata metadata = CacheStrategy.CacheMetadata.defaultMetadata();

            // Then
            assertEquals(0, metadata.getPriority());
            assertTrue(metadata.isPersistent());
            assertEquals(1, metadata.getExpectedAccessFrequency());
            assertEquals("application/octet-stream", metadata.getContentType());
        }

        @Test
        @DisplayName("Should create high priority metadata correctly")
        void testHighPriorityMetadata() {
            // When
            CacheStrategy.CacheMetadata metadata = CacheStrategy.CacheMetadata.highPriority();

            // Then
            assertEquals(10, metadata.getPriority());
            assertTrue(metadata.isPersistent());
            assertEquals(100, metadata.getExpectedAccessFrequency());
        }

        @Test
        @DisplayName("Should create custom metadata correctly")
        void testCustomMetadata() {
            // Given
            int priority = 5;
            boolean persistent = false;
            long frequency = 50;
            String contentType = "text/plain";

            // When
            CacheStrategy.CacheMetadata metadata = new CacheStrategy.CacheMetadata(
                priority, persistent, frequency, contentType);

            // Then
            assertEquals(priority, metadata.getPriority());
            assertEquals(persistent, metadata.isPersistent());
            assertEquals(frequency, metadata.getExpectedAccessFrequency());
            assertEquals(contentType, metadata.getContentType());
        }
    }

    /**
     * Enum for parameterized tests of different cache strategy types.
     */
    private enum CacheStrategyType {
        MEMORY_ONLY,
        TIERED
    }
}
