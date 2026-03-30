package io.f1r3fly.f1r3drive.placeholder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Basic tests for PlaceholderManager.
 * Tests existing methods and basic functionality.
 */
class PlaceholderManagerTest {

    private PlaceholderManager placeholderManager;

    @Mock
    private FileChangeCallback mockFileChangeCallback;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        placeholderManager = new PlaceholderManager(mockFileChangeCallback);
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
        long size = 1024;
        String checksum = "abc123";
        int priority = 1;

        boolean created = placeholderManager.createPlaceholder(
            path,
            size,
            checksum,
            priority
        );

        assertTrue(created);

        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
        assertNotNull(info);
        assertEquals(path, info.getPath());
        assertEquals(size, info.getExpectedSize());
        assertEquals(checksum, info.getChecksum());
        assertEquals(priority, info.getPriority());
    }

    @Test
    void testCreatePlaceholder_InvalidPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            placeholderManager.createPlaceholder(null, 1024, "checksum", 1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            placeholderManager.createPlaceholder("", 1024, "checksum", 1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            placeholderManager.createPlaceholder("   ", 1024, "checksum", 1);
        });
    }

    @Test
    void testLoadContent_NotFound() {
        String path = "/nonexistent/file.txt";

        byte[] content = placeholderManager.loadContent(path);

        assertNull(content);
    }

    @Test
    void testGetPlaceholderInfo_NotFound() {
        String path = "/nonexistent/file.txt";

        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);

        assertNull(info);
    }

    @Test
    void testGetAllPlaceholderPaths_Empty() {
        var paths = placeholderManager.getAllPlaceholderPaths();

        assertNotNull(paths);
        assertTrue(paths.isEmpty());
    }

    @Test
    void testGetAllPlaceholderPaths_WithPlaceholders() {
        String path1 = "/test/file1.txt";
        String path2 = "/test/file2.txt";

        placeholderManager.createPlaceholder(path1, 1024, "checksum1", 1);
        placeholderManager.createPlaceholder(path2, 2048, "checksum2", 2);

        var paths = placeholderManager.getAllPlaceholderPaths();

        assertEquals(2, paths.size());
        assertTrue(paths.contains(path1));
        assertTrue(paths.contains(path2));
    }

    @Test
    void testGetConfiguration() {
        CacheConfiguration config = placeholderManager.getConfiguration();

        assertNotNull(config);
        // Should be default configuration
        assertNotNull(config);
        // CacheConfiguration doesn't have isCacheEnabled() method
        // Just verify config is not null and has reasonable defaults
    }

    @Test
    void testConstructor_NullCallback() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PlaceholderManager(null);
        });
    }

    @Test
    void testConstructor_WithConfig() {
        CacheConfiguration customConfig = CacheConfiguration.defaultConfig();

        PlaceholderManager manager = new PlaceholderManager(
            mockFileChangeCallback,
            customConfig
        );

        try {
            assertNotNull(manager);
            assertEquals(customConfig, manager.getConfiguration());
        } finally {
            manager.cleanup();
        }
    }

    @Test
    void testConstructor_NullConfig() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PlaceholderManager(mockFileChangeCallback, null);
        });
    }

    @Test
    void testGetStatistics() {
        PlaceholderManager.CacheStatistics stats =
            placeholderManager.getCacheStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.getCacheHits());
        assertEquals(0, stats.getCacheMisses());
        assertEquals(0, stats.getPlaceholderCount());
    }

    @Test
    void testCleanup_MultipleCalls() {
        // Should not throw exception when called multiple times
        assertDoesNotThrow(() -> {
            placeholderManager.cleanup();
            placeholderManager.cleanup();
        });
    }
}
