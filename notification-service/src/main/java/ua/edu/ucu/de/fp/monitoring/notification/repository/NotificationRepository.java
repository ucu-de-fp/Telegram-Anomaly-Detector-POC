package ua.edu.ucu.de.fp.monitoring.notification.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ua.edu.ucu.de.fp.monitoring.notification.model.Notification;

import java.util.Collection;

@Repository
public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    
    Flux<Notification> findAllByOrderByTimestampDesc();

    Flux<Notification> findAllByGroupIdInOrderByTimestampDesc(Collection<Long> groupIds);
}
