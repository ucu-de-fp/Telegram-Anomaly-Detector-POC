package ua.edu.ucu.de.fp.monitoring.notification.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationSearchRequest;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationResponse;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationStreamEvent;
import ua.edu.ucu.de.fp.monitoring.notification.service.NotificationService;

import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {
    
    private final NotificationService service;
    
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<NotificationStreamEvent> streamNotifications(
            @RequestParam(name = "groupIds", required = false) java.util.List<Long> groupIds) {
        return Optional
                .ofNullable(groupIds)
                .filter(ids -> !ids.isEmpty())
                .map(service::getNotificationStreamByGroupIds)
                .orElseGet(service::getNotificationStream);
    }
    
    @GetMapping("/history")
    public Flux<NotificationResponse> getHistory(
            @RequestParam(name = "unreadOnly", required = false) Boolean unreadOnly) {
        return service.getAllNotifications(unreadOnly);
    }

    @PostMapping("/search")
    public Flux<NotificationResponse> searchByGroupIds(
            @RequestBody NotificationSearchRequest request) {
        return service.getNotificationsByGroupIds(request.groupIds(), request.unreadOnly());
    }

    @PatchMapping("/{id}/read")
    public Mono<NotificationResponse> markAsRead(@PathVariable("id") Long id) {
        return service.markAsRead(id);
    }
}
