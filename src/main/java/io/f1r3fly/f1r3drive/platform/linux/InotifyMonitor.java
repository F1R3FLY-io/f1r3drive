package io.f1r3fly.f1r3drive.platform.linux;

import io.f1r3fly.f1r3drive.platform.ChangeListener;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified inotify monitor using Java's WatchService for Phase 4 testing.
 * This implementation uses standard Java APIs instead of JNR-FFI for compatibility.
 */
public class InotifyMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        InotifyMonitor.class
    );

    // Inotify event constants for compatibility
    public static final int IN_ACCESS = 0x00000001;
    public static final int IN_MODIFY = 0x00000002;
    public static final int IN_ATTRIB = 0x00000004;
    public static final int IN_CLOSE_WRITE = 0x00000008;
    public static final int IN_CLOSE_NOWRITE = 0x00000010;
    public static final int IN_OPEN = 0x00000020;
    public static final int IN_MOVED_FROM = 0x00000040;
    public static final int IN_MOVED_TO = 0x00000080;
    public static final int IN_CREATE = 0x00000100;
    public static final int IN_DELETE = 0x00000200;
    public static final int IN_DELETE_SELF = 0x00000400;
    public static final int IN_MOVE_SELF = 0x00000800;

    // Helper constants
    public static final int IN_CLOSE = (IN_CLOSE_WRITE | IN_CLOSE_NOWRITE);
    public static final int IN_MOVE = (IN_MOVED_FROM | IN_MOVED_TO);

    // Special flags
    public static final int IN_ONLYDIR = 0x01000000;
    public static final int IN_DONT_FOLLOW = 0x02000000;
    public static final int IN_EXCL_UNLINK = 0x04000000;
    public static final int IN_MASK_ADD = 0x20000000;
    public static final int IN_ISDIR = 0x40000000;
    public static final int IN_ONESHOT = 0x80000000;

    public static final int IN_CLOEXEC = 0x80000;
    public static final int IN_NONBLOCK = 0x800;

    private final ChangeListener changeListener;
    private final Map<WatchKey, String> watchKeys;
    private final Map<String, WatchKey> pathToWatchKey;
    private final AtomicBoolean monitoring;
    private final ExecutorService executorService;

    private WatchService watchService;
    private Future<?> monitoringTask;
    private String rootPath;

    public InotifyMonitor(ChangeListener changeListener) {
        if (changeListener == null) {
            throw new NullPointerException("ChangeListener cannot be null");
        }

        this.changeListener = changeListener;
        this.watchKeys = new ConcurrentHashMap<>();
        this.pathToWatchKey = new ConcurrentHashMap<>();
        this.monitoring = new AtomicBoolean(false);
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "InotifyMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts monitoring the specified path recursively.
     */
    public void startMonitoring(String path) throws IOException {
        if (monitoring.get()) {
            throw new IllegalStateException("Monitoring is already active");
        }

        if (path == null) {
            throw new NullPointerException("Path cannot be null");
        }

        this.rootPath = path;

        try {
            // Initialize watch service
            watchService = FileSystems.getDefault().newWatchService();

            // Add watch for the root path and all subdirectories
            addWatchRecursively(path);

            monitoring.set(true);

            // Start monitoring task
            monitoringTask = executorService.submit(this::monitoringLoop);

            LOGGER.info("Started file monitoring for path: {}", path);
            changeListener.onMonitoringStarted(path);
        } catch (Exception e) {
            cleanup();
            throw new IOException("Failed to start monitoring", e);
        }
    }

    /**
     * Stops monitoring file system changes.
     */
    public void stopMonitoring() {
        if (!monitoring.get()) {
            return;
        }

        monitoring.set(false);

        // Cancel monitoring task
        if (monitoringTask != null) {
            monitoringTask.cancel(true);
        }

        cleanup();

        LOGGER.info("Stopped file monitoring");
        if (rootPath != null) {
            changeListener.onMonitoringStopped(rootPath);
        }
    }

    /**
     * Checks if monitoring is active.
     */
    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * Gets the number of active watch keys.
     */
    public int getWatchCount() {
        return watchKeys.size();
    }

    /**
     * Gets statistics about the monitor.
     */
    public MonitoringStatistics getStatistics() {
        return new MonitoringStatistics(
            watchKeys.size(),
            monitoring.get(),
            watchService != null
        );
    }

    private void addWatchRecursively(String path) throws IOException {
        Path rootPath = Paths.get(path);

        // Add watch for the root directory
        addWatch(rootPath);

        // Recursively add watches for subdirectories
        if (java.nio.file.Files.isDirectory(rootPath)) {
            try {
                java.nio.file.Files.walk(rootPath)
                    .filter(java.nio.file.Files::isDirectory)
                    .filter(p -> !p.equals(rootPath))
                    .forEach(p -> {
                        try {
                            addWatch(p);
                        } catch (IOException e) {
                            LOGGER.warn(
                                "Failed to add watch for directory: {}",
                                p,
                                e
                            );
                        }
                    });
            } catch (IOException e) {
                LOGGER.warn("Failed to walk directory tree: {}", path, e);
            }
        }
    }

    private void addWatch(Path path) throws IOException {
        String pathStr = path.toString();

        if (pathToWatchKey.containsKey(pathStr)) {
            return; // Already watching
        }

        WatchKey key = path.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );

        watchKeys.put(key, pathStr);
        pathToWatchKey.put(pathStr, key);

        LOGGER.debug("Added watch for: {}", pathStr);
    }

    private void removeWatch(String path) {
        WatchKey key = pathToWatchKey.remove(path);
        if (key != null) {
            watchKeys.remove(key);
            key.cancel();
            LOGGER.debug("Removed watch for: {}", path);
        }
    }

    private void monitoringLoop() {
        LOGGER.debug("Started monitoring loop");

        while (monitoring.get() && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                String watchedPath = watchKeys.get(key);

                if (watchedPath == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    String fullPath = Paths.get(
                        watchedPath,
                        fileName.toString()
                    ).toString();

                    processEvent(kind, fullPath);
                }

                // Reset the key
                boolean valid = key.reset();
                if (!valid) {
                    // Directory is no longer accessible
                    String removedPath = watchKeys.remove(key);
                    if (removedPath != null) {
                        pathToWatchKey.remove(removedPath);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in monitoring loop", e);
                changeListener.onError(e, null);
            }
        }

        LOGGER.debug("Monitoring loop ended");
    }

    private void processEvent(WatchEvent.Kind<?> kind, String fullPath) {
        try {
            // Handle directory creation - add new watch
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                Path path = Paths.get(fullPath);
                if (java.nio.file.Files.isDirectory(path)) {
                    try {
                        addWatch(path);
                    } catch (IOException e) {
                        LOGGER.warn(
                            "Failed to add watch for new directory: {}",
                            fullPath,
                            e
                        );
                    }
                }
                changeListener.onFileCreated(fullPath);
            }

            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // Remove watch if it was a directory
                removeWatch(fullPath);
                changeListener.onFileDeleted(fullPath);
            }

            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                changeListener.onFileModified(fullPath);
            }

            LOGGER.trace("Processed event: {} for path: {}", kind, fullPath);
        } catch (Exception e) {
            LOGGER.error("Error processing event for path: {}", fullPath, e);
            changeListener.onError(e, fullPath);
        }
    }

    private void cleanup() {
        // Cancel all watch keys
        for (WatchKey key : watchKeys.keySet()) {
            key.cancel();
        }
        watchKeys.clear();
        pathToWatchKey.clear();

        // Close watch service
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing watch service", e);
            }
            watchService = null;
        }

        // Shutdown executor
        executorService.shutdown();
    }

    /**
     * Statistics about the monitor.
     */
    public static class MonitoringStatistics {

        private final int watchCount;
        private final boolean monitoring;
        private final boolean watchServiceActive;

        public MonitoringStatistics(
            int watchCount,
            boolean monitoring,
            boolean watchServiceActive
        ) {
            this.watchCount = watchCount;
            this.monitoring = monitoring;
            this.watchServiceActive = watchServiceActive;
        }

        public int getWatchCount() {
            return watchCount;
        }

        public boolean isMonitoring() {
            return monitoring;
        }

        public boolean isInotifyActive() {
            return watchServiceActive;
        }

        @Override
        public String toString() {
            return String.format(
                "InotifyMonitor[watches=%d, monitoring=%s, active=%s]",
                watchCount,
                monitoring,
                watchServiceActive
            );
        }
    }
}
