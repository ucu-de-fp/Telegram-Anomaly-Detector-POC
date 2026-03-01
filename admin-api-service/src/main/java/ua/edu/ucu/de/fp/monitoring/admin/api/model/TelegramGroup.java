package ua.edu.ucu.de.fp.monitoring.admin.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "telegram_groups")
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

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon polygon;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point centroid;
    
    // Helper methods for functional transformations
    public TelegramGroup withName(String name) {
        return new TelegramGroup(this.id, name, this.link, this.polygon, this.centroid);
    }
    
    public TelegramGroup withLink(String link) {
        return new TelegramGroup(this.id, this.name, link, this.polygon, this.centroid);
    }

    public TelegramGroup withPolygon(Polygon polygon) {
        return new TelegramGroup(this.id, this.name, this.link, polygon, this.centroid);
    }

    public TelegramGroup withCentroid(Point centroid) {
        return new TelegramGroup(this.id, this.name, this.link, this.polygon, centroid);
    }
}
