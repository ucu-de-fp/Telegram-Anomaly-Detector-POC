package ua.edu.ucu.de.fp.monitoring.ingestion.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class TelegramGroupRepository {

    private final DatabaseClient databaseClient;

    public Flux<TelegramGroupRow> findAllGroups() {
        return databaseClient.sql("""
                SELECT name,
                       link
                FROM telegram_groups
                """)
            .map((row, meta) -> new TelegramGroupRow(
                row.get("name", String.class),
                row.get("link", String.class)
            ))
            .all();
    }

    public record TelegramGroupRow(
        String name,
        String link
    ) {}
}
