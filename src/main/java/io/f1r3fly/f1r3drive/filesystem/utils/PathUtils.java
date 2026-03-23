package io.f1r3fly.f1r3drive.filesystem.utils;

public class PathUtils {
    final static String delimiter = System.getProperty("os.name").toLowerCase().contains("win") ? "\\" : "/";

    public static String getPathDelimiterBasedOnOS() {
        return delimiter;
    }

    public static String getParentPath(String path) {
        return path.substring(0, path.lastIndexOf(getPathDelimiterBasedOnOS()));
    }

    public static String getFileName(String path) {
        return path.substring(path.lastIndexOf(getPathDelimiterBasedOnOS()) + 1);
    }

    public static boolean isDeployableFile(String path) {
        return path.endsWith(".rho") || path.endsWith(".metta");
    }

    public static boolean isEncryptedExtension(String path) {
        return path.endsWith(".encrypted");
    }

    public static boolean isAppleMetadataFile(String path) {
        String filename = getFileName(path);
        return filename.equals(".DS_Store") || filename.startsWith("._");
    }
}
