package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.Flock;

/**
 * Test wrapper to access protected Flock constructor
 */
public class TestFlock extends Flock {
    public TestFlock(Runtime runtime) {
        super(runtime);
    }
}

