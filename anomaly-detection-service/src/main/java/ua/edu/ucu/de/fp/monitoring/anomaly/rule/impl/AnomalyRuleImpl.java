package ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl;

import lombok.Builder;
import lombok.Getter;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder
@Getter
public class AnomalyRuleImpl implements AnomalyRule {


    @Builder.Default
    protected String name = "Default name";
    @Builder.Default
    protected String description = "No description";
    @Builder.Default
    protected int windowTimeSeconds = 0;
    @Builder.Default
    protected int historyTimeSeconds = 0;

    @Builder.Default
    private final Predicate<TelegramEvent> filterCondition = event -> true;

    @Builder.Default
    private final Predicate<Events> anomalyCondition = events -> !events.windowEvents().isEmpty();

    @Override
    public Function<Events, List<AnomalyNotification>> detectAnomalyFunction() {
        return events -> Optional.of(events)
                .map(groupByGroupId())
                .map(detectAnomaliesByGroups())
                .orElseGet(Collections::emptyMap)
                .entrySet().stream()
                .map(this::createAnomalyNotification)
                .toList();
    }

    private Function<Events, Map<Long, Events>> groupByGroupId() {
        return events -> Stream.concat(events.windowEvents().stream(), events.historyEvents().stream())
                .map(TelegramEvent::groupId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> new Events(
                                events.windowEvents().stream().filter(e -> e.groupId().equals(id)).toList(),
                                events.historyEvents().stream().filter(e -> e.groupId().equals(id)).toList()
                        )
                ));
    }

    private Function<Map<Long, Events>, Map<Long, List<TelegramEvent>>> detectAnomaliesByGroups() {
        return groups -> groups.entrySet().stream()
                .filter(entry -> anomalyCondition.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().windowEvents()));
    }

    private AnomalyNotification createAnomalyNotification(Map.Entry<Long, List<TelegramEvent>>  telegramEventsByGroupId) {
        return new AnomalyNotification(telegramEventsByGroupId.getKey(), this, getContent(telegramEventsByGroupId),
                LocalDateTime.now());
    }
    private String getContent(Map.Entry<Long, List<TelegramEvent>>  telegramEventsByGroupId) {
        return telegramEventsByGroupId.getValue().size() == 1
                ? telegramEventsByGroupId.getValue().getFirst().content()
                : "*";
    }
}