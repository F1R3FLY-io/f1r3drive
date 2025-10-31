package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.FusePollhandle;

/**
 * Test wrapper to access protected FusePollhandle constructor
 */
public class TestFusePollhandle extends FusePollhandle {
    public TestFusePollhandle(Runtime runtime) {
        super(runtime);
    }
}

