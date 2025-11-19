package io.f1r3fly.f1r3drive.placeholder;

import java.util.Objects;

/**
 * Placeholder file metadata container.
 * Tracks loading state, blockchain addresses for files, access timestamps,
 * and other metadata required for lazy loading management.
 */
public class PlaceholderInfo {

    private final String path;
    private final long expectedSize;
    private final String checksum;
    private final int priority;
    private final long created;

    private volatile PlaceholderState state;
    private volatile long lastAccessed;
    private volatile String errorMessage;
    private volatile int loadAttempts;

    /**
     * Creates a new PlaceholderInfo instance.
     *
     * @param path the file path
     * @param expectedSize expected file size in bytes
     * @param checksum expected file checksum (may be null)
     * @param priority loading priority (higher = more priority)
     * @param created creation timestamp
     */
    public PlaceholderInfo(
        String path,
        long expectedSize,
        String checksum,
        int priority,
        long created
    ) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (expectedSize < 0) {
            throw new IllegalArgumentException(
                "Expected size cannot be negative"
            );
        }

        this.path = path;
        this.expectedSize = expectedSize;
        this.checksum = checksum;
        this.priority = priority;
        this.created = created;
        this.lastAccessed = created;
        this.state = PlaceholderState.PENDING;
        this.loadAttempts = 0;
    }

    /**
     * Gets the file path.
     *
     * @return file path
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the expected file size in bytes.
     *
     * @return expected file size
     */
    public long getExpectedSize() {
        return expectedSize;
    }

    /**
     * Gets the expected file checksum.
     *
     * @return checksum or null if not available
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Gets the loading priority.
     *
     * @return priority value (higher = more priority)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return creation timestamp in milliseconds
     */
    public long getCreated() {
        return created;
    }

    /**
     * Gets the current placeholder state.
     *
     * @return current state
     */
    public PlaceholderState getState() {
        return state;
    }

    /**
     * Sets the placeholder state.
     *
     * @param state new state
     */
    public void setState(PlaceholderState state) {
        this.state = state;
    }

    /**
     * Gets the last access timestamp.
     *
     * @return last access timestamp in milliseconds
     */
    public long getLastAccessed() {
        return lastAccessed;
    }

    /**
     * Sets the last access timestamp.
     *
     * @param lastAccessed last access timestamp in milliseconds
     */
    public void setLastAccessed(long lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    /**
     * Gets the last error message if loading failed.
     *
     * @return error message or null if no error
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for failed loading attempts.
     *
     * @param errorMessage error message
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the number of load attempts.
     *
     * @return load attempt count
     */
    public int getLoadAttempts() {
        return loadAttempts;
    }

    /**
     * Increments the load attempt counter.
     *
     * @return new attempt count
     */
    public int incrementLoadAttempts() {
        return ++loadAttempts;
    }

    /**
     * Resets the load attempt counter.
     */
    public void resetLoadAttempts() {
        this.loadAttempts = 0;
    }

    /**
     * Checks if the placeholder has been accessed recently.
     *
     * @param thresholdMs age threshold in milliseconds
     * @return true if accessed within threshold
     */
    public boolean isRecentlyAccessed(long thresholdMs) {
        return (System.currentTimeMillis() - lastAccessed) <= thresholdMs;
    }

    /**
     * Gets the age of the placeholder since creation.
     *
     * @return age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - created;
    }

    /**
     * Gets the time since last access.
     *
     * @return time since last access in milliseconds
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessed;
    }

    /**
     * Checks if the placeholder is in a failed state.
     *
     * @return true if state is FAILED
     */
    public boolean isFailed() {
        return state == PlaceholderState.FAILED;
    }

    /**
     * Checks if the placeholder is currently being loaded.
     *
     * @return true if state is LOADING
     */
    public boolean isLoading() {
        return state == PlaceholderState.LOADING;
    }

    /**
     * Checks if the placeholder has been successfully loaded.
     *
     * @return true if state is LOADED
     */
    public boolean isLoaded() {
        return state == PlaceholderState.LOADED;
    }

    /**
     * Checks if the placeholder is pending loading.
     *
     * @return true if state is PENDING
     */
    public boolean isPending() {
        return state == PlaceholderState.PENDING;
    }

    /**
     * Creates a copy of this PlaceholderInfo with updated access time.
     *
     * @return copy with current timestamp as last accessed
     */
    public PlaceholderInfo withUpdatedAccess() {
        PlaceholderInfo copy = new PlaceholderInfo(
            path,
            expectedSize,
            checksum,
            priority,
            created
        );
        copy.state = this.state;
        copy.lastAccessed = System.currentTimeMillis();
        copy.errorMessage = this.errorMessage;
        copy.loadAttempts = this.loadAttempts;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceholderInfo that = (PlaceholderInfo) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return String.format(
            "PlaceholderInfo{path='%s', expectedSize=%d, state=%s, priority=%d, " +
                "created=%d, lastAccessed=%d, attempts=%d}",
            path,
            expectedSize,
            state,
            priority,
            created,
            lastAccessed,
            loadAttempts
        );
    }

    /**
     * Creates a detailed string representation with human-readable timestamps.
     *
     * @return detailed string representation
     */
    public String toDetailedString() {
        long now = System.currentTimeMillis();
        return String.format(
            "PlaceholderInfo{\n" +
                "  path='%s'\n" +
                "  expectedSize=%d bytes\n" +
                "  checksum='%s'\n" +
                "  state=%s\n" +
                "  priority=%d\n" +
                "  age=%d ms\n" +
                "  timeSinceLastAccess=%d ms\n" +
                "  loadAttempts=%d\n" +
                "  errorMessage='%s'\n" +
                "}",
            path,
            expectedSize,
            checksum,
            state,
            priority,
            now - created,
            now - lastAccessed,
            loadAttempts,
            errorMessage
        );
    }
}
