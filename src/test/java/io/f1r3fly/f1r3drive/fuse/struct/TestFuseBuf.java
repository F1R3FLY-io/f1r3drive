package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.FuseBuf;

/**
 * Test wrapper to access protected FuseBuf constructor
 */
public class TestFuseBuf extends FuseBuf {
    public TestFuseBuf(Runtime runtime) {
        super(runtime);
    }
}

