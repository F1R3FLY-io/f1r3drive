package io.f1r3fly.f1r3drive.errors;

public class OutOfDeployRetriesError extends F1r3DriveError {
    public OutOfDeployRetriesError(String deployId) {
        super("Out of retries for deploy " + deployId);
    }
}
