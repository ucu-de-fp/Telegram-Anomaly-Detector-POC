package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    
    @Id
    private Long id;
    
    private Long groupId;
    private String keyword;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isRead;
    
    // Functional helpers
    public Notification withId(Long id) {
        return new Notification(id, groupId, keyword, content, timestamp, isRead);
    }

    public Notification asRead() {
        return new Notification(id, groupId, keyword, content, timestamp, true);
    }
}
