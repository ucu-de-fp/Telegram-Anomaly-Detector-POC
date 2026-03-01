package ua.edu.ucu.de.fp.monitoring.notification.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationSearchRequest;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationResponse;
import ua.edu.ucu.de.fp.monitoring.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {
    
    private final NotificationService service;
    
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<NotificationResponse> streamNotifications(
            @RequestParam(name = "groupIds", required = false) java.util.List<Long> groupIds) {
        if (groupIds == null) {
            return service.getNotificationStream();
        }
        return service.getNotificationStreamByGroupIds(groupIds);
    }
    
    @GetMapping("/history")
    public Flux<NotificationResponse> getHistory() {
        return service.getAllNotifications();
    }

    @PostMapping("/search")
    public Flux<NotificationResponse> searchByGroupIds(
            @RequestBody NotificationSearchRequest request) {
        return service.getNotificationsByGroupIds(request.groupIds());
    }
}
