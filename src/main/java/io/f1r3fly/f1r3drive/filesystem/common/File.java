package io.f1r3fly.f1r3drive.filesystem.common;

import io.f1r3fly.f1r3drive.filesystem.bridge.FSFileStat;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSContext;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer;

import java.io.IOException;

public interface File extends Path {
    int read(FSPointer buffer, long size, long offset) throws IOException;

    int write(FSPointer buffer, long bufSize, long writeOffset) throws IOException, UnsupportedOperationException;

    void truncate(long offset) throws IOException;

    default long getSize() {
        return 0;
    }

    default void getAttr(FSFileStat stat, FSContext context) {
        stat.setMode(FSFileStat.S_IFREG | 0777);
        stat.setSize(getSize());
        stat.setUid(context.getUid());
        stat.setGid(context.getGid());
        stat.setModificationTime(getLastUpdated());
    }

    void open() throws IOException;

    void close();
}