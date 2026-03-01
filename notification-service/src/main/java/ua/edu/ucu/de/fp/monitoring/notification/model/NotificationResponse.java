package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    Long groupId,
    String keyword,
    String content,
    LocalDateTime timestamp
) {}
