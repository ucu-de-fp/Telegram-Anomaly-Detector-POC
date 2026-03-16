package ua.edu.ucu.de.fp.monitoring.anomaly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl.AnomalyRuleImpl;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Configuration
public class AnomalyRuleConfig {

    @Value("${detection.enabled-rules}")
    private List<String> enabledRuleNames;

    @Bean
    public List<AnomalyRule> anomalyRules() {
        List<AnomalyRule> allAvailableRules = List.of(
                AnomalyRuleImpl.builder()
                        .name("Будь-яке")
                        .description("Спрацьовує на будь яке повідомлення")
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Будь-яке у групах")
                        .description("Спрацьовує на будь яке повідомлення у групах [1] та [2]")
                        .filterCondition(groupIdIn(1L, 2L))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Термінові новини")
                        .description("Спрацьовує коли у повідомлені слово \"warning\", або \"news\" та \"breaking\".")
                        .filterCondition(contains("warning").or(containsAll("news", "breaking")))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Ріст активності")
                        .description("Спрацьовує коли кількість повідомлень у групі зростає більш ніж на 20 відсотків за 10 сек.")
                        .windowTimeSeconds(10)
                        .historyTimeSeconds(50)
                        .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(10, 50, 0.2f)))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Комбіноване")
                        .description("Спрацьовує коли кількість повідомлень зі словом \"ufo\" у групі [3] зростає більш ніж на 10 відсотків за 5 сек.")
                        .windowTimeSeconds(5)
                        .historyTimeSeconds(60)
                        .filterCondition(groupIdIn(3L).and(containsAny("ufo")))
                        .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(5, 60, 0.1f)))
                        .build()
        );
        return allAvailableRules.stream()
                .filter(rule -> enabledRuleNames.contains(rule.getName()))
                .toList();
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