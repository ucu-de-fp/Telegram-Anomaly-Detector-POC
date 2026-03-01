package ua.edu.ucu.de.fp.monitoring.admin.api.service;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.GroupDTO.*;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.TelegramGroup;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.ZoneOfInterest;
import ua.edu.ucu.de.fp.monitoring.admin.api.repository.TelegramGroupRepository;
import ua.edu.ucu.de.fp.monitoring.admin.api.repository.ZoneOfInterestRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class GroupManagementService {
    
    private final TelegramGroupRepository groupRepository;
    private final ZoneOfInterestRepository zoneRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    private Polygon toPolygon(List<PolygonPoint> points) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("Polygon must contain at least 3 points");
        }
        Coordinate[] coords = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            PolygonPoint point = points.get(i);
            coords[i] = new Coordinate(point.longitude(), point.latitude());
        }
        coords[points.size()] = coords[0];
        Polygon polygon = geometryFactory.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    }
    
    private List<PolygonPoint> toPointList(Polygon polygon) {
        if (polygon == null) {
            return List.of();
        }
        Coordinate[] coords = polygon.getCoordinates();
        if (coords.length == 0) {
            return List.of();
        }
        int length = coords.length;
        if (length > 1 && coords[0].equals2D(coords[length - 1])) {
            length -= 1;
        }
        List<PolygonPoint> points = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            points.add(new PolygonPoint(coords[i].y, coords[i].x));
        }
        return points;
    }

    private Point toCentroid(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        Point centroid = polygon.getCentroid();
        centroid.setSRID(4326);
        return centroid;
    }

    private Double centroidLatitude(TelegramGroup group) {
        Point centroid = group.getCentroid();
        if (centroid == null && group.getPolygon() != null) {
            centroid = toCentroid(group.getPolygon());
        }
        return centroid == null ? null : centroid.getY();
    }

    private Double centroidLongitude(TelegramGroup group) {
        Point centroid = group.getCentroid();
        if (centroid == null && group.getPolygon() != null) {
            centroid = toCentroid(group.getPolygon());
        }
        return centroid == null ? null : centroid.getX();
    }
    
    // Functional transformations
    private final Function<TelegramGroupRequest, TelegramGroup> requestToEntity = req -> {
        Polygon polygon = toPolygon(req.polygon());
        Point centroid = toCentroid(polygon);
        return new TelegramGroup(null, req.name(), req.link(), polygon, centroid);
    };
    
    private final Function<TelegramGroup, TelegramGroupResponse> entityToResponse = group -> 
        new TelegramGroupResponse(
            group.getId(),
            group.getName(),
            group.getLink(),
            toPointList(group.getPolygon()),
            centroidLatitude(group),
            centroidLongitude(group)
        );
    
    private final Function<ZoneRequest, Polygon> zoneRequestToPolygon = req -> {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(req.minLongitude(), req.minLatitude()),
            new Coordinate(req.maxLongitude(), req.minLatitude()),
            new Coordinate(req.maxLongitude(), req.maxLatitude()),
            new Coordinate(req.minLongitude(), req.maxLatitude()),
            new Coordinate(req.minLongitude(), req.minLatitude())
        };
        Polygon polygon = geometryFactory.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    };
    
    private final Function<ZoneOfInterest, ZoneResponse> zoneToResponse = zone -> {
        Coordinate[] coords = zone.getZone().getCoordinates();
        return new ZoneResponse(
            zone.getId(),
            coords[0].y,
            coords[0].x,
            coords[2].y,
            coords[2].x,
            zone.isActive()
        );
    };
    
    // Group management
    public List<TelegramGroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
            .map(entityToResponse)
            .toList();
    }

    public List<TelegramGroupResponse> getGroupsIntersectingPolygon(PolygonFilterRequest request) {
        Polygon polygon = toPolygon(request.polygon());
        return groupRepository.findGroupsIntersectingZone(polygon).stream()
            .map(entityToResponse)
            .toList();
    }
    
    public Optional<TelegramGroupResponse> getGroupById(Long id) {
        return groupRepository.findById(id)
            .map(entityToResponse);
    }
    
    @Transactional
    public TelegramGroupResponse createGroup(TelegramGroupRequest request) {
        return Optional.of(request)
            .map(requestToEntity)
            .map(groupRepository::save)
            .map(entityToResponse)
            .orElseThrow();
    }
    
    @Transactional
    public Optional<TelegramGroupResponse> updateGroup(Long id, TelegramGroupRequest request) {
        return groupRepository.findById(id)
            .map(existing -> {
                Polygon polygon = toPolygon(request.polygon());
                Point centroid = toCentroid(polygon);
                return existing.withName(request.name())
                             .withLink(request.link())
                             .withPolygon(polygon)
                             .withCentroid(centroid);
            })
            .map(groupRepository::save)
            .map(entityToResponse);
    }
    
    @Transactional
    public boolean deleteGroup(Long id) {
        return groupRepository.findById(id)
            .map(group -> {
                groupRepository.delete(group);
                return true;
            })
            .orElse(false);
    }
    
    // Zone management
    public Optional<ZoneResponse> getActiveZone() {
        return zoneRepository.findFirstByActiveTrue()
            .map(zoneToResponse);
    }
    
    @Transactional
    public ZoneResponse setZoneOfInterest(ZoneRequest request) {
        // Deactivate all existing zones
        zoneRepository.findAll().forEach(zone -> 
            zoneRepository.save(zone.deactivate())
        );
        
        // Create new active zone
        Polygon polygon = zoneRequestToPolygon.apply(request);
        ZoneOfInterest newZone = new ZoneOfInterest(null, polygon, true);
        
        return Optional.of(newZone)
            .map(zoneRepository::save)
            .map(zoneToResponse)
            .orElseThrow();
    }
}
