# FSEvents Integration with InMemoryFileSystem - Java Examples

## Overview

Этот документ показывает практические примеры интеграции macOS FSEvents API с существующей InMemoryFileSystem через новый ChangeWatcher паттерн.

---

## 1. Basic FSEventsMonitor Implementation

### FSEventsMonitor.java

```java
package io.f1r3fly.f1r3drive.platform.macos;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

/**
 * Native macOS FSEvents monitoring integration
 * Uses JNI to access FSEventStreamCreate API
 */
public class FSEventsMonitor {
    
    // Native stream reference (FSEventStreamRef)
    private long streamRef = 0;
    
    // Configuration
    private String watchPath;
    private FSEventCallback callback;
    private boolean isRunning = false;
    
    // Threading
    private Thread monitorThread;
    private ExecutorService callbackExecutor;
    
    // FSEvents flags
    private static final int kFSEventStreamCreateFlagFileEvents = 0x00000010;
    private static final int kFSEventStreamCreateFlagNoDefer = 0x00000002;
    
    // Event flags
    private static final int kFSEventStreamEventFlagItemCreated = 0x00000100;
    private static final int kFSEventStreamEventFlagItemModified = 0x00001000;
    private static final int kFSEventStreamEventFlagItemRemoved = 0x00000200;
    
    public FSEventsMonitor() {
        this.callbackExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FSEvents-Callback");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start monitoring specified path for file system events
     */
    public void startMonitoring(String path, FSEventCallback callback) throws FSEventsException {
        if (isRunning) {
            throw new FSEventsException("Monitor already running");
        }
        
        this.watchPath = path;
        this.callback = callback;
        
        // Start monitoring thread
        this.monitorThread = new Thread(this::runMonitorLoop, "FSEvents-Monitor");
        this.monitorThread.setDaemon(true);
        this.monitorThread.start();
        
        this.isRunning = true;
    }
    
    /**
     * Stop monitoring and cleanup resources
     */
    public void stopMonitoring() {
        if (!isRunning) return;
        
        isRunning = false;
        
        // Stop native stream
        if (streamRef != 0) {
            nativeStopStream(streamRef);
            streamRef = 0;
        }
        
        // Interrupt monitor thread
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Main monitoring loop - runs in separate thread
     */
    private void runMonitorLoop() {
        try {
            // Create native FSEvents stream
            streamRef = nativeCreateStream(
                watchPath, 
                kFSEventStreamCreateFlagFileEvents | kFSEventStreamCreateFlagNoDefer
            );
            
            if (streamRef == 0) {
                throw new FSEventsException("Failed to create FSEvents stream");
            }
            
            // Start stream and run loop
            nativeStartStream(streamRef);
            
            // Keep thread alive while monitoring
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(100); // CFRunLoop handles actual events
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(new FSEventsException("Monitor error", e));
            }
        } finally {
            cleanup();
        }
    }
    
    /**
     * Called from native code when FSEvents occur
     * This method is invoked via JNI callback
     */
    private void nativeEventCallback(String[] paths, int[] flags, long[] eventIds) {
        if (!isRunning || callback == null) return;
        
        // Process events asynchronously to avoid blocking native thread
        callbackExecutor.submit(() -> {
            for (int i = 0; i < paths.length; i++) {
                FSEvent event = new FSEvent(paths[i], flags[i], eventIds[i]);
                
                try {
                    callback.onEvent(event);
                } catch (Exception e) {
                    callback.onError(new FSEventsException("Callback error", e));
                }
            }
        });
    }
    
    public void cleanup() {
        stopMonitoring();
        
        if (callbackExecutor != null && !callbackExecutor.isShutdown()) {
            callbackExecutor.shutdown();
        }
    }
    
    // Native methods (implemented in JNI library)
    private native long nativeCreateStream(String path, int flags);
    private native void nativeStartStream(long streamRef);
    private native void nativeStopStream(long streamRef);
    
    static {
        // Load native library containing JNI implementations
        System.loadLibrary("f1r3drive-fsevents");
    }
}
```

---

## 2. MacOSChangeWatcher Implementation

### MacOSChangeWatcher.java

