package ua.edu.ucu.de.fp.monitoring.notification.model;

public record NotificationStreamEvent(
    String type,
    NotificationResponse payload
) {
    public static NotificationStreamEvent created(NotificationResponse payload) {
        return new NotificationStreamEvent("CREATED", payload);
    }

    public static NotificationStreamEvent read(NotificationResponse payload) {
        return new NotificationStreamEvent("READ", payload);
    }
}
