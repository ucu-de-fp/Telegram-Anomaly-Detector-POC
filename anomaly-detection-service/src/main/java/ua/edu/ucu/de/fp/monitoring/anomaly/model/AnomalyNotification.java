package ua.edu.ucu.de.fp.monitoring.anomaly.model;

import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;

import java.time.LocalDateTime;

public record AnomalyNotification(
    Long groupId,
    AnomalyRule rule,
    String content,
    LocalDateTime timestamp
) {
    public static AnomalyNotification fromEvent(TelegramEvent event, AnomalyRule rule) {
        return new AnomalyNotification(
            event.groupId(),
            rule,
            event.content(),
            event.timestamp()
        );
    }
}