```java
package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.platform.*;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.filesystem.Node;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

/**
 * macOS-specific ChangeWatcher implementation
 * Integrates FSEvents with InMemoryFileSystem
 */
public class MacOSChangeWatcher implements ChangeWatcher {
    
    private FSEventsMonitor fsEventsMonitor;
    private FileProviderIntegration fileProvider;
    private ChangeListener changeListener;
    private InMemoryFileSystem memoryFileSystem;
    private PlaceholderManager placeholderManager;
    
    // Configuration
    private String mountPath;
    private boolean isMonitoring = false;
    
    // Callbacks and threading
    private Map<String, FileChangeCallback> callbacks = new ConcurrentHashMap<>();
    private ExecutorService eventExecutor;
    
    // Platform info
    private MacOSPlatformInfo platformInfo;
    
    public MacOSChangeWatcher() {
        this.fsEventsMonitor = new FSEventsMonitor();
        this.fileProvider = new FileProviderIntegration();
        this.eventExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "macOS-ChangeWatcher");
            t.setDaemon(true);
            return t;
        });
        this.platformInfo = new MacOSPlatformInfo();
    }
    
    @Override
    public void initialize(String mountPath, ChangeListener listener) throws PlatformException {
        this.mountPath = mountPath;
        this.changeListener = listener;
        
        try {
            // Initialize File Provider for virtual filesystem
            fileProvider.initialize(mountPath, new FileProviderCallbackImpl());
            
            System.out.printf("MacOSChangeWatcher initialized for path: %s%n", mountPath);
            
        } catch (Exception e) {
            throw new PlatformException("Failed to initialize macOS ChangeWatcher", e);
        }
    }
    
    @Override
    public void startMonitoring() throws PlatformException {
        if (isMonitoring) {
            throw new PlatformException("Already monitoring");
        }
        
        try {
            // Start FSEvents monitoring
            fsEventsMonitor.startMonitoring(mountPath, new FSEventCallbackImpl());
            
            // Activate File Provider
            fileProvider.createVirtualMount();
            
            isMonitoring = true;
            
            System.out.println("macOS file monitoring started");
            
        } catch (Exception e) {
            throw new PlatformException("Failed to start monitoring", e);
        }
    }
    
    @Override
    public void stopMonitoring() {
        if (!isMonitoring) return;
        
        fsEventsMonitor.stopMonitoring();
        fileProvider.cleanup();
        isMonitoring = false;
        
        System.out.println("macOS file monitoring stopped");
    }
    
    @Override
    public boolean isMonitoring() {
        return isMonitoring && fsEventsMonitor.isRunning();
    }
    
    @Override
    public void registerFileChangeCallback(String path, FileChangeCallback callback) {
        callbacks.put(path, callback);
        
        // Create placeholder if needed
        if (placeholderManager != null) {
            eventExecutor.submit(() -> {
                try {
                    // Check if file should be a placeholder
                    if (callback.shouldCache(path)) {
                        // Create placeholder in File Provider
                        fileProvider.createPlaceholder(path, null);
                    }
                } catch (Exception e) {
                    System.err.printf("Error creating placeholder for %s: %s%n", path, e.getMessage());
                }
            });
        }
    }
    
    @Override
    public void unregisterFileChangeCallback(String path) {
        callbacks.remove(path);
        fileProvider.removePlaceholder(path);
    }
    
    @Override
    public void cleanup() {
        stopMonitoring();
        
        if (eventExecutor != null && !eventExecutor.isShutdown()) {
            eventExecutor.shutdown();
        }
        
        fsEventsMonitor.cleanup();
        fileProvider.cleanup();
    }
    
    @Override
    public PlatformInfo getPlatformInfo() {
        return platformInfo;
    }
    
    /**
     * Set the InMemoryFileSystem to integrate with
     */
    public void setMemoryFileSystem(InMemoryFileSystem fileSystem) {
        this.memoryFileSystem = fileSystem;
    }
    
    public void setPlaceholderManager(PlaceholderManager manager) {
        this.placeholderManager = manager;
    }
    
    /**
     * FSEvents callback implementation
     */
    private class FSEventCallbackImpl implements FSEventCallback {
        
        @Override
        public void onEvent(FSEvent event) {
            eventExecutor.submit(() -> handleFSEvent(event));
        }
        
        @Override
        public void onError(FSEventsException error) {
            System.err.printf("FSEvents error: %s%n", error.getMessage());
            
            if (changeListener != null) {
                changeListener.onError(mountPath, error);
            }
        }
    }
    
    /**
     * File Provider callback implementation
     */
    private class FileProviderCallbackImpl implements FileProviderCallback {
        
        @Override
        public void onProviderRequest(NSFileProviderRequest request) {
            eventExecutor.submit(() -> handleFileProviderRequest(request));
        }
        
        @Override
        public void onItemChanged(String path) {
            eventExecutor.submit(() -> {
                // Sync change back to InMemoryFileSystem
                syncFileProviderChange(path);
            });
        }
    }
    
    /**
     * Handle FSEvents and route to ChangeListener
     */
    private void handleFSEvent(FSEvent event) {
        String path = event.getPath();
        int flags = event.getFlags();
        
        try {
            // Convert FSEvent to ChangeListener calls
            if ((flags & FSEventsMonitor.kFSEventStreamEventFlagItemCreated) != 0) {
                FileMetadata metadata = createFileMetadata(path);
                
                // Update InMemoryFileSystem
                updateMemoryFileSystem(path, FileOperation.CREATE);
                
                // Notify listener
                if (changeListener != null) {
                    changeListener.onFileCreated(path, metadata);
                }
                
            } else if ((flags & FSEventsMonitor.kFSEventStreamEventFlagItemModified) != 0) {
                FileMetadata metadata = createFileMetadata(path);
                
                // Update InMemoryFileSystem
                updateMemoryFileSystem(path, FileOperation.MODIFY);
                
                // Notify listener
                if (changeListener != null) {
                    changeListener.onFileModified(path, metadata);
                }
                
            } else if ((flags & FSEventsMonitor.kFSEventStreamEventFlagItemRemoved) != 0) {
                // Update InMemoryFileSystem
                updateMemoryFileSystem(path, FileOperation.DELETE);
                
                // Notify listener
                if (changeListener != null) {
                    changeListener.onFileDeleted(path);
                }
            }
            
            // Check for registered callbacks
            FileChangeCallback callback = callbacks.get(path);
            if (callback != null) {
                // Handle on-demand loading if needed
                handleOnDemandLoad(path, callback);
            }
            
        } catch (Exception e) {
            System.err.printf("Error handling FSEvent for %s: %s%n", path, e.getMessage());
            
            if (changeListener != null) {
                changeListener.onError(path, new PlatformException("FSEvent handling error", e));
            }
        }
    }
    
    /**
     * Update InMemoryFileSystem based on file system events
     */
    private void updateMemoryFileSystem(String path, FileOperation operation) {
        if (memoryFileSystem == null) return;
        
        switch (operation) {
            case CREATE:
                // Check if it's a directory or file
                if (java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path))) {
                    memoryFileSystem.createDirectory(path);
                } else {
                    // For files, we might need to load content or create placeholder
                    if (placeholderManager != null && placeholderManager.isPlaceholder(path)) {
                        // Handle placeholder file
                        handlePlaceholderFile(path);
                    } else {
                        // Regular file - load content
                        loadFileContent(path);
                    }
                }
                break;
                
            case MODIFY:
                // Update existing file in memory filesystem
                if (!java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path))) {
                    loadFileContent(path);
                }
                break;
                
            case DELETE:
                // Remove from memory filesystem
                memoryFileSystem.delete(path);
                break;
        }
    }
    
    /**
     * Load file content into InMemoryFileSystem
     */
    private void loadFileContent(String path) {
        try {
            byte[] content = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
            memoryFileSystem.createFile(path, content);
            
        } catch (Exception e) {
            System.err.printf("Error loading file content for %s: %s%n", path, e.getMessage());
        }
    }
    
    /**
     * Handle placeholder file operations
     */
    private void handlePlaceholderFile(String path) {
        if (placeholderManager == null) return;
        
        // Create placeholder in memory filesystem
        memoryFileSystem.createFile(path, new byte[0]); // Empty placeholder
        
        // Set up lazy loading
        FileChangeCallback callback = callbacks.get(path);
        if (callback != null) {
            placeholderManager.createPlaceholder(path, null);
        }
    }
    
    /**
     * Handle on-demand file loading
     */
    private void handleOnDemandLoad(String path, FileChangeCallback callback) {
        // Trigger async loading
        callback.onDemandLoad(path).thenAccept(content -> {
            try {
                // Update InMemoryFileSystem with loaded content
                memoryFileSystem.createFile(path, content);
                
                // Update File Provider
                fileProvider.updatePlaceholder(path, content);
                
                // Cache content if needed
                if (callback.shouldCache(path) && placeholderManager != null) {
                    placeholderManager.cacheContent(path, content);
                }
                
            } catch (Exception e) {
                System.err.printf("Error handling on-demand load for %s: %s%n", path, e.getMessage());
            }
        });
    }
    
    /**
     * Handle File Provider requests (file access from Finder)
     */
    private void handleFileProviderRequest(NSFileProviderRequest request) {
        String path = request.getPath();
        
        try {
            // Check if file exists in InMemoryFileSystem
            Node node = memoryFileSystem.getNode(path).orElse(null);
            
            if (node == null) {
                // File doesn't exist in memory - might be blockchain file
                FileChangeCallback callback = callbacks.get(path);
                if (callback != null) {
                    // Trigger on-demand loading
                    handleOnDemandLoad(path, callback);
                }
            } else {
                // File exists - provide content to File Provider
                if (node instanceof io.f1r3fly.f1r3drive.filesystem.File) {
                    io.f1r3fly.f1r3drive.filesystem.File file = 
                        (io.f1r3fly.f1r3drive.filesystem.File) node;
                    
                    fileProvider.updatePlaceholder(path, file.getContent());
                }
            }
            
        } catch (Exception e) {
            System.err.printf("Error handling File Provider request for %s: %s%n", path, e.getMessage());
        }
    }
    
    /**
     * Sync File Provider changes back to InMemoryFileSystem
     */
    private void syncFileProviderChange(String path) {
        // This handles changes made through File Provider back to our filesystem
        try {
            NSFileProviderItem item = fileProvider.getProviderItem(path);
            if (item != null && item.hasContent()) {
                byte[] content = item.getContent();
                memoryFileSystem.createFile(path, content);
            }
            
        } catch (Exception e) {
            System.err.printf("Error syncing File Provider change for %s: %s%n", path, e.getMessage());
        }
    }
    
    /**
     * Create file metadata from path
     */
    private FileMetadata createFileMetadata(String path) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            java.nio.file.attribute.BasicFileAttributes attrs = 
                java.nio.file.Files.readAttributes(filePath, java.nio.file.attribute.BasicFileAttributes.class);
            
            return new FileMetadata(
                attrs.size(),
                attrs.lastModifiedTime().toMillis(),
                attrs.isDirectory(),
                attrs.isRegularFile()
            );
            
        } catch (Exception e) {
            // Return default metadata if can't read attributes
            return new FileMetadata(0L, System.currentTimeMillis(), false, true);
        }
    }
    
    /**
     * File operation types
     */
    private enum FileOperation {
        CREATE, MODIFY, DELETE
    }
}
```

