package ua.edu.ucu.de.fp.monitoring.anomaly.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl.KeywordsAnomalyRule;

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

    @Value("${detection.target-queue}")
    @Setter
    private String targetQueue;

    // Реєстр правил
    private final List<AnomalyRule> anomalyRules = List.of(
            KeywordsAnomalyRule.builder()
                    .name("Будь-яке")
                    .description("Спрацьовує на будь яке повідомлення")
                    .build(),
            KeywordsAnomalyRule.builder()
                    .name("Будь-яке у групах")
                    .description("Спрацьовує на будь яке повідомлення у групах [1] та [2]")
                    .filterCondition(groupIdIn(1L, 2L))
                    .build(),
            KeywordsAnomalyRule.builder()
                    .name("Термінові новини")
                    .description("Спрацьовує коли у повідомлені слово \"warning\", або \"news\" та \"breaking\".")
                    .filterCondition(contains("warning").or(containsAll("news", "breaking")))
                    .build(),
            KeywordsAnomalyRule.builder()
                    .name("Ріст активності")
                    .description("Спрацьовує коли кількість повідомлень у групі зростає більш ніж на 20 відсотків за 10 сек.")
                    .windowTimeSeconds(10)
                    .historyTimeSeconds(50)
                    .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(10, 50, 0.2f)))
                    .build(),
            KeywordsAnomalyRule.builder()
                    .name("Комбіноване")
                    .description("Спрацьовує коли кількість повідомлень зі словом \"ufo\" у групі [3] зростає більш ніж на 10 відсотків за 5 сек.")
                    .windowTimeSeconds(5)
                    .historyTimeSeconds(60)
                    .filterCondition(groupIdIn(3L).and(containsAny("ufo")))
                    .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(5, 60, 0.1f)))
                    .build()
    );

    // Сховище історії: Rule Name -> List of events
    private final Map<String, List<TelegramEvent>> ruleHistories = new ConcurrentHashMap<>();

    public void clearHistory() {
        ruleHistories.clear();
    }

    @RabbitListener(queues = "${detection.source-queue}")
    public void processEvent(String message) {
        try {
            TelegramEvent event = jsonMapper.readValue(message, TelegramEvent.class);

            anomalyRules.forEach(rule -> {
                // 1. Отримуємо та оновлюємо вікно подій для конкретного правила
                AnomalyRule.Events events = updateHistoryAndGetWindowEvents(rule, event);

                // 2. Детекція (Functional Pipeline)
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

        LocalDateTime windowThreshold = LocalDateTime.now().minusSeconds(rule.getWindowTimeSeconds());
        LocalDateTime historyThreshold = windowThreshold.minusSeconds(rule.getHistoryTimeSeconds());

        List<TelegramEvent> updatedHistory = ruleHistories.compute(rule.getName(), (key, history) -> {
            // Створюємо новий список (immutable-style approach)
            List<TelegramEvent> temporaryHistory = (history == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(history);

            if (rule.getFilterCondition().test(newEvent)) {
                temporaryHistory.add(newEvent);
            }
            return temporaryHistory;
        });

        return new AnomalyRule.Events(
                rule.getWindowTimeSeconds() > 0
                        ? updatedHistory.stream()
                                .filter(e -> e.timestamp().isAfter(windowThreshold))
                                .toList()
                        : List.of(newEvent),
                rule.getHistoryTimeSeconds() > 0
                        ? updatedHistory.stream()
                                .filter(e -> e.timestamp().isBefore(windowThreshold)
                                        && e.timestamp().isAfter(historyThreshold))
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

    Predicate<TelegramEvent> contains(String word) {
        return e -> e.content().toLowerCase().contains(word.toLowerCase());
    }

    Predicate<TelegramEvent> containsAny(String ... words) {
        return Arrays.stream(words)
                .map(word -> (Predicate<TelegramEvent>) e -> e.content().contains(word))
                .reduce(s -> false, Predicate::or);
    }

    Predicate<TelegramEvent> containsAll(String ... words) {
        return Arrays.stream(words)
                .map(word -> (Predicate<TelegramEvent>) e -> e.content().contains(word))
                .reduce(s -> true, Predicate::and);
    }

    Predicate<TelegramEvent> groupIdIn(Long ... groupIds) {
        return Arrays.stream(groupIds)
                .map(groupId -> (Predicate<TelegramEvent>) e -> e.groupId().equals(groupId))
                .reduce(s -> false, Predicate::or);
    }

    Predicate<AnomalyRule.Events> numberIncreaseMoreThen(float coefficient) {
        return events ->
                !events.historyEvents().isEmpty() && // Додаємо цю перевірку
                        events.windowEvents().size() > events.historyEvents().size() * coefficient;
    }

    private float calculateCoefficient(int windowSize, int historySize, float percentages) {
        return ((float) windowSize / historySize) * (1 + percentages);
    }
}
