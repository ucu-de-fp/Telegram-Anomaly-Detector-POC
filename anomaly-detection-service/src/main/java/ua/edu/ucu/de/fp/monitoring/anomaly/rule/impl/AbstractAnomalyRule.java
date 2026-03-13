package ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@SuperBuilder
@Getter
@Setter
public abstract class AbstractAnomalyRule implements AnomalyRule {

    @Builder.Default
    protected String name = "Default name";
    @Builder.Default
    protected String description = "No description";
    @Builder.Default
    protected int windowTimeSeconds = 0;
    @Builder.Default
    protected int historyTimeSeconds = 0;
    @Builder.Default
    protected Predicate<TelegramEvent> filterCondition = groupId -> true;

    protected AnomalyNotification createAnomalyNotification(Map.Entry<Long, List<TelegramEvent>>  telegramEventsByGroupId) {
        return new AnomalyNotification(telegramEventsByGroupId.getKey(), this, getContent(telegramEventsByGroupId),
                LocalDateTime.now());
    }
    private String getContent(Map.Entry<Long, List<TelegramEvent>>  telegramEventsByGroupId) {
        return telegramEventsByGroupId.getValue().size() == 1
                ? telegramEventsByGroupId.getValue().getFirst().content()
                : "*";
    }
}
