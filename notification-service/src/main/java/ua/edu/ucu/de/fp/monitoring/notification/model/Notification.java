package ua.edu.ucu.de.fp.monitoring.notification.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    
    @Id
    private Long id;
    
    private Long groupId;
    private String ruleName;
    private String ruleDescription;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isRead;

    public Notification asRead() {
        return new Notification(id, groupId, ruleName, ruleDescription, content, timestamp, true);
    }
}
