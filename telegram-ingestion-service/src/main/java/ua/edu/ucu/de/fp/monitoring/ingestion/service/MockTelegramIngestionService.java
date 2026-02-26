package ua.edu.ucu.de.fp.monitoring.ingestion.service;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import ua.edu.ucu.de.fp.monitoring.ingestion.model.TelegramMessage;
import ua.edu.ucu.de.fp.monitoring.ingestion.repository.TelegramGroupRepository;
import ua.edu.ucu.de.fp.monitoring.ingestion.repository.TelegramGroupRepository.TelegramGroupRow;

/**
 * Mock service that generates random Telegram messages from Ukrainian locations.
 * This simulates data ingestion without requiring actual Telegram API integration.
 * 
 * Note: For production, consider using TDLight Java library for Telegram API:
 * https://github.com/tdlight-team/tdlight-java
 */
@Service
@ConditionalOnProperty(name = "ingestion.mock.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MockTelegramIngestionService {
    
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final TelegramGroupRepository groupRepository;
    
    @Value("${ingestion.queue.name}")
    private String queueName;
    
    @Value("${ingestion.mock.interval-seconds}")
    private int intervalSeconds;
    
    private final Random random = new Random();
    private final AtomicBoolean loggedNoGroups = new AtomicBoolean(false);
    
    private final List<String> mockMessages = List.of(
        "Check out this new project!",
        "Meeting tomorrow at 10 AM",
        "Great discussion today!",
        "Warning: server maintenance scheduled",
        "New tutorial on reactive programming",
        "Alert: high CPU usage detected",
        "Code review session at 3 PM",
        "Urgent: deployment issue found",
        "Congratulations on the release!",
        "Crisis: database connection lost"
    );
    
    // Functional supplier for generating random messages
    private final Supplier<String> contentGenerator = () ->
        mockMessages.get(random.nextInt(mockMessages.size()));
    
    @PostConstruct
    public void startIngestion() {
        log.info("Starting mock Telegram ingestion service with interval {} seconds", intervalSeconds);
        
        // Reactive stream: infinite flux with intervals
        Flux.interval(Duration.ofSeconds(intervalSeconds))
            .flatMap(tick -> groupRepository.findAllGroups()
                .collectList()
                .onErrorResume(error -> {
                    log.error("Failed to load groups for mock ingestion", error);
                    return Mono.just(List.of());
                }))
            .flatMap(groups -> {
                if (groups.isEmpty()) {
                    if (loggedNoGroups.compareAndSet(false, true)) {
                        log.warn("No groups configured. Skipping mock ingestion.");
                    }
                    return Flux.empty();
                }
                loggedNoGroups.set(false);
                TelegramGroupRow group = groups.get(random.nextInt(groups.size()));
                TelegramMessage message = TelegramMessage.create(
                    group.name(),
                    group.link(),
                    contentGenerator.get()
                );
                return Flux.just(message);
            })
            .doOnNext(message -> log.info("Generated message: {} from {}",
                message.content(), message.groupName()))
            .map(this::toJson)
            .filter(json -> json != null)
            .subscribe(
                json -> rabbitTemplate.convertAndSend(queueName, json),
                error -> log.error("Error in ingestion stream", error)
            );
    }
    
    private String toJson(TelegramMessage message) {
        try {
            return jsonMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error serializing message", e);
            return null;
        }
    }
}
