package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String groupName,
    String groupLink,
    String keyword,
    String content,
    LocalDateTime timestamp
) {}