---

## 3. Integration Example with F1r3DriveFuse

### Updated F1r3DriveFuse.java (key parts)

```java
package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.platform.*;
import io.f1r3fly.f1r3drive.platform.macos.MacOSChangeWatcher;
import io.f1r3fly.f1r3drive.filesystem.InMemoryFileSystem;
import io.f1r3fly.f1r3drive.state.StateChangeEventsManager;
import io.f1r3fly.f1r3drive.placeholder.PlaceholderManager;

public class F1r3DriveFuse {
    
    // Existing fields
    private InMemoryFileSystem fileSystem;
    private StateChangeEventsManager stateManager;
    private F1r3flyBlockchainClient blockchainClient;
    
    // New fields for platform integration
    private ChangeWatcher changeWatcher;
    private PlaceholderManager placeholderManager;
    private ChangeListener changeListener;
    
    /**
     * Updated initialization method
     */
    public void initialize(String mountPath, BlockchainContext context) throws Exception {
        // Existing initialization
        this.fileSystem = new InMemoryFileSystem();
        this.stateManager = new StateChangeEventsManager();
        this.blockchainClient = new F1r3flyBlockchainClient(context);
        
        // NEW: Create platform-specific ChangeWatcher
        this.changeWatcher = createPlatformChangeWatcher();
        
        // NEW: Initialize PlaceholderManager
        this.placeholderManager = new PlaceholderManager();
        
        // NEW: Create and setup ChangeListener
        this.changeListener = new F1r3DriveChangeListener(stateManager, placeholderManager);
        
        // NEW: Configure platform integration
        setupPlatformIntegration(mountPath);
        
        System.out.println("F1r3DriveFuse initialized with platform-specific monitoring");
    }
    
    /**
     * Create platform-specific ChangeWatcher
     */
    private ChangeWatcher createPlatformChangeWatcher() throws UnsupportedPlatformException {
        return ChangeWatcherFactory.create(); // Auto-detects platform
    }
    
    /**
     * Setup platform integration
     */
    private void setupPlatformIntegration(String mountPath) throws PlatformException {
        // Initialize ChangeWatcher
        changeWatcher.initialize(mountPath, changeListener);
        
        // For macOS, set up additional integration
        if (changeWatcher instanceof MacOSChangeWatcher) {
            MacOSChangeWatcher macOSWatcher = (MacOSChangeWatcher) changeWatcher;
            
            // Connect with InMemoryFileSystem
            macOSWatcher.setMemoryFileSystem(fileSystem);
            macOSWatcher.setPlaceholderManager(placeholderManager);
            
            System.out.println("macOS-specific integration configured");
        }
        
        // Set up shutdown hooks for cleanup
        setupShutdownHooks();
    }
    
    /**
     * Updated start method
     */
    public void start() throws Exception {
        // Start blockchain client
        blockchainClient.connect();
        
        // Start state management
        stateManager.start();
        
        // NEW: Start platform monitoring
        changeWatcher.startMonitoring();
        
        // NEW: Preload blockchain files as placeholders
        preloadBlockchainFiles();
        
        System.out.println("F1r3DriveFuse started with file monitoring");
        
        // Keep application running
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    /**
     * Preload blockchain files as placeholders
     */
    private void preloadBlockchainFiles() {
        // Get wallet files from blockchain
        blockchainClient.listWalletFiles().thenAccept(files -> {
            for (String filePath : files) {
                // Register file change callback for lazy loading
                changeWatcher.registerFileChangeCallback(filePath, new BlockchainFileCallback(filePath));
                
                System.out.printf("Registered blockchain file: %s%n", filePath);
            }
        });
    }
    
    /**
     * Updated shutdown method
     */
    public void shutdown() {
        System.out.println("Shutting down F1r3DriveFuse...");
        
        // NEW: Stop platform monitoring
        if (changeWatcher != null) {
            changeWatcher.stopMonitoring();
            changeWatcher.cleanup();
        }
        
        // NEW: Cleanup placeholder manager
        if (placeholderManager != null) {
            placeholderManager.cleanup();
        }
        
        // Existing cleanup
        if (stateManager != null) {
            stateManager.shutdown();
        }
        
        if (blockchainClient != null) {
            blockchainClient.disconnect();
        }
        
        System.out.println("F1r3DriveFuse shutdown complete");
    }
    
    /**
     * Setup shutdown hooks for proper cleanup
     */
    private void setupShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered - cleaning up...");
            shutdown();
        }));
    }
    
    /**
     * ChangeListener implementation for F1r3Drive
     */
    private class F1r3DriveChangeListener implements ChangeListener {
        
        private final StateChangeEventsManager stateManager;
        private final PlaceholderManager placeholderManager;
        
        public F1r3DriveChangeListener(StateChangeEventsManager stateManager, 
                                       PlaceholderManager placeholderManager) {
            this.stateManager = stateManager;
            this.placeholderManager = placeholderManager;
        }
        
        @Override
        public void onFileCreated(String path, FileMetadata metadata) {
            System.out.printf("File created: %s (size: %d)%n", path, metadata.getSize());
            
            // Route to state manager
            stateManager.notifyFileChange(path, ChangeType.CREATED);
        }
        
        @Override
        public void onFileModified(String path, FileMetadata metadata) {
            System.out.printf("File modified: %s (size: %d)%n", path, metadata.getSize());
            
            // Update placeholder if needed
            if (placeholderManager.isPlaceholder(path)) {
                placeholderManager.clearCache(path); // Clear old cache
            }
            
            // Route to state manager
            stateManager.notifyFileChange(path, ChangeType.MODIFIED);
        }
        
        @Override
        public void onFileDeleted(String path) {
            System.out.printf("File deleted: %s%n", path);
            
            // Remove placeholder
            placeholderManager.removePlaceholder(path);
            
            // Route to state manager
            stateManager.notifyFileChange(path, ChangeType.DELETED);
        }
        
        @Override
        public void onFileAccessed(String path, AccessType accessType) {
            System.out.printf("File accessed: %s (type: %s)%n", path, accessType);
            
            // Trigger lazy loading if it's a placeholder
            if (placeholderManager.isPlaceholder(path)) {
                placeholderManager.preloadContent(path);
            }
        }
        
        @Override
        public void onDirectoryCreated(String path) {
            System.out.printf("Directory created: %s%n", path);
            stateManager.notifyFileChange(path, ChangeType.DIR_CREATED);
        }
        
        @Override
        public void onDirectoryDeleted(String path) {
            System.out.printf("Directory deleted: %s%n", path);
            stateManager.notifyFileChange(path, ChangeType.DIR_DELETED);
        }
        
        @Override
        public void onError(String path, Exception error) {
            System.err.printf("File system error at %s: %s%n", path, error.getMessage());
        }
    }
    
    /**
     * FileChangeCallback implementation for blockchain files
     */
    private class BlockchainFileCallback implements FileChangeCallback {
        
        private final String filePath;
        
        public BlockchainFileCallback(String filePath) {
            this.filePath = filePath;
        }
        
        @Override
        public CompletableFuture<byte[]> onDemandLoad(String path) {
            System.out.printf("Loading blockchain file on-demand: %s%n", path);
            
            // Load file content from blockchain
            return blockchainClient.readFile(path).thenApply(content -> {
                System.out.printf("Loaded %d bytes for %s%n", content.length, path);
                return content;
            });
        }
        
        @Override
        public void onPlaceholderUpdate(String path, PlaceholderInfo info) {
            System.out.printf("Placeholder updated: %s -> %s%n", path, info.getState());
        }
        
        @Override
        public boolean shouldCache(String path) {
            // Cache blockchain files for better performance
            return true;
        }
    }
}
```

