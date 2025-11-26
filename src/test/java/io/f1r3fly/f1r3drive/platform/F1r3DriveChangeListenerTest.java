package io.f1r3fly.f1r3drive.platform;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for F1r3DriveChangeListener.
 * Tests the integration between platform file events and F1r3Drive internal systems.
 */
class F1r3DriveChangeListenerTest {

    @Mock
    private FileSystem mockFileSystem;

    @Mock
    private PlaceholderManager mockPlaceholderManager;

    private F1r3DriveChangeListener changeListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        changeListener = new F1r3DriveChangeListener(mockFileSystem, mockPlaceholderManager);
    }

    @Test
    void testOnFileCreated_WithPlaceholder() throws Exception {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.loadContent(testPath)).thenReturn("test content".getBytes());

        // When
        changeListener.onFileCreated(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager).loadContent(testPath);
    }

    @Test
    void testOnFileCreated_WithoutPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(false);

        // When
        changeListener.onFileCreated(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager, never()).loadContent(testPath);
    }

    @Test
    void testOnFileModified_WithPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.invalidateCache(testPath)).thenReturn(true);

        // When
        changeListener.onFileModified(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager).invalidateCache(testPath);
    }

    @Test
    void testOnFileModified_WithoutPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(false);

        // When
        changeListener.onFileModified(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager, never()).invalidateCache(testPath);
    }

    @Test
    void testOnFileDeleted_WithPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.removePlaceholder(testPath)).thenReturn(true);

        // When
        changeListener.onFileDeleted(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager).removePlaceholder(testPath);
    }

    @Test
    void testOnFileDeleted_WithoutPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(false);

        // When
        changeListener.onFileDeleted(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager, never()).removePlaceholder(testPath);
    }

    @Test
    void testOnFileMoved_WithPlaceholder() {
        // Given
        String oldPath = "/test/old.txt";
        String newPath = "/test/new.txt";
        when(mockPlaceholderManager.isPlaceholder(oldPath)).thenReturn(true);
        when(mockPlaceholderManager.movePlaceholder(oldPath, newPath)).thenReturn(true);

        // When
        changeListener.onFileMoved(oldPath, newPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(oldPath);
        verify(mockPlaceholderManager).movePlaceholder(oldPath, newPath);
    }

    @Test
    void testOnFileMoved_WithoutPlaceholder() {
        // Given
        String oldPath = "/test/old.txt";
        String newPath = "/test/new.txt";
        when(mockPlaceholderManager.isPlaceholder(oldPath)).thenReturn(false);

        // When
        changeListener.onFileMoved(oldPath, newPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(oldPath);
        verify(mockPlaceholderManager, never()).movePlaceholder(oldPath, newPath);
    }

    @Test
    void testOnFileAccessed_WithPlaceholder() throws Exception {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.ensureLoaded(testPath)).thenReturn("content".getBytes());

        // When
        changeListener.onFileAccessed(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager).ensureLoaded(testPath);
    }

    @Test
    void testOnFileAccessed_WithoutPlaceholder() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(false);

        // When
        changeListener.onFileAccessed(testPath);

        // Then
        verify(mockPlaceholderManager).isPlaceholder(testPath);
        verify(mockPlaceholderManager, never()).ensureLoaded(testPath);
    }

    @Test
    void testOnFileAttributesChanged() {
        // Given
        String testPath = "/test/file.txt";

        // When - should not throw exception
        assertDoesNotThrow(() -> changeListener.onFileAttributesChanged(testPath));

        // Then - no interactions with mocks expected for attribute changes
        verifyNoInteractions(mockPlaceholderManager);
        verifyNoInteractions(mockFileSystem);
    }

    @Test
    void testOnError_WithPath() {
        // Given
        Exception testError = new RuntimeException("Test error");
        String testPath = "/test/file.txt";

        // When - should not throw exception
        assertDoesNotThrow(() -> changeListener.onError(testError, testPath));
    }

    @Test
    void testOnError_WithoutPath() {
        // Given
        Exception testError = new RuntimeException("Test error");

        // When - should not throw exception
        assertDoesNotThrow(() -> changeListener.onError(testError, null));
    }

    @Test
    void testOnMonitoringStarted() {
        // Given
        String watchedPath = "/test/watched";

        // When - should not throw exception
        assertDoesNotThrow(() -> changeListener.onMonitoringStarted(watchedPath));
    }

    @Test
    void testOnMonitoringStopped() {
        // Given
        String watchedPath = "/test/watched";

        // When - should not throw exception
        assertDoesNotThrow(() -> changeListener.onMonitoringStopped(watchedPath));
    }

    @Test
    void testExceptionHandling_OnFileCreated() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.loadContent(testPath)).thenThrow(new RuntimeException("Load failed"));

        // When - should not throw exception despite internal error
        assertDoesNotThrow(() -> changeListener.onFileCreated(testPath));
    }

    @Test
    void testExceptionHandling_OnFileModified() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.invalidateCache(testPath)).thenThrow(new RuntimeException("Invalidate failed"));

        // When - should not throw exception despite internal error
        assertDoesNotThrow(() -> changeListener.onFileModified(testPath));
    }

    @Test
    void testExceptionHandling_OnFileDeleted() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.removePlaceholder(testPath)).thenThrow(new RuntimeException("Remove failed"));

        // When - should not throw exception despite internal error
        assertDoesNotThrow(() -> changeListener.onFileDeleted(testPath));
    }

    @Test
    void testExceptionHandling_OnFileMoved() {
        // Given
        String oldPath = "/test/old.txt";
        String newPath = "/test/new.txt";
        when(mockPlaceholderManager.isPlaceholder(oldPath)).thenReturn(true);
        when(mockPlaceholderManager.movePlaceholder(oldPath, newPath)).thenThrow(new RuntimeException("Move failed"));

        // When - should not throw exception despite internal error
        assertDoesNotThrow(() -> changeListener.onFileMoved(oldPath, newPath));
    }

    @Test
    void testExceptionHandling_OnFileAccessed() {
        // Given
        String testPath = "/test/file.txt";
        when(mockPlaceholderManager.isPlaceholder(testPath)).thenReturn(true);
        when(mockPlaceholderManager.ensureLoaded(testPath)).thenThrow(new RuntimeException("Ensure load failed"));

        // When - should not throw exception despite internal error
        assertDoesNotThrow(() -> changeListener.onFileAccessed(testPath));
    }
}
