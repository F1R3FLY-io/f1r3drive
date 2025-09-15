package io.f1r3fly.f1r3drive.fuse.utils;

import java.io.IOException;
import java.nio.file.Path;
import jnr.posix.util.Platform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MountUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MountUtils.class);
    
    /**
     * Perform/force a umount at the provided Path with default retry settings
     * @param mountPoint the mount point to unmount
     * @return true if unmount was successful or path was already unmounted, false otherwise
     */
    public static boolean umount(Path mountPoint) {
        return umount(mountPoint, 3, 500);
    }
    
    /**
     * Perform/force a umount at the provided Path with configurable retry automation
     * @param mountPoint the mount point to unmount
     * @param maxRetries maximum number of retry attempts for resource busy situations
     * @param retryDelayMs delay in milliseconds between retries (will use exponential backoff)
     * @return true if unmount was successful or path was already unmounted, false otherwise
     */
    public static boolean umount(Path mountPoint, int maxRetries, long retryDelayMs) {
        String mountPath = mountPoint.toAbsolutePath().toString();
        LOGGER.debug("Attempting to unmount: {}", mountPath);
        
        try {
            if (!isMounted(mountPath)) {
                LOGGER.debug("Mount point {} is already unmounted", mountPath);
                return true; // Already unmounted - success
            }
            
            if (Platform.IS_MAC) {
                return unmountMacOSWithRetry(mountPath, maxRetries, retryDelayMs);
            } else if (Platform.IS_WINDOWS) {
                // Best-effort on Windows; primary flow uses fuse_exit elsewhere
                LOGGER.debug("Using Windows mountvol for: {}", mountPath);
                int rc = new ProcessBuilder("mountvol", mountPath, "/p").start().waitFor();
                LOGGER.debug("Windows mountvol completed with exit code: {}", rc);
                return true; // Windows unmount is best-effort, consider it successful
            } else {
                // Linux: prefer fusermount -u, then fallback to umount
                LOGGER.debug("Using Linux fusermount for: {}", mountPath);
                int rc = new ProcessBuilder("fusermount", "-u", mountPath).start().waitFor();
                if (rc != 0) {
                    LOGGER.debug("fusermount failed with exit code {}, checking if still mounted", rc);
                    if (!isMounted(mountPath)) {
                        LOGGER.debug("Mount point {} was successfully unmounted despite error code", mountPath);
                        return true;
                    }
                    LOGGER.debug("Trying umount as fallback for: {}", mountPath);
                    rc = new ProcessBuilder("umount", mountPath).start().waitFor();
                    if (rc != 0 && isMounted(mountPath)) {
                        LOGGER.warn("Failed to unmount {} with both fusermount and umount, exit code: {}", mountPath, rc);
                        return false; // Failed to unmount
                    }
                }
                LOGGER.debug("Successfully unmounted: {}", mountPath);
                return true;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while unmounting {}", mountPath, ie);
            return false;
        } catch (IOException ioe) {
            LOGGER.warn("IO error while unmounting {}", mountPath, ioe);
            return false;
        }
    }

    /**
     * Unmount a macOS FUSE filesystem with intelligent retry and automation
     * @param mountPath the mount point to unmount
     * @param maxRetries maximum number of retry attempts
     * @param retryDelayMs delay in milliseconds between retries
     * @return true if unmount was successful, false otherwise
     */
    private static boolean unmountMacOSWithRetry(String mountPath, int maxRetries, long retryDelayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            LOGGER.debug("Unmount attempt {} of {} for: {}", attempt, maxRetries, mountPath);
            
            // Step 1: Try regular umount
            LOGGER.debug("Using macOS umount for: {}", mountPath);
            String umountError = executeCommandWithOutput("umount", mountPath);
            if (umountError == null) {
                LOGGER.debug("Successfully unmounted {} with umount on attempt {}", mountPath, attempt);
                return true;
            }
            
            // Check if already unmounted despite error
            if (!isMounted(mountPath)) {
                LOGGER.debug("Mount point {} was successfully unmounted despite error: {}", mountPath, umountError);
                return true;
            }
            
            LOGGER.debug("umount failed with error: {}", umountError);
            
            // Step 2: Check if this is a resource busy error and handle accordingly
            boolean isResourceBusy = isResourceBusyError(umountError);
            if (isResourceBusy) {
                LOGGER.debug("Detected 'Resource busy' error, trying automated cleanup");
                
                // Optional: Try to identify what's using the mount (for logging)
                logProcessesUsingMount(mountPath);
                
                // Add a small delay for resource busy situations
                if (attempt < maxRetries) {
                    LOGGER.debug("Waiting {}ms before retry due to resource busy", retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Interrupted while waiting for retry", ie);
                        return false;
                    }
                }
            }
            
            // Step 3: Try diskutil unmount (non-forced)
            LOGGER.debug("Trying diskutil unmount as fallback for: {}", mountPath);
            String diskutilError = executeCommandWithOutput("diskutil", "unmount", mountPath);
            if (diskutilError == null) {
                LOGGER.debug("Successfully unmounted {} with diskutil on attempt {}", mountPath, attempt);
                return true;
            }
            
            if (!isMounted(mountPath)) {
                LOGGER.debug("Mount point {} was successfully unmounted with diskutil despite error: {}", mountPath, diskutilError);
                return true;
            }
            
            // Step 4: For resource busy or final attempt, try force unmount
            if (isResourceBusy || attempt == maxRetries) {
                LOGGER.debug("Trying diskutil unmount force for: {}", mountPath);
                String forceError = executeCommandWithOutput("diskutil", "unmount", "force", mountPath);
                if (forceError == null) {
                    LOGGER.debug("Successfully unmounted {} with force on attempt {}", mountPath, attempt);
                    return true;
                }
                
                if (!isMounted(mountPath)) {
                    LOGGER.debug("Mount point {} was successfully unmounted with force despite error: {}", mountPath, forceError);
                    return true;
                }
                
                if (attempt == maxRetries) {
                    LOGGER.warn("Failed to unmount {} after {} attempts. Errors - umount: {}, diskutil: {}, force: {}", 
                              mountPath, maxRetries, umountError, diskutilError, forceError);
                    return false;
                }
            }
            
            // Exponential backoff for subsequent retries
            retryDelayMs = Math.min(retryDelayMs * 2, 5000); // Cap at 5 seconds
        }
        
        LOGGER.warn("Failed to unmount {} after {} attempts", mountPath, maxRetries);
        return false;
    }
    
    /**
     * Check if the error message indicates a "Resource busy" situation
     * @param errorMessage the error message from unmount command
     * @return true if this is a resource busy error
     */
    private static boolean isResourceBusyError(String errorMessage) {
        if (errorMessage == null) return false;
        String lowerError = errorMessage.toLowerCase();
        return lowerError.contains("resource busy") || 
               lowerError.contains("device busy") ||
               lowerError.contains("target is busy") ||
               lowerError.contains("busy");
    }
    
    /**
     * Log processes that might be using the mount point (for debugging)
     * @param mountPath the mount point to check
     */
    private static void logProcessesUsingMount(String mountPath) {
        try {
            String lsofOutput = executeCommandWithOutput("lsof", "+D", mountPath);
            if (lsofOutput != null && !lsofOutput.trim().isEmpty()) {
                LOGGER.debug("Processes using {}: {}", mountPath, lsofOutput);
            } else {
                LOGGER.debug("No processes found using {} (or lsof failed)", mountPath);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check processes using mount point", e);
        }
    }

    /**
     * Execute a command and return error message if it fails, null if success
     * @param command the command and arguments to execute
     * @return error message if command failed, null if successful
     */
    private static String executeCommandWithOutput(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merge stderr into stdout
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = output.length() > 0 ? output.toString() : "Exit code: " + exitCode;
                return errorMsg;
            }
            return null; // Success
        } catch (Exception e) {
            return "Exception: " + e.getMessage();
        }
    }

    private static boolean isMounted(String mountPath) {
        Process p = null;
        try {
            p = new ProcessBuilder("mount").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String needle = " on " + mountPath;
                while ((line = br.readLine()) != null) {
                    if (line.contains(needle)) {
                        // Additional check: make sure it's followed by space or ( to avoid partial matches
                        int index = line.indexOf(needle);
                        if (index >= 0) {
                            int endIndex = index + needle.length();
                            if (endIndex >= line.length() || 
                                line.charAt(endIndex) == ' ' || 
                                line.charAt(endIndex) == '(' ||
                                line.charAt(endIndex) == '\t') {
                                LOGGER.debug("Found mount: {}", line.trim());
                                return true;
                            }
                        }
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            LOGGER.debug("Error checking if mounted, assuming mounted for safety", e);
            // If detection fails, assume mounted to keep original behavior conservative
            return true;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        LOGGER.debug("Mount point {} not found in mount output", mountPath);
        return false;
    }
}
