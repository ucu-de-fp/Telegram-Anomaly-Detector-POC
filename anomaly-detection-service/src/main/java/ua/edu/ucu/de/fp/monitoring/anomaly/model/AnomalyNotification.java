package ua.edu.ucu.de.fp.monitoring.anomaly.model;

import java.time.LocalDateTime;

public record AnomalyNotification(
    Long groupId,
    String keyword,
    String content,
    LocalDateTime timestamp
) {
    public static AnomalyNotification fromEvent(TelegramEvent event, String keyword) {
        return new AnomalyNotification(
            event.groupId(),
            keyword,
            event.content(),
            event.timestamp()
        );
    }
}
