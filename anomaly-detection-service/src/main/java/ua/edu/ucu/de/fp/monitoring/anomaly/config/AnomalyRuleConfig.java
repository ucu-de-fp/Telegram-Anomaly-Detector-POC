package ua.edu.ucu.de.fp.monitoring.anomaly.config;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import ua.edu.ucu.de.fp.monitoring.anomaly.config.properties.DetectionProperties;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.impl.AnomalyRuleImpl;

// todo: винести? оскільки DetectionProperties також використовується в конфізі rabbitmq
@EnableConfigurationProperties(DetectionProperties.class)
@Configuration
@RequiredArgsConstructor
public class AnomalyRuleConfig {

    private final DetectionProperties properties;

    @Bean
    public List<AnomalyRule> anomalyRules() {
        List<AnomalyRule> allAvailableRules = List.of(
                AnomalyRuleImpl.builder()
                        .name("Будь-яке")
                        .description("Спрацьовує на будь яке повідомлення")
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Будь-яке у групах")
                        .description("Спрацьовує на будь яке повідомлення у групах [3814005327] та [3814005328]")
                        .filterCondition(groupIdIn(3814005327L, 3814005328L))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Термінові новини")
                        .description("Спрацьовує коли у повідомлені слово \"warning\", або \"news\" та \"breaking\".")
                        .filterCondition(contains("warning").or(containsAll("news", "breaking")))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Ріст активності")
                        .description("Спрацьовує коли кількість повідомлень у групі зростає більш ніж на 20 відсотків за 10 сек.")
                        .windowTimeSeconds(30)
                        .historyTimeSeconds(90)
                        .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(30, 90, 0.5f)))
                        .build(),
                AnomalyRuleImpl.builder()
                        .name("Комбіноване")
                        .description("Спрацьовує коли кількість повідомлень зі символом \"!\" у групі [1002] зростає більш ніж на 10 відсотків за 5 сек.")
                        .windowTimeSeconds(30)
                        .historyTimeSeconds(90)
                        .filterCondition(groupIdIn(1002L).and(containsAny("!")))
                        .anomalyCondition(numberIncreaseMoreThen(calculateCoefficient(30, 90, 1.5f)))
                        .build()
        );
        return allAvailableRules.stream()
                .filter(rule -> properties.getEnabledRules().contains(rule.getName()))
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