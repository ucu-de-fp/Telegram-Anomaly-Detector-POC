package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;

public record NotificationEvent(
    String groupName,
    String groupLink,
    String keyword,
    String content,
    LocalDateTime timestamp
) {}
