package io.f1r3fly.f1r3drive.filesystem;

import io.f1r3fly.f1r3drive.errors.*;
import ru.serce.jnrfuse.FuseFillDir;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseContext;
import ru.serce.jnrfuse.struct.Statvfs;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;

import javax.annotation.Nullable;
import java.io.IOException;

public interface FileSystem {

    File getFile(String path);
    Directory getDirectory(String path);

    boolean isRootPath(String path);

    @Nullable
    String getParentPath(String path);

    void createFile(String path, @mode_t long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted;

    void getAttributes(String path, FileStat stat, FuseContext fuseContext) throws PathNotFound;

    void makeDirectory(String path, @mode_t long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted;

    int readFile(String path, Pointer buf, @size_t long size, @off_t long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void readDirectory(String path, Pointer buf, FuseFillDir filter) throws PathNotFound, PathIsNotADirectory;

    void getFileSystemStats(String path, Statvfs stbuf);

    void renameFile(String path, String newName) throws PathNotFound, OperationNotPermitted;

    void removeDirectory(String path) throws PathNotFound, PathIsNotADirectory, DirectoryNotEmpty, OperationNotPermitted;

    void truncateFile(String path, long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void unlinkFile(String path) throws PathNotFound, OperationNotPermitted;

    void openFile(String path) throws PathNotFound, PathIsNotAFile, IOException;

    int writeFile(String path, Pointer buf, @size_t long size, @off_t long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void flushFile(String path) throws PathNotFound, PathIsNotAFile;

    void unlockRootDirectory(String revAddress, String privateKey);
    void changeTokenFile(String tokenFilePath) throws NoDataByPath;

    // utils
    // TODO: hide it?
    void terminate();
    void waitOnBackgroundDeploy();

}
