package io.f1r3fly.f1r3drive.filesystem.bridge;

/**
 * Abstraction for filesystem statistics.
 * Isolates filesystem code from FUSE-specific statvfs structures.
 */
public interface FSStatVfs {
    /**
     * Set the filesystem block size
     * @param size Block size in bytes
     */
    void setBlockSize(long size);
    
    /**
     * Set the fragment size
     * @param size Fragment size in bytes
     */
    void setFragmentSize(long size);
    
    /**
     * Set the total number of blocks
     * @param count Total blocks
     */
    void setBlocks(long count);
    
    /**
     * Set the number of available blocks (for unprivileged users)
     * @param count Available blocks
     */
    void setBlocksAvailable(long count);
    
    /**
     * Set the number of free blocks
     * @param count Free blocks
     */
    void setBlocksFree(long count);
    
    /**
     * Set the maximum filename length
     * @param length Maximum length
     */
    void setMaxFilenameLength(long length);
}

