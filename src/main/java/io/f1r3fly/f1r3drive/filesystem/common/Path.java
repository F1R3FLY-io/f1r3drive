package io.f1r3fly.f1r3drive.filesystem.common;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSFileStat;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSContext;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public interface Path {
    void getAttr(FSFileStat stat, FSContext context);

    // Helper method to get path separator
    default String separator() {
        return PathUtils.getPathDelimiterBasedOnOS();
    }

    // Helper method to normalize path by removing leading separators
    default String normalizePath(String path) {
        while (path.startsWith(separator())) {
            path = path.substring(separator().length());
        }
        return path;
    }

    // Simplified find method - only handles current path matching
    default Path find(String path) {
        path = normalizePath(path);
        if (path.equals(getName()) || path.isEmpty()) {
            return this;
        }
        return null;
    }

    @NotNull String getName();

    @NotNull String getAbsolutePath();

    @NotNull Long getLastUpdated();

    @Nullable
    Directory getParent();

    void delete() throws OperationNotPermitted;

    void rename(String newName, Directory newParent) throws OperationNotPermitted;

    default void cleanLocalCache() {};

    BlockchainContext getBlockchainContext();
}
