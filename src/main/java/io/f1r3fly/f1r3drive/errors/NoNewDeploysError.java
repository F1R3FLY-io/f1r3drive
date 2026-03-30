package io.f1r3fly.f1r3drive.errors;

public class NoNewDeploysError extends F1r3DriveError {

    public final String deployId;

    public NoNewDeploysError(String message, String deployId) {
        super("No new deploys found. Error: %s".formatted(message));
        this.deployId = deployId;
    }

    public NoNewDeploysError(String message, String deployId, Throwable cause) {
        super("No new deploys found. Error: %s".formatted(message), cause);
        this.deployId = deployId;
    }
}