---

## 4. FSEvent and FileMetadata Helper Classes

### FSEvent.java

```java
package io.f1r3fly.f1r3drive.platform.macos;

/**
 * Represents a macOS FSEvents event
 */
public class FSEvent {
    private final String path;
    private final int flags;
    private final long eventId;
    private final long timestamp;
    
    // FSEvents flag constants
    public static final int kFSEventStreamEventFlagItemCreated = 0x00000100;
    public static final int kFSEventStreamEventFlagItemModified = 0x00001000;
    public static final int kFSEventStreamEventFlagItemRemoved = 0x00000200;
    public static final int kFSEventStreamEventFlagItemRenamed = 0x00000800;
    
    public FSEvent(String path, int flags, long eventId) {
        this.path = path;
        this.flags = flags;
        this.eventId = eventId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getPath() { return path; }
    public int getFlags() { return flags; }
    public long getEventId() { return eventId; }
    public long getTimestamp() { return timestamp; }
    
    // Convenience methods
    public boolean isCreated() {
        return (flags & kFSEventStreamEventFlagItemCreated) != 0;
    }
    
    public boolean isModified() {
        return (flags & kFSEventStreamEventFlagItemModified) != 0;
    }
    
    public boolean isDeleted() {
        return (flags & kFSEventStreamEventFlagItemRemoved) != 0;
    }
    
    public boolean isRenamed() {
        return (flags & kFSEventStreamEventFlagItemRenamed) != 0;
    }
    
    @Override
    public String toString() {
        return String.format("FSEvent{path='%s', flags=0x%x, eventId=%d, created=%s, modified=%s, deleted=%s}", 
                           path, flags, eventId, isCreated(), isModified(), isDeleted());
    }
}
```

### FileMetadata.java

```java
package io.f1r3fly.f1r3drive.platform;

/**
 * File metadata for change events
 */
public class FileMetadata {
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private final boolean isRegularFile;
    
    public FileMetadata(long size, long lastModified, boolean isDirectory, boolean isRegularFile) {
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
        this.isRegularFile = isRegularFile;
    }
    
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public boolean isDirectory() { return isDirectory; }
    public boolean isRegularFile() { return isRegularFile; }
    
    @Override
    public String toString() {
        return String.format("FileMetadata{size=%d, lastModified=%d, isDirectory=%s, isRegularFile