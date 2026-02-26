package ua.edu.ucu.de.fp.monitoring.anomaly.model;

import java.time.LocalDateTime;

public record AnomalyNotification(
    String groupName,
    String groupLink,
    String keyword,
    String content,
    LocalDateTime timestamp
) {
    public static AnomalyNotification fromEvent(TelegramEvent event, String keyword) {
        return new AnomalyNotification(
            event.groupName(),
            event.groupLink(),
            keyword,
            event.content(),
            event.timestamp()
        );
    }
}
