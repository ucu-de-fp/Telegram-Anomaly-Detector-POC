package ua.edu.ucu.de.fp.monitoring.anomaly.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.AnomalyNotification;
import ua.edu.ucu.de.fp.monitoring.anomaly.model.TelegramEvent;

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
    private String targetQueue;
    
    @Value("${detection.keywords}")
    private List<String> keywords;
    
    // Functional predicates for anomaly detection
    private Predicate<String> containsKeyword(String keyword) {
        return content -> content != null && 
                         content.toLowerCase().contains(keyword.toLowerCase());
    }
    
    private Predicate<TelegramEvent> isAnomaly = event ->
        keywords.stream()
               .anyMatch(keyword -> containsKeyword(keyword).test(event.content()));
    
    // Function to find matching keyword
    private Function<TelegramEvent, Optional<String>> findMatchingKeyword = event ->
        keywords.stream()
               .filter(keyword -> containsKeyword(keyword).test(event.content()))
               .findFirst();
    
    // Functional pipeline for event processing
    @RabbitListener(queues = "${detection.source-queue}")
    public void processEvent(String message) {
        try {
            TelegramEvent event = jsonMapper.readValue(message, TelegramEvent.class);
            log.debug("Processing event: \"{}\" from groupId \"{}\"", event.content(), event.groupId());
            
            // Functional composition: parse -> filter -> transform -> publish
            Optional.of(event)
                .filter(isAnomaly)
                .flatMap(e -> findMatchingKeyword.apply(e)
                    .map(keyword -> AnomalyNotification.fromEvent(e, keyword)))
                .map(this::toJson)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .ifPresentOrElse(
                    json -> {
                        rabbitTemplate.convertAndSend(targetQueue, json);
                        log.info("Anomaly detected and published: {}", event.groupId());
                    },
                    () -> log.debug("No anomaly detected in event from {}", event.groupId())
                );
                
        } catch (Exception e) {
            log.error("Error processing event", e);
        }
    }
    
    private Optional<String> toJson(AnomalyNotification notification) {
        try {
            return Optional.of(jsonMapper.writeValueAsString(notification));
        } catch (Exception e) {
            log.error("Error serializing notification", e);
            return Optional.empty();
        }
    }
}
