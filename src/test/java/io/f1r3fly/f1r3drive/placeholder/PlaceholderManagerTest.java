package io.f1r3fly.f1r3drive.placeholder;

import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlaceholderManager.
 * Tests cache management, lazy loading, and placeholder lifecycle.
 */
class PlaceholderManagerTest {

    private PlaceholderManager placeholderManager;

    @Mock
    private FileChangeCallback mockFileChangeCallback;

    private CacheConfiguration testConfig;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        testConfig = new CacheConfiguration();
        testConfig.setMaxCacheSize(1024 * 1024); // 1MB
        testConfig.setMaxCachedFiles(10);
        testConfig.setEvictionPolicy(CacheConfiguration.EvictionPolicy.LRU);
        testConfig.setCacheEnabled(true);

        placeholderManager = new PlaceholderManager(mockFileChangeCallback, testConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (placeholderManager != null) {
            placeholderManager.cleanup();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testCreatePlaceholder_Success() {
        String path = "/test/file.txt";
        String blockchainAddress = "addr123";

        boolean created = placeholderManager.createPlaceholder(path, blockchainAddress);

        assertTrue(created);

        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
        assertNotNull(info);
        assertEquals(path, info.getPath());
        assertEquals(blockchainAddress, info.getBlockchainAddress());
        assertEquals(PlaceholderState.NOT_LOADED, info.getState());
    }

    @Test
    void testCreatePlaceholder_AlreadyExists() {
        String path = "/test/file.txt";
        String blockchainAddress = "addr123";

        // Create first placeholder
        assertTrue(placeholderManager.createPlaceholder(path, blockchainAddress));

        // Try to create same placeholder again
        assertFalse(placeholderManager.createPlaceholder(path, blockchainAddress));
    }

    @Test
    void testCreatePlaceholder_InvalidPath() {
        assertFalse(placeholderManager.createPlaceholder(null, "addr123"));
        assertFalse(placeholderManager.createPlaceholder("", "addr123"));
        assertFalse(placeholderManager.createPlaceholder("   ", "addr123"));
    }

    @Test
    void testCreatePlaceholder_InvalidBlockchainAddress() {
        String path = "/test/file.txt";

        assertFalse(placeholderManager.createPlaceholder(path, null));
        assertFalse(placeholderManager.createPlaceholder(path, ""));
        assertFalse(placeholderManager.createPlaceholder(path, "   "));
    }

    @Test
    void testLoadContent_Success() throws Exception {
        String path = "/test/file.txt";
        String blockchainAddress = "addr123";
        byte[] expectedContent = "Hello World".getBytes();

        // Setup mock
        when(mockFileChangeCallback.loadFileContent(path)).thenReturn(expectedContent);

        // Create placeholder
        placeholderManager.createPlaceholder(path, blockchainAddress);

        // Load content
        byte[] content = placeholderManager.loadContent(path);

        assertNotNull(content);
        assertArrayEquals(expectedContent, content);
        verify(mockFileChangeCallback).loadFileContent(path);

        // Verify placeholder state updated
        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
        assertEquals(PlaceholderState.LOADED, info.getState());
    }

    @Test
    void testLoadContent_NotFound() {
        String path = "/nonexistent/file.txt";

        byte[] content = placeholderManager.loadContent(path);

        assertNull(content);
        verifyNoInteractions(mockFileChangeCallback);
    }

    @Test
    void testLoadContent_CacheHit() throws Exception {
        String path = "/test/file.txt";
        String blockchainAddress = "addr123";
        byte[] expectedContent = "Cached Content".getBytes();

        // Setup mock to return content only once
        when(mockFileChangeCallback.loadFileContent(path)).thenReturn(expectedContent);

        // Create placeholder and load content
        placeholderManager.createPlaceholder(path, blockchainAddress);
        byte[] firstLoad = placeholderManager.loadContent(path);
        byte[] secondLoad = placeholderManager.loadContent(path);

        assertArrayEquals(expectedContent, firstLoad);
        assertArrayEquals(expectedContent, secondLoad);

        // Verify callback was called only once (second call was cache hit)
        verify(mockFileChangeCallback, times(1)).loadFileContent(path);

        // Verify cache statistics
        PlaceholderManager.CacheStatistics stats = placeholderManager.getStatistics();
        assertEquals(1, stats.getCacheHits());
        assertEquals(1, stats.getCacheMisses());
    }

    @Test
    void testLoadContent_CallbackException() throws Exception {
        String path = "/test/file.txt";
        String blockchainAddress = "addr123";

        // Setup mock to throw exception
        when(mockFileChangeCallback.loadFileContent(path))
            .thenThrow(new RuntimeException("Blockchain error"));

        // Create placeholder
        placeholderManager.createPlaceholder(path, blockchainAddress);

        // Load content should handle exception gracefully
        byte[] content = placeholderManager.loadContent(path);

        assertNull(content);

        // Verify placeholder state is updated to ERROR
        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
        assertEquals(PlaceholderState.ERROR, info.getState());
    }

    @Test
    void testPreloadFile_Success() throws Exception {
        String path = "/test/preload.txt";
        String blockchainAddress = "addr456";
        byte[] expectedContent = "Preloaded Content".getBytes();

        when(mockFileChangeCallback.loadFileContent(path)).thenReturn(expectedContent);

        // Create placeholder
        placeholderManager.createPlaceholder(path, blockchainAddress);

        // Preload with high priority
        CompletableFuture<byte[]> future = placeholderManager.preloadFile(path, true);

        byte[] content = future.get(5, TimeUnit.SECONDS);
        assertNotNull(content);
        assertArrayEquals(expectedContent, content);

        // Verify it's now cached
        byte[] cachedContent = placeholderManager.loadContent(path);
        assertArrayEquals(expectedContent, cachedContent);
    }

    @Test
    void testPreloadFile_NotFound() throws Exception {
        String path = "/nonexistent/preload.txt";

        CompletableFuture<byte[]> future = placeholderManager.preloadFile(path, false);

        byte[] content = future.get(1, TimeUnit.SECONDS);
        assertNull(content);
    }

    @Test
    void testConcurrentLoading() throws Exception {
        String path = "/test/concurrent.txt";
        String blockchainAddress = "addr789";
        byte[] expectedContent = "Concurrent Content".getBytes();

        // Add delay to simulate slow loading
        when(mockFileChangeCallback.loadFileContent(path)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return expectedContent;
        });

        placeholderManager.createPlaceholder(path, blockchainAddress);

        // Start multiple concurrent loads
        int numThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    byte[] content = placeholderManager.loadContent(path);
                    if (content != null && java.util.Arrays.equals(expectedContent, content)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        // All threads should succeed
        assertEquals(numThreads, successCount.get());

        // But callback should be called only once due to deduplication
        verify(mockFileChangeCallback, times(1)).loadFileContent(path);
    }

    @Test
    void testCacheEviction_LRU() throws Exception {
        // Create config with small cache
        CacheConfiguration smallCacheConfig = new CacheConfiguration();
        smallCacheConfig.setMaxCachedFiles(2);
        smallCacheConfig.setEvictionPolicy(CacheConfiguration.EvictionPolicy.LRU);
        smallCacheConfig.setCacheEnabled(true);

        PlaceholderManager smallCacheManager = new PlaceholderManager(mockFileChangeCallback, smallCacheConfig);

        try {
            // Setup content for 3 files
            String path1 = "/test/file1.txt";
            String path2 = "/test/file2.txt";
            String path3 = "/test/file3.txt";
            byte[] content1 = "Content 1".getBytes();
            byte[] content2 = "Content 2".getBytes();
            byte[] content3 = "Content 3".getBytes();

            when(mockFileChangeCallback.loadFileContent(path1)).thenReturn(content1);
            when(mockFileChangeCallback.loadFileContent(path2)).thenReturn(content2);
            when(mockFileChangeCallback.loadFileContent(path3)).thenReturn(content3);

            // Create placeholders and load content
            smallCacheManager.createPlaceholder(path1, "addr1");
            smallCacheManager.createPlaceholder(path2, "addr2");
            smallCacheManager.createPlaceholder(path3, "addr3");

            smallCacheManager.loadContent(path1); // Load file1 (cache: [file1])
            smallCacheManager.loadContent(path2); // Load file2 (cache: [file1, file2])
            smallCacheManager.loadContent(path3); // Load file3, should evict file1 (cache: [file2, file3])

            // Access file1 again - should reload from blockchain
            smallCacheManager.loadContent(path1);

            // file1 should have been loaded twice due to eviction
            verify(mockFileChangeCallback, times(2)).loadFileContent(path1);
            verify(mockFileChangeCallback, times(1)).loadFileContent(path2);
            verify(mockFileChangeCallback, times(1)).loadFileContent(path3);

            // Verify cache statistics
            PlaceholderManager.CacheStatistics stats = smallCacheManager.getStatistics();
            assertTrue(stats.getTotalEvictions() > 0);

        } finally {
            smallCacheManager.cleanup();
        }
    }

    @Test
    void testClearCache() throws Exception {
        String path = "/test/cached.txt";
        byte[] content = "Cached Content".getBytes();

        when(mockFileChangeCallback.loadFileContent(path)).thenReturn(content);

        placeholderManager.createPlaceholder(path, "addr");
        placeholderManager.loadContent(path); // Load to cache

        // Verify cache has content
        PlaceholderManager.CacheStatistics statsBefore = placeholderManager.getStatistics();
        assertEquals(1, statsBefore.getCachedFilesCount());

        // Clear cache for specific file
        placeholderManager.clearCache(path);

        // Load again should call callback
        placeholderManager.loadContent(path);
        verify(mockFileChangeCallback, times(2)).loadFileContent(path);
    }

    @Test
    void testClearAllCache() throws Exception {
        String path1 = "/test/file1.txt";
        String path2 = "/test/file2.txt";
        byte[] content1 = "Content 1".getBytes();
        byte[] content2 = "Content 2".getBytes();

        when(mockFileChangeCallback.loadFileContent(path1)).thenReturn(content1);
        when(mockFileChangeCallback.loadFileContent(path2)).thenReturn(content2);

        placeholderManager.createPlaceholder(path1, "addr1");
        placeholderManager.createPlaceholder(path2, "addr2");
        placeholderManager.loadContent(path1);
        placeholderManager.loadContent(path2);

        // Clear all cache
        placeholderManager.clearAllCache();

        // Load again should call callbacks
        placeholderManager.loadContent(path1);
        placeholderManager.loadContent(path2);

        verify(mockFileChangeCallback, times(2)).loadFileContent(path1);
        verify(mockFileChangeCallback, times(2)).loadFileContent(path2);
    }

    @Test
    void testRemovePlaceholder() {
        String path = "/test/removable.txt";

        placeholderManager.createPlaceholder(path, "addr");
        assertNotNull(placeholderManager.getPlaceholderInfo(path));

        boolean removed = placeholderManager.removePlaceholder(path);

        assertTrue(removed);
        assertNull(placeholderManager.getPlaceholderInfo(path));
    }

    @Test
    void testRemovePlaceholder_NotFound() {
        boolean removed = placeholderManager.removePlaceholder("/nonexistent");
        assertFalse(removed);
    }

    @Test
    void testGetAllPlaceholderPaths() {
        String path1 = "/test/file1.txt";
        String path2 = "/test/file2.txt";

        placeholderManager.createPlaceholder(path1, "addr1");
        placeholderManager.createPlaceholder(path2, "addr2");

        Set<String> paths = placeholderManager.getAllPlaceholderPaths();

        assertEquals(2, paths.size());
        assertTrue(paths.contains(path1));
        assertTrue(paths.contains(path2));
    }

    @Test
    void testIsLoading() throws Exception {
        String path = "/test/loading.txt";
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        CountDownLatch loadingStarted = new CountDownLatch(1);
        CountDownLatch continueLoading = new CountDownLatch(1);

        when(mockFileChangeCallback.loadFileContent(path)).thenAnswer(invocation -> {
            callbackCalled.set(true);
            loadingStarted.countDown();
            continueLoading.await(5, TimeUnit.SECONDS);
            return "Content".getBytes();
        });

        placeholderManager.createPlaceholder(path, "addr");

        // Start loading in background
        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
            try {
                return placeholderManager.loadContent(path);
            } catch (Exception e) {
                return null;
            }
        });

        // Wait for loading to start
        assertTrue(loadingStarted.await(5, TimeUnit.SECONDS));

        // Check if loading
        assertTrue(placeholderManager.isLoading(path));

        // Complete loading
        continueLoading.countDown();
        future.get(5, TimeUnit.SECONDS);

        // Should no longer be loading
        assertFalse(placeholderManager.isLoading(path));
    }

