package ua.edu.ucu.de.fp.monitoring.anomaly.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Setter;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;

/**
 * Functional reactive anomaly detection service.
 * Uses higher-order functions, predicates, and Optional for functional composition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final List<AnomalyRule> anomalyRules;
    private final Map<String, List<TelegramEvent>> ruleHistories = new ConcurrentHashMap<>();

    @Value("${detection.target-queue}")
    @Setter
    private String targetQueue;

    public void clearHistory() {
        ruleHistories.clear();
    }

    @RabbitListener(queues = "${detection.source-queue}")
    public void processEvent(String message) {
        try {
            TelegramEvent event = jsonMapper.readValue(message, TelegramEvent.class);
            log.info("Received event: {}", event);

            anomalyRules.forEach(rule -> {
                AnomalyRule.Events events = updateHistoryAndGetWindowEvents(rule, event);

                Optional.of(events)
                        .map(rule.detectAnomalyFunction()) // Шукаємо аномалії
                        .stream()
                        .flatMap(Collection::stream)
                        .peek(a -> log.info("Anomaly [{}] detected", a.rule().getName()))
                        .map(this::toJson)
                        .flatMap(Optional::stream)
                        .forEach(json -> rabbitTemplate.convertAndSend(targetQueue, json));
            });

        } catch (Exception e) {
            log.error("Error processing event", e);
        }
    }

    private AnomalyRule.Events updateHistoryAndGetWindowEvents(AnomalyRule rule, TelegramEvent newEvent) {

        boolean isNewEventMuchFilterCondition = rule.getFilterCondition().test(newEvent);
        LocalDateTime windowThreshold = LocalDateTime.now().minusSeconds(rule.getWindowTimeSeconds());
        LocalDateTime historyThreshold = windowThreshold.minusSeconds(rule.getHistoryTimeSeconds());

        List<TelegramEvent> updatedHistory = ruleHistories.compute(rule.getName(), (key, history) -> {
            List<TelegramEvent> temporaryHistory = (history == null)
                    ? new java.util.ArrayList<>()
                    : new java.util.ArrayList<>(history);
            if (isNewEventMuchFilterCondition) {
                temporaryHistory.add(newEvent);
            }
            return temporaryHistory.stream().filter(e -> e.timestamp().isAfter(historyThreshold)).toList();
        });

        return new AnomalyRule.Events(
                rule.getWindowTimeSeconds() > 0
                        ? updatedHistory.stream()
                                .filter(e -> e.timestamp().isAfter(windowThreshold))
                                .toList()
                        : isNewEventMuchFilterCondition ? List.of(newEvent) : List.of(),
                rule.getHistoryTimeSeconds() > 0
                        ? updatedHistory.stream()
                                .filter(e -> e.timestamp().isBefore(windowThreshold))
                                .toList()
                        : List.of()
        );
    }

    private Optional<String> toJson(AnomalyNotification notification) {
        return Optional.ofNullable(notification)
                .flatMap(n -> {
                    try {
                        return Optional.of(jsonMapper.writeValueAsString(n));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
    }
}
