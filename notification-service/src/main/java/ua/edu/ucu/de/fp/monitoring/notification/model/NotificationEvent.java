package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;

public record NotificationEvent(
    Long groupId,
    Rule rule,
    String content,
    LocalDateTime timestamp
) {
    public String ruleName() {
        return rule == null ? null : rule.name();
    }

    public String ruleDescription() {
        return rule == null ? null : rule.description();
    }

    public record Rule(String name, String description) { }
}
