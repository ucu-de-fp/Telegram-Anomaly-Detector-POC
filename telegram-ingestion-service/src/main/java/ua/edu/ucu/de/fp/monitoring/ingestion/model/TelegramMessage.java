package ua.edu.ucu.de.fp.monitoring.ingestion.model;

import java.time.LocalDateTime;

// Immutable record for functional programming
public record TelegramMessage(
    Long groupId,
    String content,
    LocalDateTime timestamp
) {
    // Factory method for creating messages with current timestamp
    public static TelegramMessage create(Long groupId, String content) {
        return new TelegramMessage(groupId, content, LocalDateTime.now());
    }
}
