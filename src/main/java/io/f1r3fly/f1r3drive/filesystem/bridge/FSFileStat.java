package io.f1r3fly.f1r3drive.filesystem.bridge;

/**
 * Abstraction for file/directory attributes.
 * Isolates filesystem code from FUSE-specific stat structures.
 */
public interface FSFileStat {
    // Mode constants
    int S_IFDIR = 0040000;  // Directory
    int S_IFREG = 0100000;  // Regular file
    
    /**
     * Set the file mode (type and permissions)
     * @param mode File mode bits (type | permissions)
     */
    void setMode(int mode);
    
    /**
     * Set the file size in bytes
     * @param size Size in bytes
     */
    void setSize(long size);
    
    /**
     * Set the user ID of the file owner
     * @param uid User ID
     */
    void setUid(long uid);
    
    /**
     * Set the group ID of the file owner
     * @param gid Group ID
     */
    void setGid(long gid);
    
    /**
     * Set the modification time
     * @param seconds Seconds since epoch
     */
    void setModificationTime(long seconds);
}

