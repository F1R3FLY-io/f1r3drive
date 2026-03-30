package io.f1r3fly.f1r3drive.folders;

import java.util.Objects;

/**
 * Represents a folder token from blockchain
 * Contains all necessary information about the folder and its token
 */
public class FolderToken {

    private final String folderName;
    private final String folderPath;
    private final String ownerAddress;
    private final String tokenData;
    private final long createdTimestamp;
    private volatile long lastAccessTimestamp;
    private volatile boolean isLocked;

    public FolderToken(
        String folderName,
        String folderPath,
        String ownerAddress,
        String tokenData,
        long createdTimestamp
    ) {
        this.folderName = Objects.requireNonNull(
            folderName,
            "folderName cannot be null"
        );
        this.folderPath = Objects.requireNonNull(
            folderPath,
            "folderPath cannot be null"
        );
        this.ownerAddress = Objects.requireNonNull(
            ownerAddress,
            "ownerAddress cannot be null"
        );
        this.tokenData = Objects.requireNonNull(
            tokenData,
            "tokenData cannot be null"
        );
        this.createdTimestamp = createdTimestamp;
        this.lastAccessTimestamp = createdTimestamp;
        this.isLocked = false;
    }

    /**
     * Gets the folder name
     */
    public String getFolderName() {
        return folderName;
    }

    /**
     * Gets the full path to the folder in the filesystem
     */
    public String getFolderPath() {
        return folderPath;
    }

    /**
     * Gets the owner address of the token
     */
    public String getOwnerAddress() {
        return ownerAddress;
    }

    /**
     * Gets token data from blockchain
     */
    public String getTokenData() {
        return tokenData;
    }

    /**
     * Gets the timestamp when token was created
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Gets the timestamp of last access
     */
    public long getLastAccessTimestamp() {
        return lastAccessTimestamp;
    }

    /**
     * Updates the last access timestamp
     */
    public void updateLastAccess() {
        this.lastAccessTimestamp = System.currentTimeMillis();
    }

    /**
     * Checks if the token is locked
     */
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * Locks the token (e.g., during operations)
     */
    public void lock() {
        this.isLocked = true;
    }

    /**
     * Unlocks the token
     */
    public void unlock() {
        this.isLocked = false;
    }

    /**
     * Checks if the token belongs to the specified address
     */
    public boolean isOwnedBy(String address) {
        return ownerAddress.equals(address);
    }

    /**
     * Calculates the age of the token in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTimestamp;
    }

    /**
     * Calculates time since last access in milliseconds
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessTimestamp;
    }

    /**
     * Checks if the token is stale (not used for more than specified time)
     */
    public boolean isStale(long maxIdleTimeMs) {
        return getTimeSinceLastAccess() > maxIdleTimeMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderToken that = (FolderToken) o;
        return (
            createdTimestamp == that.createdTimestamp &&
            Objects.equals(folderName, that.folderName) &&
            Objects.equals(folderPath, that.folderPath) &&
            Objects.equals(ownerAddress, that.ownerAddress)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            folderName,
            folderPath,
            ownerAddress,
            createdTimestamp
        );
    }

    @Override
    public String toString() {
        return (
            "FolderToken{" +
            "folderName='" +
            folderName +
            '\'' +
            ", folderPath='" +
            folderPath +
            '\'' +
            ", ownerAddress='" +
            ownerAddress +
            '\'' +
            ", createdTimestamp=" +
            createdTimestamp +
            ", lastAccessTimestamp=" +
            lastAccessTimestamp +
            ", isLocked=" +
            isLocked +
            '}'
        );
    }

    /**
     * Creates a short representation for logging
     */
    public String toShortString() {
        return String.format(
            "FolderToken[%s@%s]",
            folderName,
            ownerAddress.substring(0, Math.min(8, ownerAddress.length()))
        );
    }
}
