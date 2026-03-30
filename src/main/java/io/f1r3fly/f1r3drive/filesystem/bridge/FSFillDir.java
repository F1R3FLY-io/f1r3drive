package io.f1r3fly.f1r3drive.filesystem.bridge;

/**
 * Abstraction for directory listing callback.
 * Isolates filesystem code from FUSE-specific fill directory functions.
 */
@FunctionalInterface
public interface FSFillDir {
    /**
     * Add a directory entry to the listing
     * @param name Entry name
     * @param stat File attributes (can be null)
     * @param offset Offset for the next entry
     * @return 0 on success, non-zero on error
     */
    int apply(String name, FSFileStat stat, long offset);
}

