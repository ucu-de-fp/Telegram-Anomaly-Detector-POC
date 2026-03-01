package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.util.List;

public record NotificationSearchRequest(
    List<Long> groupIds
) {}
