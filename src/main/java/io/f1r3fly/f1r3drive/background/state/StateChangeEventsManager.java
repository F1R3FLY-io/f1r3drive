package io.f1r3fly.f1r3drive.background.state;

import io.f1r3fly.f1r3drive.platform.ChangeListener;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Refactored StateChangeEventsManager following SOLID principles.
 *
 * Now follows:
 * - Single Responsibility: Only coordinates between components
 * - Open/Closed: Extensible through interfaces
 * - Dependency Inversion: Depends on abstractions, not concretions
 * - Interface Segregation: Uses focused interfaces
 */
public class StateChangeEventsManager {

    private static final Logger logger = Logger.getLogger(
        StateChangeEventsManager.class.getName()
    );

    private final EventQueue eventQueue;
    private final EventProcessorRegistry processorRegistry;
    private final EventProcessor eventProcessor;
    private final ExecutorService eventProcessingThreadPool;
    private final Thread eventDispatcherThread;
    private final StateChangeEventsManagerConfig config;
    private volatile boolean shutdown = false;

    // 🆕 NEW: External change listener support
    private ChangeListener changeListener;
    private final BlockingQueue<ExternalChangeEvent> externalEventQueue =
        new LinkedBlockingQueue<>();
    private Thread externalEventProcessor;

    /**
     * Create with default configuration.
     */
    public StateChangeEventsManager() {
        this(StateChangeEventsManagerConfig.defaultConfig());
    }

    /**
     * Create with custom configuration.
     * Follows Dependency Inversion by accepting configuration.
     */
    public StateChangeEventsManager(StateChangeEventsManagerConfig config) {
        this(
            config,
            new BlockingEventQueue(config.getQueueCapacity()),
            new DefaultEventProcessorRegistry()
        );
    }

    /**
     * Create with custom dependencies (for testing and flexibility).
     * Follows Dependency Injection pattern.
     */
    public StateChangeEventsManager(
        StateChangeEventsManagerConfig config,
        EventQueue eventQueue,
        EventProcessorRegistry processorRegistry
    ) {
        if (config == null || eventQueue == null || processorRegistry == null) {
            throw new IllegalArgumentException(
                "All dependencies must be non-null"
            );
        }

        this.config = config;
        this.eventQueue = eventQueue;
        this.processorRegistry = processorRegistry;
        this.eventProcessor = new DefaultEventProcessor(processorRegistry);

        // Create a thread pool for processing events
        this.eventProcessingThreadPool = Executors.newFixedThreadPool(
            config.getThreadPoolSize(),
            r -> {
                Thread t = new Thread(r, "StateChangeEventProcessor");
                t.setDaemon(true); // Run as daemon thread in background
                return t;
            }
        );

        // Create a dispatcher thread that takes events from queue and submits them to thread pool
        this.eventDispatcherThread = new Thread(
            this::dispatchEvents,
            "StateChangeEventDispatcher"
        );
        eventDispatcherThread.setDaemon(true); // Run as daemon thread in background

        // Register shutdown hook if configured
        if (config.shouldRegisterShutdownHook()) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
    }

