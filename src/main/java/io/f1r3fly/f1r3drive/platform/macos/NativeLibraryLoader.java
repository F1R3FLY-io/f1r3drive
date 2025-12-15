package io.f1r3fly.f1r3drive.platform.macos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading native libraries from JAR resources.
 * This class handles extracting native libraries from the JAR file to a temporary location
 * and loading them using System.load().
 */
public class NativeLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        NativeLibraryLoader.class
    );

    private static final String TEMP_DIR_PREFIX = "f1r3drive-native-";
    private static volatile boolean isLoaded = false;
    private static volatile String loadedLibraryPath = null;

    private NativeLibraryLoader() {
        // Utility class - no instantiation
    }

    /**
     * Loads the FSEvents native library.
     * This method is thread-safe and will only load the library once.
     *
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    public static synchronized void loadFSEventsLibrary()
        throws UnsatisfiedLinkError {
        if (isLoaded) {
            LOGGER.debug(
                "FSEvents library already loaded from: {}",
                loadedLibraryPath
            );
            return;
        }

        String libraryName = "libf1r3drive-fsevents.dylib";
        String resourcePath = "/" + libraryName;

        try {
            // First try to load from system library path
            try {
                System.loadLibrary("f1r3drive-fsevents");
                isLoaded = true;
                loadedLibraryPath = "system library path";
                LOGGER.info("Successfully loaded FSEvents library from system");
                return;
            } catch (UnsatisfiedLinkError e) {
                LOGGER.debug(
                    "Failed to load from system library path, trying JAR extraction: {}",
                    e.getMessage()
                );
            }

            // Extract and load from JAR resources
            String extractedPath = extractLibraryFromJar(
                resourcePath,
                libraryName
            );
            System.load(extractedPath);

            isLoaded = true;
            loadedLibraryPath = extractedPath;
            LOGGER.info(
                "Successfully loaded FSEvents library from JAR: {}",
                extractedPath
            );
        } catch (Exception e) {
            String errorMsg =
                "Failed to load FSEvents native library: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new UnsatisfiedLinkError(errorMsg);
        }
    }

    /**
     * Extracts a native library from JAR resources to a temporary file.
     *
     * @param resourcePath the path to the resource inside the JAR
     * @param libraryName the name of the library file
     * @return the path to the extracted library file
     * @throws IOException if extraction fails
     */
    private static String extractLibraryFromJar(
        String resourcePath,
        String libraryName
    ) throws IOException {
        // Get the resource as an input stream
        InputStream libraryStream =
            NativeLibraryLoader.class.getResourceAsStream(resourcePath);
        if (libraryStream == null) {
            throw new IOException(
                "Native library not found in JAR: " + resourcePath
            );
        }

        try {
            // Create temporary directory
            Path tempDir = createTempDirectory();
            Path libraryPath = tempDir.resolve(libraryName);

            // Extract the library
            try (
                FileOutputStream outputStream = new FileOutputStream(
                    libraryPath.toFile()
                )
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = libraryStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Make the library executable
            File libraryFile = libraryPath.toFile();
            if (!libraryFile.setExecutable(true)) {
                LOGGER.warn(
                    "Failed to make library executable: {}",
                    libraryPath
                );
            }

            LOGGER.debug("Extracted native library to: {}", libraryPath);
            return libraryPath.toString();
        } finally {
            libraryStream.close();
        }
    }

    /**
     * Loads the File Provider native library.
     * This method is thread-safe and will only load the library once.
     *
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    public static synchronized void loadFileProviderLibrary()
        throws UnsatisfiedLinkError {
        String libraryName = "libf1r3drive-fileprovider.dylib";
        String resourcePath = "/" + libraryName;

        try {
            // First try to load from system library path
            try {
                System.loadLibrary("f1r3drive-fileprovider");
                LOGGER.info(
                    "Successfully loaded File Provider library from system"
                );
                return;
            } catch (UnsatisfiedLinkError e) {
                LOGGER.debug(
                    "Failed to load from system library path, trying JAR extraction: {}",
                    e.getMessage()
                );
            }

            // Extract and load from JAR resources
            String extractedPath = extractLibraryFromJar(
                resourcePath,
                libraryName
            );
            System.load(extractedPath);

            LOGGER.info(
                "Successfully loaded File Provider library from JAR: {}",
                extractedPath
            );
        } catch (Exception e) {
            String errorMsg =
                "Failed to load File Provider native library: " +
                e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new UnsatisfiedLinkError(errorMsg);
        }
    }

    /**
     * Creates a temporary directory for native libraries.
     * The directory will be deleted on JVM exit.
     *
     * @return the path to the temporary directory
     * @throws IOException if directory creation fails
     */
    private static Path createTempDirectory() throws IOException {
        String tempDirProperty = System.getProperty("java.io.tmpdir");
        Path tempBaseDir = Paths.get(tempDirProperty);

        // Create a unique temporary directory
        Path tempDir = Files.createTempDirectory(tempBaseDir, TEMP_DIR_PREFIX);

        // Schedule directory for deletion on JVM exit
        tempDir.toFile().deleteOnExit();

        LOGGER.debug("Created temporary directory: {}", tempDir);
        return tempDir;
    }

    /**
     * Checks if the FSEvents library is loaded.
     *
     * @return true if the library is loaded, false otherwise
     */
    public static boolean isLibraryLoaded() {
        return isLoaded;
    }

    /**
     * Gets the path from which the library was loaded.
     *
     * @return the library path, or null if not loaded
     */
    public static String getLoadedLibraryPath() {
        return loadedLibraryPath;
    }

    /**
     * Gets information about the current platform and library loading capabilities.
     *
     * @return platform information string
     */
    public static String getPlatformInfo() {
        StringBuilder info = new StringBuilder();
        info.append("OS: ").append(System.getProperty("os.name"));
        info.append(", Arch: ").append(System.getProperty("os.arch"));
        info.append(", Java: ").append(System.getProperty("java.version"));
        info
            .append(", Temp dir: ")
            .append(System.getProperty("java.io.tmpdir"));
        info.append(", Library loaded: ").append(isLoaded);
        if (loadedLibraryPath != null) {
            info.append(", Path: ").append(loadedLibraryPath);
        }
        return info.toString();
    }
}
