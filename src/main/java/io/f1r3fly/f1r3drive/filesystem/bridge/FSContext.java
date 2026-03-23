package io.f1r3fly.f1r3drive.filesystem.bridge;

/**
 * Abstraction for filesystem operation context.
 * Isolates filesystem code from FUSE-specific context structures.
 */
public interface FSContext {
    /**
     * Get the user ID of the calling process
     * @return User ID
     */
    long getUid();
    
    /**
     * Get the group ID of the calling process
     * @return Group ID
     */
    long getGid();
}

