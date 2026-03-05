package ua.edu.ucu.de.fp.monitoring.admin.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(
    name = "telegram_groups",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_telegram_groups_telegram_group_id",
        columnNames = "telegram_group_id"
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelegramGroup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;
    
    @NotBlank(message = "Link is required")
    @Column(nullable = false)
    private String link;

    @NotNull(message = "Telegram group id is required")
    @Column(name = "telegram_group_id", unique = true)
    private Long telegramGroupId;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point centroid;
    
    // Helper methods for functional transformations
    public TelegramGroup withName(String name) {
        return new TelegramGroup(this.id, name, this.link, this.telegramGroupId, this.polygon, this.centroid);
    }
    
    public TelegramGroup withLink(String link) {
        return new TelegramGroup(this.id, this.name, link, this.telegramGroupId, this.polygon, this.centroid);
    }

    public TelegramGroup withTelegramGroupId(Long telegramGroupId) {
        return new TelegramGroup(this.id, this.name, this.link, telegramGroupId, this.polygon, this.centroid);
    }

    public TelegramGroup withPolygon(Polygon polygon) {
        return new TelegramGroup(this.id, this.name, this.link, this.telegramGroupId, polygon, this.centroid);
    }

    public TelegramGroup withCentroid(Point centroid) {
        return new TelegramGroup(this.id, this.name, this.link, this.telegramGroupId, this.polygon, centroid);
    }
}
