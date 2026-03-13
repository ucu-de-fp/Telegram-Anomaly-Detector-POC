package ua.edu.ucu.de.fp.monitoring.anomaly.rule;

import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface AnomalyRule {
    String getName();
    String getDescription();
    int getWindowTimeSeconds();
    int getHistoryTimeSeconds();
    Predicate<TelegramEvent> getFilterCondition();
    Function<Events, List<AnomalyNotification>> detectAnomalyFunction();

    record Events(List<TelegramEvent> windowEvents, List<TelegramEvent> historyEvents) {};
}
