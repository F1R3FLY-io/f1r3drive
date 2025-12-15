package io.f1r3fly.f1r3drive.platform.macos;

import io.f1r3fly.f1r3drive.platform.ChangeListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JNI integration with macOS FSEvents API for low-level filesystem monitoring.
 * Runs in separate thread with CFRunLoop and provides efficient file system event detection.
 *
 * This class bridges Java code with native macOS FSEvents through JNI calls to
 * libf1r3drive-fsevents.dylib native library.
 */
public class FSEventsMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        FSEventsMonitor.class
    );

    // Native library loading
    static {
        try {
            NativeLibraryLoader.loadFSEventsLibrary();
            LOGGER.info("Successfully loaded native FSEvents library");
        } catch (UnsatisfiedLinkError e) {
            LOGGER.error(
                "Failed to load native FSEvents library: {}",
                e.getMessage()
            );
            LOGGER.error(
                "Platform info: {}",
                NativeLibraryLoader.getPlatformInfo()
            );
            throw new RuntimeException(
                "Native FSEvents library not available",
                e
            );
        }
    }

    // FSEvents constants (from FSEvents.h)
    public static final int kFSEventStreamCreateFlagNoDefer = 0x00000002;
    public static final int kFSEventStreamCreateFlagWatchRoot = 0x00000004;
    public static final int kFSEventStreamCreateFlagFileEvents = 0x00000010;
    public static final int kFSEventStreamCreateFlagMarkSelf = 0x00000020;
    public static final int kFSEventStreamCreateFlagIgnoreSelf = 0x00000008;

    // Event flags (from FSEvents.h)
    public static final int kFSEventStreamEventFlagItemCreated = 0x00000100;
    public static final int kFSEventStreamEventFlagItemRemoved = 0x00000200;
    public static final int kFSEventStreamEventFlagItemInodeMetaMod =
        0x00000400;
    public static final int kFSEventStreamEventFlagItemRenamed = 0x00000800;
    public static final int kFSEventStreamEventFlagItemModified = 0x00001000;
    public static final int kFSEventStreamEventFlagItemFinderInfoMod =
        0x00002000;
    public static final int kFSEventStreamEventFlagItemChangeOwner = 0x00004000;
    public static final int kFSEventStreamEventFlagItemXattrMod = 0x00008000;
    public static final int kFSEventStreamEventFlagItemIsFile = 0x00010000;
    public static final int kFSEventStreamEventFlagItemIsDir = 0x00020000;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private Thread monitorThread;
    private ChangeListener changeListener;
    private String watchedPath;
    private long streamRef = 0; // Native FSEventStreamRef pointer
    private CountDownLatch startLatch;
    private CountDownLatch stopLatch;

    // Configuration parameters
    private double latency = 0.1; // 100ms latency
    private int streamFlags =
        kFSEventStreamCreateFlagFileEvents |
        kFSEventStreamCreateFlagWatchRoot |
        kFSEventStreamCreateFlagNoDefer;

    /**
     * Creates a new FSEventsMonitor instance.
     */
    public FSEventsMonitor() {
        LOGGER.debug("FSEventsMonitor instance created");
    }

    /**
     * Starts monitoring the specified path for file system events.
     *
     * @param path the path to monitor
     * @param listener the listener to notify of changes
     * @throws Exception if monitoring cannot be started
     */
    public synchronized void startMonitoring(
        String path,
        ChangeListener listener
    ) throws Exception {
        if (isRunning.get()) {
            throw new IllegalStateException("Monitor is already running");
        }

        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        if (listener == null) {
            throw new IllegalArgumentException("ChangeListener cannot be null");
        }

        this.watchedPath = path;
        this.changeListener = listener;
        this.startLatch = new CountDownLatch(1);

        LOGGER.info("Starting FSEvents monitoring for path: {}", path);

        // Create and start monitoring thread
        monitorThread = new Thread(
            this::runEventLoop,
            "FSEventsMonitor-" + path.hashCode()
        );
        monitorThread.setDaemon(true);
        monitorThread.start();

        // Wait for initialization to complete
        if (!startLatch.await(10, TimeUnit.SECONDS)) {
            stopMonitoring();
            throw new Exception(
                "Failed to start FSEvents monitoring within timeout"
            );
        }

        if (!isInitialized.get()) {
            stopMonitoring();
            throw new Exception("Failed to initialize FSEvents stream");
        }

        LOGGER.info(
            "FSEvents monitoring started successfully for path: {}",
            path
        );
    }

    /**
     * Stops monitoring file system changes.
     */
    public synchronized void stopMonitoring() {
        if (!isRunning.get()) {
            return;
        }

        LOGGER.info("Stopping FSEvents monitoring for path: {}", watchedPath);

        isRunning.set(false);
        stopLatch = new CountDownLatch(1);

        // Stop native stream
        if (streamRef != 0) {
            nativeStopStream(streamRef);
        }

        // Wait for thread to finish
        if (monitorThread != null) {
            try {
                if (!stopLatch.await(5, TimeUnit.SECONDS)) {
                    LOGGER.warn(
                        "FSEvents monitoring thread did not stop gracefully, interrupting"
                    );
                    monitorThread.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn(
                    "Interrupted while waiting for monitoring thread to stop"
                );
            }
        }

        cleanup();
        LOGGER.info("FSEvents monitoring stopped for path: {}", watchedPath);
    }

    /**
     * Checks if the monitor is currently active.
     *
     * @return true if monitoring is active, false otherwise
     */
    public boolean isMonitoring() {
        return isRunning.get() && isInitialized.get();
    }

    /**
     * Sets the latency for FSEvents stream.
     *
     * @param latency latency in seconds (default: 0.1)
     */
    public void setLatency(double latency) {
        if (isRunning.get()) {
            throw new IllegalStateException(
                "Cannot change latency while monitoring is active"
            );
        }
        this.latency = latency;
    }

    /**
     * Sets additional stream flags for FSEvents.
     *
     * @param flags additional flags to combine with default flags
     */
    public void setAdditionalStreamFlags(int flags) {
        if (isRunning.get()) {
            throw new IllegalStateException(
                "Cannot change flags while monitoring is active"
            );
        }
        this.streamFlags |= flags;
    }

    /**
     * Main event loop that runs in the monitoring thread.
     */
    private void runEventLoop() {
        try {
            LOGGER.debug(
                "Starting FSEvents run loop for path: {}",
                watchedPath
            );

            isRunning.set(true);

            // Create native FSEvents stream
            streamRef = nativeCreateStream(watchedPath, latency, streamFlags);

            if (streamRef == 0) {
                LOGGER.error(
                    "Failed to create FSEvents stream for path: {}",
                    watchedPath
                );
                isInitialized.set(false);
                startLatch.countDown();
                return;
            }

            // Schedule stream on run loop
            if (!nativeScheduleStream(streamRef)) {
                LOGGER.error("Failed to schedule FSEvents stream on run loop");
                isInitialized.set(false);
                startLatch.countDown();
                return;
            }

            // Start the stream
            if (!nativeStartStream(streamRef)) {
                LOGGER.error("Failed to start FSEvents stream");
                isInitialized.set(false);
                startLatch.countDown();
                return;
            }

            isInitialized.set(true);
            startLatch.countDown();

            // Notify listener that monitoring started
            if (changeListener != null) {
                changeListener.onMonitoringStarted(watchedPath);
            }

            // Run the CFRunLoop (this blocks until stopped)
            nativeRunLoop(streamRef);
        } catch (Exception e) {
            LOGGER.error("Error in FSEvents run loop", e);
            if (changeListener != null) {
                changeListener.onError(e, watchedPath);
            }
        } finally {
            isRunning.set(false);
            isInitialized.set(false);

            if (changeListener != null) {
                changeListener.onMonitoringStopped(watchedPath);
            }

            if (stopLatch != null) {
                stopLatch.countDown();
            }

            LOGGER.debug("FSEvents run loop ended for path: {}", watchedPath);
        }
    }

    /**
     * Callback method called from native code when file system events occur.
     * This method is called from the native FSEvents callback function.
     *
     * @param paths array of file paths that changed
     * @param flags array of event flags for each path
     * @param eventIds array of event IDs for each path
     */
    private void onFileSystemEvent(
        String[] paths,
        int[] flags,
        long[] eventIds
    ) {
        if (changeListener == null || !isRunning.get()) {
            return;
        }

        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            int eventFlags = flags[i];
            long eventId = eventIds[i];

            LOGGER.debug(
                "FSEvent: path={}, flags=0x{}, eventId={}",
                path,
                Integer.toHexString(eventFlags),
                eventId
            );

            try {
                processEvent(path, eventFlags);
            } catch (Exception e) {
                LOGGER.error("Error processing FSEvent for path: {}", path, e);
                changeListener.onError(e, path);
            }
        }
    }

    /**
     * Processes a single file system event and dispatches to appropriate listener methods.
     *
     * @param path the path where the event occurred
     * @param flags the event flags
     */
    private void processEvent(String path, int flags) {
        // Handle creation
        if ((flags & kFSEventStreamEventFlagItemCreated) != 0) {
            changeListener.onFileCreated(path);
        }

        // Handle deletion
        if ((flags & kFSEventStreamEventFlagItemRemoved) != 0) {
            changeListener.onFileDeleted(path);
        }

        // Handle modification
        if ((flags & kFSEventStreamEventFlagItemModified) != 0) {
            changeListener.onFileModified(path);
        }

        // Handle rename (macOS FSEvents treats moves as renames)
        if ((flags & kFSEventStreamEventFlagItemRenamed) != 0) {
            // For renames, we only get the new path, so we treat it as a creation
            // The old path would have been reported as removed in a separate event
            changeListener.onFileCreated(path);
        }

        // Handle attribute changes
        if (
            (flags &
                (kFSEventStreamEventFlagItemInodeMetaMod |
                    kFSEventStreamEventFlagItemFinderInfoMod |
                    kFSEventStreamEventFlagItemChangeOwner |
                    kFSEventStreamEventFlagItemXattrMod)) !=
            0
        ) {
            changeListener.onFileAttributesChanged(path);
        }
    }

    /**
     * Performs cleanup of native resources.
     */
    private void cleanup() {
        if (streamRef != 0) {
            nativeCleanup(streamRef);
            streamRef = 0;
        }
    }

    // Native method declarations - these are implemented in libf1r3drive-fsevents.dylib

    /**
     * Creates a native FSEvents stream.
     *
     * @param path the path to watch
     * @param latency the stream latency in seconds
     * @param flags the stream creation flags
     * @return native stream reference or 0 on failure
     */
    private native long nativeCreateStream(
        String path,
        double latency,
        int flags
    );

    /**
     * Schedules the stream on the current thread's run loop.
     *
     * @param streamRef the native stream reference
     * @return true if successful, false otherwise
     */
    private native boolean nativeScheduleStream(long streamRef);

    /**
     * Starts the FSEvents stream.
     *
     * @param streamRef the native stream reference
     * @return true if successful, false otherwise
     */
    private native boolean nativeStartStream(long streamRef);

    /**
     * Stops the FSEvents stream.
     *
     * @param streamRef the native stream reference
     */
    private native void nativeStopStream(long streamRef);

    /**
     * Runs the CFRunLoop until stopped.
     *
     * @param streamRef the native stream reference
     */
    private native void nativeRunLoop(long streamRef);

    /**
     * Cleans up native resources.
     *
     * @param streamRef the native stream reference
     */
    private native void nativeCleanup(long streamRef);

    /**
     * Gets information about the native FSEvents implementation.
     *
     * @return information string
     */
    public native String nativeGetVersion();

    /**
     * Checks if the native library is properly loaded and functional.
     *
     * @return true if native library is functional, false otherwise
     */
    public native boolean nativeIsAvailable();
}
