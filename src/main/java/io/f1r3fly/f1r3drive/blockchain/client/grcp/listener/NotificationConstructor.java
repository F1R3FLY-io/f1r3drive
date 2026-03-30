package io.f1r3fly.f1r3drive.blockchain.client.grcp.listener;

import javax.annotation.Nullable;

public class NotificationConstructor {

    private static final String DELIMITER = ";";

    public static class NotificationReasons {
        public static final String FILE_CREATED = "FC";
        public static final String DIRECTORY_CREATED = "DC";
        public static final String FILE_WROTE = "W";
        public static final String TRUNCATED = "T";
        public static final String RENAMED = "R";
        public static final String DELETED = "D";
    }

    public record NotificationPayload(String reason, String path, @Nullable String newPath) {

        public String makeString() {
            // template: R;oldPath;newPath
            // drop 3th if newPath is null

            if (newPath == null) {
                return reason + DELIMITER + path;
            } else {
                return reason + DELIMITER + path + DELIMITER + newPath;
            }
        }

        public static NotificationPayload parseNotification(String notification) {
            String[] parts = notification.split(DELIMITER);

            if (parts.length == 2) {
                return new NotificationPayload(parts[0], parts[1], null);
            } else if (parts.length == 3) {
                return new NotificationPayload(parts[0], parts[1], parts[2]);
            } else {
                throw new IllegalArgumentException("Invalid notification format");
            }
        }
    }

}
