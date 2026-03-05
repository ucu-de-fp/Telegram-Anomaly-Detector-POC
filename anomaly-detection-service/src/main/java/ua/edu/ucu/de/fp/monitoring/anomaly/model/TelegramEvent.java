package ua.edu.ucu.de.fp.monitoring.anomaly.model;

import java.time.LocalDateTime;

// Immutable records for functional programming
public record TelegramEvent(
    Long groupId,
    String content,
    LocalDateTime timestamp
) {}