    @Test
    void testCacheStatistics() throws Exception {
        String path1 = "/test/stats1.txt";
        String path2 = "/test/stats2.txt";
        byte[] content1 = "Stats Content 1".getBytes();
        byte[] content2 = "Stats Content 2".getBytes();

        when(mockFileChangeCallback.loadFileContent(path1)).thenReturn(content1);
        when(mockFileChangeCallback.loadFileContent(path2)).thenReturn(content2);

        placeholderManager.createPlaceholder(path1, "addr1");
        placeholderManager.createPlaceholder(path2, "addr2");

        // Initial stats
        PlaceholderManager.CacheStatistics initialStats = placeholderManager.getStatistics();
        assertEquals(0, initialStats.getCacheHits());
        assertEquals(0, initialStats.getCacheMisses());
        assertEquals(2, initialStats.getPlaceholderCount());

        // Load content (cache misses)
        placeholderManager.loadContent(path1);
        placeholderManager.loadContent(path2);

        PlaceholderManager.CacheStatistics afterLoads = placeholderManager.getStatistics();
        assertEquals(0, afterLoads.getCacheHits());
        assertEquals(2, afterLoads.getCacheMisses());
        assertEquals(2, afterLoads.getTotalLoads());
        assertEquals(2, afterLoads.getCachedFilesCount());
        assertTrue(afterLoads.getCacheSize() > 0);

        // Load again (cache hits)
        placeholderManager.loadContent(path1);
        placeholderManager.loadContent(path2);

        PlaceholderManager.CacheStatistics finalStats = placeholderManager.getStatistics();
        assertEquals(2, finalStats.getCacheHits());
        assertEquals(2, finalStats.getCacheMisses());
        assertEquals(4, finalStats.getTotalLoads());

        // Test hit ratio
        assertEquals(0.5, finalStats.getHitRatio(), 0.01);
    }

