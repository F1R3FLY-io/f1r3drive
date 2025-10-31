package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/**
 * Test wrapper to access protected FuseFileInfo constructor
 */
public class TestFuseFileInfo extends FuseFileInfo {
    public TestFuseFileInfo(Runtime runtime) {
        super(runtime);
    }
}

