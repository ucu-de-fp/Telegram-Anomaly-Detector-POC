package ua.edu.ucu.de.fp.monitoring.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.notification.model.Notification;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationEvent;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationResponse;
import ua.edu.ucu.de.fp.monitoring.notification.model.NotificationStreamEvent;
import ua.edu.ucu.de.fp.monitoring.notification.repository.NotificationRepository;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final JsonMapper jsonMapper;

    // Reactive stream for SSE
    private final Sinks.Many<NotificationStreamEvent> notificationSink =
        Sinks.many().multicast().onBackpressureBuffer();

    // Functional transformations wrapped with a reusable decorator
    private final Function<NotificationEvent, Notification> eventToEntity = decorate(
        "eventToEntity",
        event -> new Notification(
            null,
            event.groupId(),
            event.ruleName(),
            event.ruleDescription(),
            event.content(),
            event.timestamp(),
            false
        )
    );

    private final Function<Notification, NotificationResponse> entityToResponse = decorate(
        "entityToResponse",
        notification -> new NotificationResponse(
            notification.getId(),
            notification.getGroupId(),
            notification.getRuleName(),
            notification.getRuleDescription(),
            notification.getContent(),
            notification.getTimestamp(),
            notification.getIsRead()
        )
    );

    private final Function<Collection<Long>, Set<Long>> toIdSet = decorate(
        "toIdSet",
        groupIds -> groupIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet())
    );

    private final Function<Set<Long>, Predicate<NotificationStreamEvent>> streamEventByGroupIds =
        groupIds -> streamEvent ->
            Objects.nonNull(streamEvent.payload())
                    && groupIds.contains(streamEvent.payload().groupId());

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
                .doOnNext(this::emitCreatedEvent)
                .subscribe(
                    response -> log.debug("Notification processed: {}", response.id()),
                    error -> log.error("Error processing notification", error)
                );

        } catch (Exception e) {
            log.error("Error parsing notification message", e);
        }
    }

    // SSE stream for frontend
    public Flux<NotificationStreamEvent> getNotificationStream() {
        return notificationSink.asFlux();
    }

    public Flux<NotificationStreamEvent> getNotificationStreamByGroupIds(Collection<Long> groupIds) {
      return Optional
              .ofNullable(groupIds)
              .filter(NotificationService::notEmptyList)
              .map(toIdSet)
              .map(ids -> notificationSink.asFlux().filter(streamEventByGroupIds.apply(ids)))
              .orElseGet(Flux::empty);
    }

    // Get historical notifications
    public Flux<NotificationResponse> getAllNotifications(Boolean unreadOnly) {
      return Optional
              .ofNullable(unreadOnly)
              .filter(Boolean.TRUE::equals)
              .map(_ -> repository.findAllByIsReadFalseOrderByTimestampDesc())
              .orElseGet(repository::findAllByOrderByTimestampDesc)
              .map(entityToResponse);
    }

    public Flux<NotificationResponse> getNotificationsByGroupIds(Collection<Long> groupIds, Boolean unreadOnly) {
      return Optional
              .ofNullable(groupIds)
              .filter(NotificationService::notEmptyList)
              .map(toIdSet)
              .map(ids -> Optional
                      .ofNullable(unreadOnly)
                      .filter(Boolean.TRUE::equals)
                      .map(_ -> repository.findAllByGroupIdInAndIsReadFalseOrderByTimestampDesc(ids))
                      .orElseGet(() -> repository.findAllByGroupIdInOrderByTimestampDesc(ids)))
              .orElseGet(Flux::empty)
              .map(entityToResponse);
    }

  private static boolean notEmptyList(Collection<Long> ids) {
    return !ids.isEmpty();
  }

  public Mono<NotificationResponse> markAsRead(Long notificationId) {
        return repository.findById(notificationId)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + notificationId)
            ))
            .flatMap(notification -> {
                if (Boolean.TRUE.equals(notification.getIsRead())) {
                    return Mono.just(notification);
                }
                return repository.save(notification.asRead())
                    .doOnNext(saved -> emitReadEvent(entityToResponse.apply(saved)));
            })
            .map(entityToResponse);
    }

    private void emitCreatedEvent(NotificationResponse response) {
        NotificationStreamEvent streamEvent = NotificationStreamEvent.created(response);
        log.info("Broadcasting notification: {}", streamEvent);
        notificationSink.tryEmitNext(streamEvent);
    }

    private void emitReadEvent(NotificationResponse response) {
        NotificationStreamEvent streamEvent = NotificationStreamEvent.read(response);
        log.info("Broadcasting notification update: {}", streamEvent);
        notificationSink.tryEmitNext(streamEvent);
    }

    private <T, R> Function<T, R> decorate(String name, Function<T, R> function) {
        return input -> {
            long startedAt = System.nanoTime();
            try {
                return function.apply(input);
            } finally {
                long elapsedMicros = (System.nanoTime() - startedAt) / 1_000;
                log.debug("Function {} executed in {} us", name, elapsedMicros);
            }
        };
    }
}
