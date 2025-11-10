package io.f1r3fly.f1r3drive.platform;

/**
 * Callback interface for on-demand file loading from blockchain.
 * Supports lazy loading and content caching management.
 */
public interface FileChangeCallback {

    /**
     * Called when a file needs to be loaded from the blockchain on-demand.
     * This is typically triggered when a placeholder file is accessed.
     *
     * @param path the path of the file to load
     * @return the file content as byte array, or null if file cannot be loaded
     */
    byte[] loadFileContent(String path);

    /**
     * Called to check if a file exists in the blockchain.
     *
     * @param path the path to check
     * @return true if the file exists in blockchain, false otherwise
     */
    boolean fileExistsInBlockchain(String path);

    /**
     * Called to get metadata about a file in the blockchain.
     *
     * @param path the path of the file
     * @return file metadata or null if file doesn't exist
     */
    FileMetadata getFileMetadata(String path);

    /**
     * Called when a file is saved to the blockchain.
     * This allows the callback to update internal caches or state.
     *
     * @param path the path of the saved file
     * @param content the content that was saved
     */
    void onFileSavedToBlockchain(String path, byte[] content);

    /**
     * Called when a file is deleted from the blockchain.
     *
     * @param path the path of the deleted file
     */
    void onFileDeletedFromBlockchain(String path);

    /**
     * Called to preload a file into cache without immediate access.
     * Used for performance optimization.
     *
     * @param path the path of the file to preload
     * @param priority the loading priority (higher values = higher priority)
     */
    void preloadFile(String path, int priority);

    /**
     * Called to clear cached content for a specific file.
     *
     * @param path the path of the file to clear from cache
     */
    void clearCache(String path);

    /**
     * Called to get cache statistics for monitoring and optimization.
     *
     * @return cache statistics
     */
    CacheStatistics getCacheStatistics();

    /**
     * Inner class for file metadata.
     */
    class FileMetadata {
        private final long size;
        private final long lastModified;
        private final String checksum;
        private final boolean isDirectory;

        public FileMetadata(long size, long lastModified, String checksum, boolean isDirectory) {
            this.size = size;
            this.lastModified = lastModified;
            this.checksum = checksum;
            this.isDirectory = isDirectory;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }

        public String getChecksum() {
            return checksum;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }

    /**
     * Inner class for cache statistics.
     */
    class CacheStatistics {
        private final long cacheHits;
        private final long cacheMisses;
        private final long cacheSize;
        private final long maxCacheSize;
        private final int cachedFilesCount;

        public CacheStatistics(long cacheHits, long cacheMisses, long cacheSize,
                              long maxCacheSize, int cachedFilesCount) {
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheSize = cacheSize;
            this.maxCacheSize = maxCacheSize;
            this.cachedFilesCount = cachedFilesCount;
        }

        public long getCacheHits() {
            return cacheHits;
        }

        public long getCacheMisses() {
            return cacheMisses;
        }

        public long getCacheSize() {
            return cacheSize;
        }

        public long getMaxCacheSize() {
            return maxCacheSize;
        }

        public int getCachedFilesCount() {
            return cachedFilesCount;
        }

        public double getHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
    }
}