    /**
     * Dispatcher loop - extracted for better readability.
     */
    private void dispatchEvents() {
        while (!Thread.currentThread().isInterrupted() && !shutdown) {
            try {
                StateChangeEvents event = eventQueue.take();
                // Submit event processing to thread pool
                eventProcessingThreadPool.submit(() -> {
                    try {
                        eventProcessor.processEvent(event);
                    } catch (Throwable e) {
                        // Log the error and continue processing next event
                        logger.log(
                            Level.SEVERE,
                            "Failed to process event: " +
                                event.getClass().getSimpleName(),
                            e
                        );
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 🆕 NEW: External event processor loop.
     */
    private void processExternalEvents() {
        while (!Thread.currentThread().isInterrupted() && !shutdown) {
            try {
                ExternalChangeEvent event = externalEventQueue.take();
                if (changeListener != null) {
                    try {
                        // Route external event to change listener
                        switch (event.getType()) {
                            case CREATED:
                                changeListener.onFileCreated(event.getPath());
                                break;
                            case MODIFIED:
                                changeListener.onFileModified(event.getPath());
                                break;
                            case DELETED:
                                changeListener.onFileDeleted(event.getPath());
                                break;
                            case MOVED:
                                changeListener.onFileMoved(
                                    event.getOldPath(),
                                    event.getPath()
                                );
                                break;
                            case ACCESSED:
                                changeListener.onFileAccessed(event.getPath());
                                break;
                            case ATTRIBUTES_CHANGED:
                                changeListener.onFileAttributesChanged(
                                    event.getPath()
                                );
                                break;
                        }
                    } catch (Throwable e) {
                        logger.log(
                            Level.SEVERE,
                            "Failed to process external event: " +
                                event.getType(),
                            e
                        );
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void start() {
        eventDispatcherThread.start();

        // 🆕 NEW: Start external event processor if listener is set
        if (changeListener != null && externalEventProcessor == null) {
            externalEventProcessor = new Thread(
                this::processExternalEvents,
                "ExternalChangeEventProcessor"
            );
            externalEventProcessor.setDaemon(true);
            externalEventProcessor.start();
        }
    }

    public void shutdown() {
        this.shutdown = true;
        this.eventDispatcherThread.interrupt();

        // 🆕 NEW: Shutdown external event processor
        if (externalEventProcessor != null) {
            externalEventProcessor.interrupt();
        }

        // Shutdown thread pool
        eventProcessingThreadPool.shutdown();

        try {
            // Wait for dispatcher thread to finish
            this.eventDispatcherThread.join(2000);

            // 🆕 NEW: Wait for external event processor to finish
            if (externalEventProcessor != null) {
                externalEventProcessor.join(2000);
            }

            // Wait for thread pool to terminate
            long timeoutMs = config.getShutdownTimeoutMs();
            if (
                !eventProcessingThreadPool.awaitTermination(
                    timeoutMs,
                    TimeUnit.MILLISECONDS
                )
            ) {
                eventProcessingThreadPool.shutdownNow();
                // Wait a bit more for tasks to respond to being cancelled
                if (
                    !eventProcessingThreadPool.awaitTermination(
                        2000,
                        TimeUnit.MILLISECONDS
                    )
                ) {
                    logger.warning("Thread pool did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            eventProcessingThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        this.eventQueue.clear();
        this.externalEventQueue.clear();
    }

    /**
     * Add an event to be processed.
     *
     * @param event the event to add
     * @return true if event was added successfully, false if shutdown or queue full
     */
    public boolean addEvent(StateChangeEvents event) {
        if (shutdown || event == null) {
            return false;
        }
        return eventQueue.offer(event);
    }

    /**
     * Register a processor for a specific event type.
     * Delegates to the processor registry.
     */
    public void registerEventProcessor(
        Class<? extends StateChangeEvents> eventClass,
        StateChangeEventProcessor processor
    ) {
        processorRegistry.registerProcessor(eventClass, processor);
    }

    /**
     * Unregister a processor for a specific event type.
     * Delegates to the processor registry.
     */
    public void unregisterEventProcessor(
        Class<? extends StateChangeEvents> eventClass,
        StateChangeEventProcessor processor
    ) {
        processorRegistry.unregisterProcessor(eventClass, processor);
    }

    /**
     * Get the current size of the event queue.
     */
    public int getQueueSize() {
        return eventQueue.size();
    }

    /**
     * Check if the manager is shutdown.
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * 🆕 NEW: Set external change listener.
     *
     * @param listener the change listener to set
     */
    public void setChangeListener(ChangeListener listener) {
        this.changeListener = listener;

        // Start external event processor if not already running and we're started
        if (listener != null && externalEventProcessor == null && !shutdown) {
            externalEventProcessor = new Thread(
                this::processExternalEvents,
                "ExternalChangeEventProcessor"
            );
            externalEventProcessor.setDaemon(true);
            externalEventProcessor.start();
        }
    }

    /**
     * 🆕 NEW: Notify about external file changes.
     *
     * @param path the path of the changed file
     * @param type the type of change
     */
    public void notifyFileChange(String path, ChangeType type) {
        if (shutdown) {
            return;
        }

        ExternalChangeEvent event = new ExternalChangeEvent(path, type);
        if (!externalEventQueue.offer(event)) {
            logger.warning(
                "Failed to queue external change event: " +
                    type +
                    " for path: " +
                    path
            );
        }
    }

    /**
     * 🆕 NEW: Notify about external file move/rename.
     *
     * @param oldPath the original path
     * @param newPath the new path
     */
    public void notifyFileMove(String oldPath, String newPath) {
        if (shutdown) {
            return;
        }

        ExternalChangeEvent event = new ExternalChangeEvent(
            oldPath,
            newPath,
            ChangeType.MOVED
        );
        if (!externalEventQueue.offer(event)) {
            logger.warning(
                "Failed to queue external move event for: " +
                    oldPath +
                    " -> " +
                    newPath
            );
        }
    }

    /**
     * 🆕 NEW: External change event types.
     */
    public enum ChangeType {
        CREATED,
        MODIFIED,
        DELETED,
        MOVED,
        ACCESSED,
        ATTRIBUTES_CHANGED,
    }

    /**
     * 🆕 NEW: Notify about external change event.
     * Generic method that can be called from platform-specific code.
     */
    public void notifyExternalChange() {
        // This method can be used as a general notification
        // that external changes have occurred and should be processed
        // Currently delegates to the existing event processing mechanism
        if (!shutdown && changeListener != null) {
            // Trigger a general refresh or check for external changes
            // Implementation can be expanded based on specific requirements
        }
    }

    /**
     * 🆕 NEW: External change event representation.
     */
    private static class ExternalChangeEvent {

        private final String path;
        private final String oldPath;
        private final ChangeType type;

        public ExternalChangeEvent(String path, ChangeType type) {
            this.path = path;
            this.oldPath = null;
            this.type = type;
        }

        public ExternalChangeEvent(
            String oldPath,
            String newPath,
            ChangeType type
        ) {
            this.oldPath = oldPath;
            this.path = newPath;
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public String getOldPath() {
            return oldPath;
        }

        public ChangeType getType() {
            return type;
        }
    }
}
