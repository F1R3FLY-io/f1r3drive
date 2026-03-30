package io.f1r3fly.f1r3drive.filesystem;

import io.f1r3fly.f1r3drive.errors.*;
import io.f1r3fly.f1r3drive.filesystem.bridge.*;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;

import javax.annotation.Nullable;
import java.io.IOException;

public interface FileSystem {

    File getFile(String path);
    Directory getDirectory(String path);

    boolean isRootPath(String path);

    @Nullable
    String getParentPath(String path);

    void createFile(String path, long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted;

    void getAttributes(String path, FSFileStat stat, FSContext context) throws PathNotFound;

    void makeDirectory(String path, long mode) throws PathNotFound, FileAlreadyExists, OperationNotPermitted;

    int readFile(String path, FSPointer buf, long size, long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void readDirectory(String path, FSFillDir filter) throws PathNotFound, PathIsNotADirectory;

    void getFileSystemStats(String path, FSStatVfs stbuf);

    void renameFile(String path, String newName) throws PathNotFound, OperationNotPermitted;

    void removeDirectory(String path) throws PathNotFound, PathIsNotADirectory, DirectoryNotEmpty, OperationNotPermitted;

    void truncateFile(String path, long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void unlinkFile(String path) throws PathNotFound, OperationNotPermitted;

    void openFile(String path) throws PathNotFound, PathIsNotAFile, IOException;

    int writeFile(String path, FSPointer buf, long size, long offset) throws PathNotFound, PathIsNotAFile, IOException;

    void flushFile(String path) throws PathNotFound, PathIsNotAFile;

    void unlockRootDirectory(String revAddress, String privateKey);
    void changeTokenFile(String tokenFilePath) throws NoDataByPath;

    // utils
    // TODO: hide it?
    void terminate();
    void waitOnBackgroundDeploy();

}
