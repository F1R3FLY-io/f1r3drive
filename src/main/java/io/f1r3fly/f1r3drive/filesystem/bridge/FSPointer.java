package io.f1r3fly.f1r3drive.filesystem.bridge;

/**
 * Abstraction for pointer/buffer operations.
 * Isolates filesystem code from FUSE-specific buffer types.
 */
public interface FSPointer {
    /**
     * Write bytes to the buffer at the specified offset
     * @param offset Offset in the buffer
     * @param bytes Source byte array
     * @param start Starting position in source array
     * @param length Number of bytes to write
     */
    void put(long offset, byte[] bytes, int start, int length);
    
    /**
     * Read bytes from the buffer at the specified offset
     * @param offset Offset in the buffer
     * @param bytes Destination byte array
     * @param start Starting position in destination array
     * @param length Number of bytes to read
     */
    void get(long offset, byte[] bytes, int start, int length);
    
    /**
     * Read a single byte at the specified offset
     * @param offset Offset in the buffer
     * @return The byte value
     */
    byte getByte(long offset);
    
    /**
     * Write a single byte at the specified offset
     * @param offset Offset in the buffer
     * @param value The byte value to write
     */
    void putByte(long offset, byte value);
}

