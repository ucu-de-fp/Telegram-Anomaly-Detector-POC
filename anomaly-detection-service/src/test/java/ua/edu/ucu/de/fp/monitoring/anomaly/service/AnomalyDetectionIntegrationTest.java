package ua.edu.ucu.de.fp.monitoring.anomaly.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnomalyDetectionServiceTest {

    private AnomalyDetectionService service;
    private RabbitTemplate rabbitTemplate;
    private JsonMapper jsonMapper;
    private final String QUEUE = "anomaly-notifications";

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        service = new AnomalyDetectionService(rabbitTemplate, jsonMapper);
        service.setTargetQueue(QUEUE);
        service.clearHistory();
    }

    @Test
    @DisplayName("Stateless правила: Будь-яке та Термінові новини")
    void testStatelessRules() throws Exception {
        // Подія, що підпадає під "Будь-яке", "Будь-яке у групах (гр.1)" та "Термінові новини (warning)"
        var event = new TelegramEvent(1L, "System warning alert", LocalDateTime.now());

        service.processEvent(jsonMapper.writeValueAsString(event));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Очікуємо 3 аномалії: "Будь-яке", "Будь-яке у групах", "Термінові новини"
        verify(rabbitTemplate, times(3)).convertAndSend(eq(QUEUE), captor.capture());

        List<String> results = captor.getAllValues();
        assertTrue(results.stream().anyMatch(s -> s.contains("Будь-яке")));
        assertTrue(results.stream().anyMatch(s -> s.contains("Термінові новини")));
    }

    @Test
    @DisplayName("Правило: Ріст активності (Trend detection)")
    void testActivityGrowthRule() throws Exception {
        // Налаштування: window 10s, history 50s. Коефіцієнт ~0.24
        // Потрібно: windowEvents.size > historyEvents.size * 0.24

        // 1. Створюємо "історію" (старі повідомлення) - 10 штук
        for (int i = 0; i < 10; i++) {
            var oldEvent = new TelegramEvent(5L, "Old msg", LocalDateTime.now().minusSeconds(20));
            service.processEvent(jsonMapper.writeValueAsString(oldEvent));
        }
        // Очистимо виклики rabbitTemplate, які могли статися через правило "Будь-яке"
        clearInvocations(rabbitTemplate);

        // 2. Додаємо нове повідомлення в поточне вікно
        // Тепер history=10, window=1. 1 > 10 * 0.24 = 2.4 (False) - Аномалії бути не повинно
        var currentEvent = new TelegramEvent(5L, "Current msg", LocalDateTime.now());
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));

        // Перевіряємо, що аномалія "Ріст активності" не відправлена (тільки базові правила)
        verifyAnomalyNotSent("Ріст активності");

        // 3. Додаємо ще 3 повідомлення (разом 4 у вікні)
        // 4 > 10 * 0.24 = 2.4 (True)
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));
        service.processEvent(jsonMapper.writeValueAsString(currentEvent));

        verifyAnomalySent("Ріст активності");
    }

    @Test
    @DisplayName("Правило: Комбіноване (Filter + Trend)")
    void testCombinedRule() throws Exception {
        // Слово "ufo" у групі 3. window 5s, history 60s.
        Long targetGroup = 3L;

        // 1. Події в іншій групі або без слова "ufo" не мають впливати
        service.processEvent(jsonMapper.writeValueAsString(new TelegramEvent(targetGroup, "hello", LocalDateTime.now())));
        service.processEvent(jsonMapper.writeValueAsString(new TelegramEvent(4L, "ufo", LocalDateTime.now())));

        clearInvocations(rabbitTemplate);

        // 2. Створюємо історію для правила (гр.3 + "ufo") - 5 повідомлень
        for (int i = 0; i < 5; i++) {
            service.processEvent(jsonMapper.writeValueAsString(
                    new TelegramEvent(targetGroup, "ufo spotted", LocalDateTime.now().minusSeconds(10))));
        }

        // 3. Додаємо повідомлення у вікно
        // Коефіцієнт для 5s/60s + 10% ≈ 0.09. 1 > 5 * 0.09 (True)
        service.processEvent(jsonMapper.writeValueAsString(
                new TelegramEvent(targetGroup, "ufo here", LocalDateTime.now())));

        verifyAnomalySent("Комбіноване");
    }

    // --- Допоміжні методи для scannability ---

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