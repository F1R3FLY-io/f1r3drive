package io.f1r3fly.f1r3drive.platform.linux;

import io.f1r3fly.f1r3drive.filesystem.FileSystem;
import io.f1r3fly.f1r3drive.platform.ChangeListener;
import io.f1r3fly.f1r3drive.platform.FileChangeCallback;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

/**
 * Simplified FUSE filesystem implementation for Phase 4 testing.
 * This version focuses on placeholder file support and basic FUSE operations
 * without deep integration with the complex filesystem hierarchy.
 */
public class FuseFilesystem extends FuseStubFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        FuseFilesystem.class
    );

    // File type constants from stat.h
    private static final int S_IFREG = 0100000; // Regular file
    private static final int S_IFDIR = 0040000; // Directory

    private final FileSystem fileSystem;
    private final FileChangeCallback fileChangeCallback;
    private final ChangeListener changeListener;
    private final Map<String, PlaceholderInfo> placeholders;
    private final Map<String, VirtualFile> virtualFiles;
    private final AtomicLong nextInode;

    public FuseFilesystem(
        FileSystem fileSystem,
        FileChangeCallback fileChangeCallback,
        ChangeListener changeListener
    ) {
        this.fileSystem = fileSystem;
        this.fileChangeCallback = fileChangeCallback;
        this.changeListener = changeListener;
        this.placeholders = new ConcurrentHashMap<>();
        this.virtualFiles = new ConcurrentHashMap<>();
        this.nextInode = new AtomicLong(2);
    }

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.trace("getattr: {}", path);

        try {
            // Check virtual files first
            VirtualFile virtualFile = virtualFiles.get(path);
            if (virtualFile != null) {
                fillVirtualFileStat(virtualFile, stat);
                return 0;
            }

            // Check placeholder files
            if (isPlaceholderFile(path)) {
                fillPlaceholderStat(path, stat);
                return 0;
            }

            // Check for virtual directories
            if (isVirtualDirectory(path)) {
                fillDirectoryStat(path, stat);
                return 0;
            }

            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            LOGGER.error("Error in getattr for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(
        String path,
        Pointer buf,
        FuseFillDir filter,
        @off_t long offset,
        FuseFileInfo fi
    ) {
        LOGGER.trace("readdir: {}", path);

        try {
            // Add . and .. entries
            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);

            // Add virtual files and placeholders for this directory
            addVirtualEntries(path, buf, filter);
            addPlaceholderEntries(path, buf, filter);

            return 0;
        } catch (Exception e) {
            LOGGER.error("Error in readdir for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.trace("open: {}", path);

        try {
            VirtualFile virtualFile = virtualFiles.get(path);

            if (virtualFile == null && isPlaceholderFile(path)) {
                // Try to load placeholder file
                loadPlaceholderFile(path);
                virtualFile = virtualFiles.get(path);
            }

            if (virtualFile == null) {
                return -ErrorCodes.ENOENT();
            }

            // Notify about file access
            if (changeListener != null) {
                changeListener.onFileAccessed(path);
            }

            return 0;
        } catch (Exception e) {
            LOGGER.error("Error in open for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int read(
        String path,
        Pointer buf,
        @size_t long size,
        @off_t long offset,
        FuseFileInfo fi
    ) {
        LOGGER.trace("read: {} (size={}, offset={})", path, size, offset);

        try {
            VirtualFile virtualFile = virtualFiles.get(path);

            if (virtualFile == null && isPlaceholderFile(path)) {
                loadPlaceholderFile(path);
                virtualFile = virtualFiles.get(path);
            }

            if (virtualFile == null) {
                return -ErrorCodes.ENOENT();
            }

            byte[] content = virtualFile.getContent();
            if (content == null) {
                return 0;
            }

            int fileSize = content.length;
            if (offset >= fileSize) {
                return 0;
            }

            int bytesToRead = (int) Math.min(size, fileSize - offset);
            buf.put(0, content, (int) offset, bytesToRead);

            return bytesToRead;
        } catch (Exception e) {
            LOGGER.error("Error in read for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int write(
        String path,
        Pointer buf,
        @size_t long size,
        @off_t long offset,
        FuseFileInfo fi
    ) {
        LOGGER.trace("write: {} (size={}, offset={})", path, size, offset);

        try {
            VirtualFile virtualFile = virtualFiles.get(path);
            if (virtualFile == null) {
                return -ErrorCodes.ENOENT();
            }

            byte[] existingContent = virtualFile.getContent();
            int existingSize = existingContent != null
                ? existingContent.length
                : 0;

            int newSize = (int) Math.max(existingSize, offset + size);
            byte[] newContent = new byte[newSize];

            if (existingContent != null && existingSize > 0) {
                System.arraycopy(
                    existingContent,
                    0,
                    newContent,
                    0,
                    Math.min(existingSize, newSize)
                );
            }

            byte[] writeData = new byte[(int) size];
            buf.get(0, writeData, 0, (int) size);
            System.arraycopy(
                writeData,
                0,
                newContent,
                (int) offset,
                (int) size
            );

            virtualFile.setContent(newContent);

            if (changeListener != null) {
                changeListener.onFileModified(path);
            }

            return (int) size;
        } catch (Exception e) {
            LOGGER.error("Error in write for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        LOGGER.trace("create: {} (mode={})", path, mode);

        try {
            removePlaceholder(path);

            VirtualFile newFile = new VirtualFile(path, new byte[0]);
            virtualFiles.put(path, newFile);

            if (changeListener != null) {
                changeListener.onFileCreated(path);
            }

            return 0;
        } catch (Exception e) {
            LOGGER.error("Error in create for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int unlink(String path) {
        LOGGER.trace("unlink: {}", path);

        try {
            VirtualFile virtualFile = virtualFiles.remove(path);
            if (virtualFile == null && !isPlaceholderFile(path)) {
                return -ErrorCodes.ENOENT();
            }

            removePlaceholder(path);

            if (changeListener != null) {
                changeListener.onFileDeleted(path);
            }

            return 0;
        } catch (Exception e) {
            LOGGER.error("Error in unlink for path: {}", path, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.trace("statfs: {}", path);

        stbuf.f_bsize.set(4096L);
        stbuf.f_frsize.set(4096L);
        stbuf.f_blocks.set(1000000L);
        stbuf.f_bfree.set(500000L);
        stbuf.f_bavail.set(500000L);
        stbuf.f_files.set(100000L);
        stbuf.f_ffree.set(50000L);
        stbuf.f_namemax.set(255L);

        return 0;
    }

    /**
     * Registers a placeholder file that will be loaded from blockchain on access.
     */
    public void registerPlaceholder(String path, long size, long lastModified) {
        PlaceholderInfo info = new PlaceholderInfo(path, size, lastModified);
        placeholders.put(path, info);
        LOGGER.debug("Registered placeholder file: {} (size: {})", path, size);
    }

    /**
     * Removes a placeholder file registration.
     */
    public void removePlaceholder(String path) {
        PlaceholderInfo removed = placeholders.remove(path);
        if (removed != null) {
            LOGGER.debug("Removed placeholder file: {}", path);
        }
    }

    /**
     * Gets the number of registered placeholder files.
     */
    public int getPlaceholderCount() {
        return placeholders.size();
    }

    private void fillVirtualFileStat(VirtualFile virtualFile, FileStat stat) {
        stat.st_ino.set(nextInode.getAndIncrement());
        stat.st_mode.set(S_IFREG | 0644);
        stat.st_nlink.set(1);
        stat.st_uid.set(getContext().uid.get());
        stat.st_gid.set(getContext().gid.get());

        byte[] content = virtualFile.getContent();
        stat.st_size.set(content != null ? content.length : 0);

        long timestamp = System.currentTimeMillis() / 1000;
        stat.st_atim.tv_sec.set(timestamp);
        stat.st_mtim.tv_sec.set(timestamp);
        stat.st_ctim.tv_sec.set(timestamp);
    }

    private void fillPlaceholderStat(String path, FileStat stat) {
        PlaceholderInfo info = placeholders.get(path);
        if (info == null) {
            return;
        }

        stat.st_ino.set(nextInode.getAndIncrement());
        stat.st_mode.set(S_IFREG | 0644);
        stat.st_nlink.set(1);
        stat.st_uid.set(getContext().uid.get());
        stat.st_gid.set(getContext().gid.get());
        stat.st_size.set(info.getSize());

        long timestamp = info.getLastModified() / 1000;
        stat.st_atim.tv_sec.set(timestamp);
        stat.st_mtim.tv_sec.set(timestamp);
        stat.st_ctim.tv_sec.set(timestamp);
    }

    private void fillDirectoryStat(String path, FileStat stat) {
        stat.st_ino.set(nextInode.getAndIncrement());
        stat.st_mode.set(S_IFDIR | 0755);
        stat.st_nlink.set(2);
        stat.st_uid.set(getContext().uid.get());
        stat.st_gid.set(getContext().gid.get());
        stat.st_size.set(4096L);

        long timestamp = System.currentTimeMillis() / 1000;
        stat.st_atim.tv_sec.set(timestamp);
        stat.st_mtim.tv_sec.set(timestamp);
        stat.st_ctim.tv_sec.set(timestamp);
    }

    private boolean isPlaceholderFile(String path) {
        return placeholders.containsKey(path);
    }

    private boolean isVirtualDirectory(String path) {
        // Consider root and common paths as virtual directories
        return "/".equals(path) || path.startsWith("/");
    }

    private void loadPlaceholderFile(String path) {
        if (fileChangeCallback == null) {
            LOGGER.warn(
                "Cannot load placeholder file - no callback registered: {}",
                path
            );
            return;
        }

        try {
            LOGGER.debug("Loading placeholder file from blockchain: {}", path);
            byte[] content = fileChangeCallback.loadFileContent(path).join();

            if (content != null) {
                VirtualFile virtualFile = new VirtualFile(path, content);
                virtualFiles.put(path, virtualFile);
                removePlaceholder(path);
                LOGGER.debug(
                    "Successfully loaded placeholder file: {} ({} bytes)",
                    path,
                    content.length
                );
            } else {
                LOGGER.warn(
                    "Failed to load placeholder file content: {}",
                    path
                );
            }
        } catch (Exception e) {
            LOGGER.error("Error loading placeholder file: {}", path, e);
        }
    }

    private void addVirtualEntries(
        String dirPath,
        Pointer buf,
        FuseFillDir filter
    ) {
        for (String path : virtualFiles.keySet()) {
            String parentPath = getParentPath(path);
            if (parentPath != null && parentPath.equals(dirPath)) {
                String fileName = getFileName(path);
                if (fileName != null) {
                    filter.apply(buf, fileName, null, 0);
                }
            }
        }
    }

    private void addPlaceholderEntries(
        String dirPath,
        Pointer buf,
        FuseFillDir filter
    ) {
        for (String placeholderPath : placeholders.keySet()) {
            String parentPath = getParentPath(placeholderPath);
            if (parentPath != null && parentPath.equals(dirPath)) {
                String fileName = getFileName(placeholderPath);
                if (fileName != null) {
                    filter.apply(buf, fileName, null, 0);
                }
            }
        }
    }

    private String getParentPath(String path) {
        if (path == null || path.equals("/")) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    private String getFileName(String path) {
        if (path == null || path.equals("/")) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        return path.substring(lastSlash + 1);
    }

    /**
     * Simple virtual file implementation for testing.
     */
    private static class VirtualFile {

        private final String path;
        private byte[] content;

        public VirtualFile(String path, byte[] content) {
            this.path = path;
            this.content = content;
        }

        public String getPath() {
            return path;
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }
    }

    /**
     * Information about a placeholder file.
     */
    private static class PlaceholderInfo {

        private final String path;
        private final long size;
        private final long lastModified;

        public PlaceholderInfo(String path, long size, long lastModified) {
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }
}
