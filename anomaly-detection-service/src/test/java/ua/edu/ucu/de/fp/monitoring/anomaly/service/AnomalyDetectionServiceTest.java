package ua.edu.ucu.de.fp.monitoring.anomaly.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.anomaly.config.AnomalyRuleConfig;
import ua.edu.ucu.de.fp.monitoring.anomaly.config.properties.DetectionProperties;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;
import ua.edu.ucu.de.fp.monitoring.anomaly.rule.AnomalyRule;

class AnomalyDetectionServiceTest {

    private AnomalyDetectionService service;
    private RabbitTemplate rabbitTemplate;
    private JsonMapper jsonMapper;
    private final String QUEUE = "anomaly-notifications";

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        DetectionProperties properties = new DetectionProperties();
        properties.setEnabledRules(List.of(
                "Будь-яке",
                "Будь-яке у групах",
                "Термінові новини",
                "Ріст активності",
                "Комбіноване"
        ));
        AnomalyRuleConfig config = new AnomalyRuleConfig(properties);
        List<AnomalyRule> anomalyRules = config.anomalyRules();
        service = new AnomalyDetectionService(rabbitTemplate, jsonMapper, anomalyRules);
        service.setTargetQueue(QUEUE);
        service.clearHistory();
    }

    @Test
    @DisplayName("Stateless правила: Будь-яке, Будь-яке у групах та Термінові новини")
    void testStatelessRules() throws Exception {
        // Групи в конфігу: 3814005327 та 3814005328
        var event = new TelegramEvent(3814005327L, "System Warning alert", LocalDateTime.now());

        service.processEvent(jsonMapper.writeValueAsString(event));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Очікуємо 3 аномалії
        verify(rabbitTemplate, times(3)).convertAndSend(eq(QUEUE), captor.capture());
        List<String> results = captor.getAllValues();
        assertTrue(results.stream().anyMatch(s -> s.contains("Будь-яке")));
        assertTrue(results.stream().anyMatch(s -> s.contains("Будь-яке у групах")));
        assertTrue(results.stream().anyMatch(s -> s.contains("Термінові новини")));
    }

    @Test
    @DisplayName("Правило: Ріст активності (Trend detection 30s/90s + 50%)")
    void testActivityGrowthRule() throws Exception {
        // Коефіцієнт (30/90) * 1.5 = 0.5

        // 1. Створюємо історію - 10 повідомлень (historyThreshold = now - 120s, windowThreshold = now - 30s)
        for (int i = 0; i < 10; i++) {
            var oldEvent = new TelegramEvent(5L, "Old msg", LocalDateTime.now().minusSeconds(40));
            service.processEvent(jsonMapper.writeValueAsString(oldEvent));
        }
        clearInvocations(rabbitTemplate);

        // 2. Додаємо 3 повідомлення у вікно (30с)
        // 3 > 10 * 0.5 = 5 (False)
        var currentEvent = new TelegramEvent(5L, "Current msg", LocalDateTime.now());
        for (int i = 0; i < 3; i++) {
            service.processEvent(jsonMapper.writeValueAsString(currentEvent));
        }
        verifyAnomalyNotSent("Ріст активності");

        // 3. Додаємо ще 3 повідомлення (разом 6 у вікні)
        // 6 > 10 * 0.5 = 5 (True)
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));

        verifyAnomalySent("Ріст активності");
    }

    @Test
    @DisplayName("Правило: Комбіноване (Filter + Trend 30s/90s + 150%)")
    void testCombinedRule() throws Exception {
        // Коефіцієнт (30/90) * 2.5 = 0.833
        Long targetGroup = 1002L;

        // 1. Історія: 6 повідомлень з "!" у групі 1002
        for (int i = 0; i < 10; i++) {
            service.processEvent(jsonMapper.writeValueAsString(
                    new TelegramEvent(targetGroup, "Alarm!", LocalDateTime.now().minusSeconds(45))));
        }
        clearInvocations(rabbitTemplate);

        // 2. Вікно: 5 повідомлень
        // 5 > 6 * 0.833 = 4.998 (True)
        var currentEvent = new TelegramEvent(targetGroup, "Danger!", LocalDateTime.now());
        for (int i = 0; i < 9; i++) {
            service.processEvent(jsonMapper.writeValueAsString(currentEvent));
        }

        verifyAnomalySent("Комбіноване");
    }

    private void verifyAnomalySent(String ruleName) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(eq(QUEUE), captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(s -> s.contains(ruleName)),
                "Мала бути зафіксована аномалія: " + ruleName);
    }

    private void verifyAnomalyNotSent(String ruleName) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(eq(QUEUE), captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(s -> s.contains(ruleName)),
                "Аномалії '" + ruleName + "' не повинно бути");
    }
}