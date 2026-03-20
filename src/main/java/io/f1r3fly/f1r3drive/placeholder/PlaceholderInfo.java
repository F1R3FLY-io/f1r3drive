package io.f1r3fly.f1r3drive.placeholder;

import java.util.Objects;

/**
 * Unified Placeholder metadata container.
 */
public class PlaceholderInfo {
    private final String path;
    private final long expectedSize;
    private final long created;
    private final boolean isDirectory;
    private final int materializationPolicy;
    private final int priority;
    private final String checksum;

    private volatile PlaceholderState state;
    private volatile long lastAccessed;
    public volatile boolean isMaterialized;

    // Fully compatible constructor (7 args)
    public PlaceholderInfo(String path, long expectedSize, long created, boolean isDirectory, int materializationPolicy, int priority, String checksum) {
        this.path = path;
        this.expectedSize = expectedSize;
        this.created = created;
        this.isDirectory = isDirectory;
        this.materializationPolicy = materializationPolicy;
        this.priority = priority;
        this.checksum = checksum;
        this.state = PlaceholderState.PENDING;
        this.lastAccessed = created;
        this.isMaterialized = false;
    }

    // Overload for 5 args (FileProviderIntegration)
    public PlaceholderInfo(String path, long expectedSize, long lastModified, boolean isDirectory, int materializationPolicy) {
        this(path, expectedSize, lastModified, isDirectory, materializationPolicy, 1, null);
    }

    // Overload for 6 args
    public PlaceholderInfo(String path, long expectedSize, long lastModified, boolean isDirectory, int materializationPolicy, int priority) {
        this(path, expectedSize, lastModified, isDirectory, materializationPolicy, priority, null);
    }

    // Overload for PlaceholderManager (5 args with checksum)
    public PlaceholderInfo(String path, long expectedSize, String checksum, int priority, long created) {
        this(path, expectedSize, created, false, 0, priority, checksum);
    }

    // Getters
    public String getPath() { return path; }
    public long getExpectedSize() { return expectedSize; }
    public long getCreated() { return created; }
    public boolean isDirectory() { return isDirectory; }
    public int getMaterializationPolicy() { return materializationPolicy; }
    public int getPriority() { return priority; }
    public String getChecksum() { return checksum; }
    public PlaceholderState getState() { return state; }
    public long getLastAccessed() { return lastAccessed; }

    // Setters
    public void setState(PlaceholderState state) { 
        this.state = state; 
        this.isMaterialized = (state == PlaceholderState.LOADED);
    }
    public void setLastAccessed(long lastAccessed) { this.lastAccessed = lastAccessed; }

    public boolean isLoaded() { return state == PlaceholderState.LOADED || isMaterialized; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(path, ((PlaceholderInfo) o).path);
    }

    @Override
    public int hashCode() { return Objects.hash(path); }
}
