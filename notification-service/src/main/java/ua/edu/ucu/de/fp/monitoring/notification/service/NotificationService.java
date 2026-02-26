package ua.edu.ucu.de.fp.monitoring.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.notification.model.Notification;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationEvent;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationResponse;
import ua.edu.ucu.de.fp.monitoring.notification.repository.NotificationRepository;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository repository;
    private final JsonMapper jsonMapper;
    
    // Reactive stream for SSE
    private final Sinks.Many<NotificationResponse> notificationSink = 
        Sinks.many().multicast().onBackpressureBuffer();
    
    // Functional transformations
    private final Function<NotificationEvent, Notification> eventToEntity = event ->
        new Notification(
            null,
            event.groupName(),
            event.groupLink(),
            event.keyword(),
            event.content(),
            event.timestamp()
        );
    
    private final Function<Notification, NotificationResponse> entityToResponse = notification ->
        new NotificationResponse(
            notification.getId(),
            notification.getGroupName(),
            notification.getGroupLink(),
            notification.getKeyword(),
            notification.getContent(),
            notification.getTimestamp()
        );
    
    // Listen to RabbitMQ and process notifications
    @RabbitListener(queues = "${notification.queue.name}")
    public void receiveNotification(String message) {
        try {
            NotificationEvent event = jsonMapper.readValue(message, NotificationEvent.class);
            log.info("Received notification: {}", event);
            
            // Functional pipeline: event -> entity -> save -> response -> emit
            Mono.just(event)
                .map(eventToEntity)
                .flatMap(repository::save)
                .map(entityToResponse)
                .doOnNext(response -> {
                    log.info("Broadcasting notification: {}", response);
                    notificationSink.tryEmitNext(response);
                })
                .subscribe(
                    response -> log.debug("Notification processed: {}", response.id()),
                    error -> log.error("Error processing notification", error)
                );
                
        } catch (Exception e) {
            log.error("Error parsing notification message", e);
        }
    }
    
    // SSE stream for frontend
    public Flux<NotificationResponse> getNotificationStream() {
        return notificationSink.asFlux();
    }

    public Flux<NotificationResponse> getNotificationStreamByGroupLinks(Collection<String> groupLinks) {
        if (groupLinks == null || groupLinks.isEmpty()) {
            return Flux.empty();
        }
        Set<String> linkSet = groupLinks.stream()
            .filter(link -> link != null && !link.isBlank())
            .collect(Collectors.toSet());
        if (linkSet.isEmpty()) {
            return Flux.empty();
        }
        return notificationSink.asFlux()
            .filter(notification -> linkSet.contains(notification.groupLink()));
    }
    
    // Get historical notifications
    public Flux<NotificationResponse> getAllNotifications() {
        return repository.findAllByOrderByTimestampDesc()
            .map(entityToResponse);
    }

    public Flux<NotificationResponse> getNotificationsByGroupLinks(Collection<String> groupLinks) {
        if (groupLinks == null || groupLinks.isEmpty()) {
            return Flux.empty();
        }
        Set<String> linkSet = groupLinks.stream()
            .filter(link -> link != null && !link.isBlank())
            .collect(Collectors.toSet());
        if (linkSet.isEmpty()) {
            return Flux.empty();
        }
        return repository.findAllByGroupLinkInOrderByTimestampDesc(linkSet)
            .map(entityToResponse);
    }
}
