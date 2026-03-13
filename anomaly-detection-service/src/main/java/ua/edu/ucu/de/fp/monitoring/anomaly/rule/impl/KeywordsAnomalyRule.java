package ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl;

import lombok.Builder;
import lombok.experimental.SuperBuilder;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
public class KeywordsAnomalyRule extends AbstractAnomalyRule {

    @Builder.Default
    protected Predicate<Events> anomalyCondition = events -> !events.windowEvents().isEmpty();

    @Override
    public Function<Events, List<AnomalyNotification>> detectAnomalyFunction() {
        return events -> Optional.of(events)
                .map(groupByGroupId())
                .map(detectAnomalyFunction(anomalyCondition)) // Шукаємо аномалії
                .orElseGet(Collections::emptyMap).entrySet().stream()
                .map(this::createAnomalyNotification)
                .toList();
    }

    public Function<Events, Map<Long, Events>> groupByGroupId() {
        return events -> Stream.concat(events.windowEvents().stream(), events.historyEvents().stream())
                .map(TelegramEvent::groupId)
                .distinct()
                .collect(Collectors.toMap(groupId -> groupId, filterEventsByGroupId(events)));
    }

    private Function<Long, Events> filterEventsByGroupId(Events events) {
        return groupId -> new Events(
                events.windowEvents().stream().filter(e -> e.groupId().equals(groupId)).toList(),
                events.historyEvents().stream().filter(e -> e.groupId().equals(groupId)).toList()
        );
    }

    private Function<Map<Long, Events>, Map<Long, List<TelegramEvent>>> detectAnomalyFunction(Predicate<Events> anomalyCondition) {
        return events -> events.entrySet().stream()
                .filter(entry -> anomalyCondition.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().windowEvents()));
    }
}
