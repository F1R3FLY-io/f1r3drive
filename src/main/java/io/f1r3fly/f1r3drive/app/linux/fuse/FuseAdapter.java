package io.f1r3fly.f1r3drive.app.linux.fuse;

import io.f1r3fly.f1r3drive.filesystem.bridge.*;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseContext;
import ru.serce.jnrfuse.struct.Statvfs;

/**
 * Adapter class that converts between FUSE types and filesystem wrapper types.
 * This isolates the filesystem layer from FUSE-specific implementations.
 */
public class FuseAdapter {

    /**
     * Convert a FUSE Pointer to FSPointer wrapper
     */
    public static FSPointer fromFusePointer(Pointer fusePointer) {
        return new FSPointer() {
            @Override
            public void put(long offset, byte[] bytes, int start, int length) {
                fusePointer.put(offset, bytes, start, length);
            }

            @Override
            public void get(long offset, byte[] bytes, int start, int length) {
                fusePointer.get(offset, bytes, start, length);
            }

            @Override
            public byte getByte(long offset) {
                return fusePointer.getByte(offset);
            }

            @Override
            public void putByte(long offset, byte value) {
                fusePointer.putByte(offset, value);
            }
        };
    }

    /**
     * Convert a FUSE FileStat to FSFileStat wrapper
     */
    public static FSFileStat fromFuseFileStat(FileStat fuseStat) {
        return new FSFileStat() {
            @Override
            public void setMode(int mode) {
                fuseStat.st_mode.set(mode);
            }

            @Override
            public void setSize(long size) {
                fuseStat.st_size.set(size);
            }

            @Override
            public void setUid(long uid) {
                fuseStat.st_uid.set(uid);
            }

            @Override
            public void setGid(long gid) {
                fuseStat.st_gid.set(gid);
            }

            @Override
            public void setModificationTime(long seconds) {
                fuseStat.st_mtim.tv_sec.set(seconds);
            }
        };
    }

    /**
     * Convert a FUSE FuseContext to FSContext wrapper
     */
    public static FSContext fromFuseContext(FuseContext fuseContext) {
        return new FSContext() {
            @Override
            public long getUid() {
                return fuseContext.uid.get();
            }

            @Override
            public long getGid() {
                return fuseContext.gid.get();
            }
        };
    }

    /**
     * Convert a FUSE Statvfs to FSStatVfs wrapper
     */
    public static FSStatVfs fromFuseStatVfs(Statvfs fuseStatvfs) {
        return new FSStatVfs() {
            @Override
            public void setBlockSize(long size) {
                fuseStatvfs.f_bsize.set(size);
            }

            @Override
            public void setFragmentSize(long size) {
                fuseStatvfs.f_frsize.set(size);
            }

            @Override
            public void setBlocks(long count) {
                fuseStatvfs.f_blocks.set(count);
            }

            @Override
            public void setBlocksAvailable(long count) {
                fuseStatvfs.f_bavail.set(count);
            }

            @Override
            public void setBlocksFree(long count) {
                fuseStatvfs.f_bfree.set(count);
            }

            @Override
            public void setMaxFilenameLength(long length) {
                fuseStatvfs.f_namemax.set(length);
            }
        };
    }

    /**
     * Convert a FUSE FuseFillDir callback to FSFillDir wrapper
     */
    public static FSFillDir fromFuseFillDir(Pointer buf, FuseFillDir fuseFiller) {
        return new FSFillDir() {
            @Override
            public int apply(String name, FSFileStat stat, long offset) {
                // FUSE FillDir doesn't use stat parameter (it's always null in practice)
                return fuseFiller.apply(buf, name, null, offset);
            }
        };
    }
}