    @Test
    void testUpdatePlaceholderState() {
        String path = "/test/state.txt";

        placeholderManager.createPlaceholder(path, "addr");

        PlaceholderInfo initialInfo = placeholderManager.getPlaceholderInfo(path);
        assertEquals(PlaceholderState.NOT_LOADED, initialInfo.getState());

        // Update state
        boolean updated = placeholderManager.updatePlaceholderState(path, PlaceholderState.LOADING);

        assertTrue(updated);
        PlaceholderInfo updatedInfo = placeholderManager.getPlaceholderInfo(path);
        assertEquals(PlaceholderState.LOADING, updatedInfo.getState());
    }

    @Test
    void testUpdatePlaceholderState_NotFound() {
        boolean updated = placeholderManager.updatePlaceholderState("/nonexistent", PlaceholderState.LOADED);
        assertFalse(updated);
    }

    @Test
    void testCleanup() throws Exception {
        String path = "/test/cleanup.txt";
        placeholderManager.createPlaceholder(path, "addr");

        // Start a loading operation
        when(mockFileChangeCallback.loadFileContent(path)).thenAnswer(invocation -> {
            Thread.sleep(1000);
            return "Content".getBytes();
        });

        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
            try {
                return placeholderManager.loadContent(path);
            } catch (Exception e) {
                return null;
            }
        });

        // Give it time to start
        Thread.sleep(100);

        // Cleanup should shutdown executors
        placeholderManager.cleanup();

        // Future should complete (possibly with null due to interruption)
        byte[] result = future.get(2, TimeUnit.SECONDS);
        // Don't assert the result as cleanup may interrupt the operation
    }

    @Test
    void testConfiguration() {
        CacheConfiguration config = placeholderManager.getConfiguration();
        assertNotNull(config);
        assertEquals(testConfig.getMaxCacheSize(), config.getMaxCacheSize());
        assertEquals(testConfig.getMaxCachedFiles(), config.getMaxCachedFiles());
        assertEquals(testConfig.getEvictionPolicy(), config.getEvictionPolicy());
    }

    @Test
    void testDefaultConstructor() {
        PlaceholderManager defaultManager = new PlaceholderManager(mockFileChangeCallback);
        try {
            assertNotNull(defaultManager.getConfiguration());
            assertTrue(defaultManager.getConfiguration().isCacheEnabled());
        } finally {
            defaultManager.cleanup();
        }
    }

    @Test
    void testNullFileChangeCallback() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PlaceholderManager(null);
        });
    }
}
