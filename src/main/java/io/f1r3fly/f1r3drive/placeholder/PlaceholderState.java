package io.f1r3fly.f1r3drive.placeholder;

/**
 * Enumeration of possible states for placeholder files.
 * Represents the lifecycle of a placeholder from creation to successful loading or failure.
 */
public enum PlaceholderState {
    /**
     * Placeholder has been created but loading has not started yet.
     * This is the initial state when a placeholder is first registered.
     */
    PENDING("Placeholder created, waiting for loading to start"),

    /**
     * Placeholder content is currently being loaded from blockchain.
     * Loading operation is in progress in background thread.
     */
    LOADING("Content is being loaded from blockchain"),

    /**
     * Placeholder content has been successfully loaded and is available.
     * Content is cached and ready for use.
     */
    LOADED("Content successfully loaded and cached"),

    /**
     * Loading failed due to an error.
     * May be retried based on configuration and error type.
     */
    FAILED("Loading failed due to error"),

    /**
     * Placeholder is being refreshed with updated content.
     * Used when the blockchain version has changed and needs reloading.
     */
    REFRESHING("Content is being refreshed from blockchain"),

    /**
     * Placeholder has been marked for deletion.
     * Will be removed from the system in next cleanup cycle.
     */
    MARKED_FOR_DELETION("Placeholder marked for deletion");

    private final String description;

    /**
     * Creates a placeholder state with description.
     *
     * @param description human-readable description of the state
     */
    PlaceholderState(String description) {
        this.description = description;
    }

    /**
     * Gets the human-readable description of this state.
     *
     * @return state description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this state represents an active loading operation.
     *
     * @return true if currently loading or refreshing
     */
    public boolean isActiveLoading() {
        return this == LOADING || this == REFRESHING;
    }

    /**
     * Checks if this state represents a successful completion.
     *
     * @return true if loaded successfully
     */
    public boolean isSuccessful() {
        return this == LOADED;
    }

    /**
     * Checks if this state represents a failure condition.
     *
     * @return true if failed or marked for deletion
     */
    public boolean isFailure() {
        return this == FAILED || this == MARKED_FOR_DELETION;
    }

    /**
     * Checks if the placeholder can be loaded or reloaded.
     *
     * @return true if loading is possible
     */
    public boolean canLoad() {
        return this == PENDING || this == FAILED;
    }

    /**
     * Checks if the placeholder has content available.
     *
     * @return true if content is available
     */
    public boolean hasContent() {
        return this == LOADED;
    }

    /**
     * Gets the next logical state after a successful loading operation.
     *
     * @return LOADED state
     */
    public static PlaceholderState afterSuccessfulLoad() {
        return LOADED;
    }

    /**
     * Gets the next logical state after a failed loading operation.
     *
     * @return FAILED state
     */
    public static PlaceholderState afterFailedLoad() {
        return FAILED;
    }

    /**
     * Gets the state for starting a loading operation.
     *
     * @param isRefresh true if this is a refresh operation
     * @return LOADING or REFRESHING state
     */
    public static PlaceholderState startLoading(boolean isRefresh) {
        return isRefresh ? REFRESHING : LOADING;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", name(), description);
    }
}
