package ua.edu.ucu.de.fp.monitoring.admin.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.TelegramGroup;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

@Repository
public interface TelegramGroupRepository extends JpaRepository<TelegramGroup, Long> {

    boolean existsByTelegramGroupId(Long telegramGroupId);

    boolean existsByTelegramGroupIdAndIdNot(Long telegramGroupId, Long id);
    
    @Query(value = "SELECT * FROM telegram_groups WHERE centroid IS NOT NULL AND ST_Within(centroid, :zone)", nativeQuery = true)
    List<TelegramGroup> findGroupsWithinZone(@Param("zone") Polygon zone);

    @Query(value = "SELECT * FROM telegram_groups WHERE polygon IS NOT NULL AND ST_Intersects(polygon, :zone)", nativeQuery = true)
    List<TelegramGroup> findGroupsIntersectingZone(@Param("zone") Polygon zone);
}
