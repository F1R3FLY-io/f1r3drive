package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.Timespec;

/**
 * Test wrapper to access protected Timespec constructor
 */
public class TestTimespec extends Timespec {
    public TestTimespec(Runtime runtime) {
        super(runtime);
    }
}

