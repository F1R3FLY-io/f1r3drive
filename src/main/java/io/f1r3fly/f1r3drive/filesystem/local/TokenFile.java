package io.f1r3fly.f1r3drive.filesystem.local;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSContext;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSFileStat;
import io.f1r3fly.f1r3drive.filesystem.bridge.FSPointer;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.File;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;

public class TokenFile extends AbstractLocalPath implements File {

    final long value;
    private final String tokenInfo;

    public TokenFile(
        BlockchainContext blockchainContext,
        String name,
        TokenDirectory parent,
        long value
    ) {
        super(blockchainContext, name, parent);
        this.value = value;
        this.lastUpdated = System.currentTimeMillis();
        // Token files are empty - information is in filename only
        this.tokenInfo = "";
    }

    @Override
    public int read(FSPointer buffer, long size, long offset) {
        // Token files are empty - return 0 bytes
        return 0;
    }

    @Override
    public int write(FSPointer buffer, long bufSize, long writeOffset) {
        return 0; // Read-only
    }

    @Override
    public void truncate(long offset) {}

    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public void getAttr(FSFileStat stat, FSContext context) {
        stat.setMode(FSFileStat.S_IFREG | 0444); // Read-only file
        stat.setSize(0); // Token files are empty
        stat.setUid(context.getUid());
        stat.setGid(context.getGid());
        stat.setModificationTime(getLastUpdated());
    }

    @Override
    public void rename(String newName, Directory newParent)
        throws OperationNotPermitted {
        if (!this.name.equals(newName)) {
            throw OperationNotPermitted.instance;
        }

        // allow to move TokenFile inside Wallet Directory only
        if (
            !(newParent instanceof LockedWalletDirectory) &&
            !(newParent instanceof UnlockedWalletDirectory)
        ) {
            throw OperationNotPermitted.instance;
        }

        this.parent = newParent;
    }

    @Override
    public TokenDirectory getParent() {
        return (TokenDirectory) super.getParent();
    }

    public long getValue() {
        return value;
    }
}
