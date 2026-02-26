package ua.edu.ucu.de.fp.monitoring.ingestion.model;

import java.time.LocalDateTime;

// Immutable record for functional programming
public record TelegramMessage(
    String groupName,
    String groupLink,
    String content,
    LocalDateTime timestamp
) {
    // Factory method for creating messages with current timestamp
    public static TelegramMessage create(String groupName, String groupLink, String content) {
        return new TelegramMessage(groupName, groupLink, content, LocalDateTime.now());
    }
}
