package io.f1r3fly.f1r3drive.folders;

import java.io.IOException;

/**
 * Interface defining standard operations that can be performed on physical wallet folders.
 * This interface provides a contract for both locked and unlocked wallet implementations,
 * where locked wallets will throw exceptions for write operations and unlocked wallets
 * will provide full access.
 */
public interface WalletOperations {

    // File Operations

    /**
     * Creates a new file with the specified content
     * @param relativePath Path relative to wallet root
     * @param content File content as byte array
     * @throws IOException If file creation fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void createFile(String relativePath, byte[] content) throws IOException, IllegalStateException;

    /**
     * Writes content to an existing file
     * @param relativePath Path relative to wallet root
     * @param content New file content as byte array
     * @throws IOException If file write fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void writeFile(String relativePath, byte[] content) throws IOException, IllegalStateException;

    /**
     * Reads content from a file (allowed for both locked and unlocked wallets)
     * @param relativePath Path relative to wallet root
     * @return File content as byte array
     * @throws IOException If file read fails
     */
    byte[] readFile(String relativePath) throws IOException;

    /**
     * Deletes a file
     * @param relativePath Path relative to wallet root
     * @throws IOException If file deletion fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void deleteFile(String relativePath) throws IOException, IllegalStateException;

    /**
     * Renames a file
     * @param oldPath Current file path relative to wallet root
     * @param newPath New file path relative to wallet root
     * @throws IOException If file rename fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void renameFile(String oldPath, String newPath) throws IOException, IllegalStateException;

    // Directory Operations

    /**
     * Creates a new directory
     * @param relativePath Path relative to wallet root
     * @throws IOException If directory creation fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void createDirectory(String relativePath) throws IOException, IllegalStateException;

    /**
     * Deletes a directory and all its contents
     * @param relativePath Path relative to wallet root
     * @throws IOException If directory deletion fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void deleteDirectory(String relativePath) throws IOException, IllegalStateException;

    /**
     * Lists the contents of a directory (allowed for both locked and unlocked wallets)
     * @param relativePath Path relative to wallet root
     * @return Array of file and directory names
     * @throws IOException If directory listing fails
     */
    String[] listDirectory(String relativePath) throws IOException;

    /**
     * Renames a directory
     * @param oldPath Current directory path relative to wallet root
     * @param newPath New directory path relative to wallet root
     * @throws IOException If directory rename fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void renameDirectory(String oldPath, String newPath) throws IOException, IllegalStateException;

    // File System Queries (Read-only operations allowed for both locked and unlocked)

    /**
     * Checks if a file or directory exists
     * @param relativePath Path relative to wallet root
     * @return true if path exists, false otherwise
     */
    boolean fileExists(String relativePath);

    /**
     * Checks if a path is a directory
     * @param relativePath Path relative to wallet root
     * @return true if path is a directory, false otherwise
     */
    boolean isDirectory(String relativePath);

    // Blockchain and Token Operations

    /**
     * Updates the balance for a specific token
     * @param tokenName Name of the token
     * @param balance New balance value
     * @throws IOException If token file update fails
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void updateTokenBalance(String tokenName, long balance) throws IOException, IllegalStateException;

    /**
     * Executes a blockchain transaction
     * @param transactionData Transaction data to execute
     * @throws IllegalStateException If wallet is locked (for locked wallets)
     */
    void executeBlockchainTransaction(String transactionData) throws IllegalStateException;

    // Wallet State Information

    /**
     * Checks if this wallet is currently locked
     * @return true if wallet is locked, false if unlocked
     */
    boolean isLocked();

    /**
     * Checks if this wallet is currently unlocked
     * @return true if wallet is unlocked, false if locked
     */
    boolean isUnlocked();

    /**
     * Gets the REV address for this wallet
     * @return The wallet's REV address
     */
    String getRevAddress();

    /**
     * Gets the current status of this wallet
     * @return Wallet status (LOCKED/UNLOCKED)
     */
    String getWalletStatus();
}
