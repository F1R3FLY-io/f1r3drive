package io.f1r3fly.f1r3drive.errors;

public class AnotherProposalInProgressError extends F1r3DriveError {

    public AnotherProposalInProgressError(Throwable cause) {
        super("Another proposal is in progress", cause);
    }
}
