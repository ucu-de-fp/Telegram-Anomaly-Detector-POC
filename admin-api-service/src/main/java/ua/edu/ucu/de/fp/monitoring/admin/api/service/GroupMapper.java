package ua.edu.ucu.de.fp.monitoring.admin.api.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.GroupDTO;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.TelegramGroup;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static ua.edu.ucu.de.fp.monitoring.admin.api.service.GeometryUtils.geometryFactory;

public class GroupMapper {

    public static final Function<List<GroupDTO.PolygonPoint>, Supplier<Polygon>> lazyPolygon =
            points -> () -> toPolygon(points);

    public static final Function<Polygon, Point> polygonToCentroid = GroupMapper::toCentroid;

    public static final Function<GroupDTO.TelegramGroupRequest, TelegramGroup> requestToEntity = req -> {
        Polygon polygon = lazyPolygon.apply(req.polygon()).get();
        Point centroid = polygonToCentroid.apply(polygon);
        return new TelegramGroup(null, req.name(), req.link(), req.telegramGroupId(), polygon, centroid);
    };

    public static final Function<Polygon, List<GroupDTO.PolygonPoint>> polygonToList = p -> {
        Coordinate[] coords = p.getCoordinates();
        int length = coords.length;
        if (length > 1 && coords[0].equals2D(coords[length - 1])) {
            length -= 1;
        }
        return IntStream.range(0, length)
                .mapToObj(i -> new GroupDTO.PolygonPoint(coords[i].y, coords[i].x))
                .toList();
    };

    public static final Function<GroupDTO.TelegramGroupRequest, Function<TelegramGroup, TelegramGroup>> requestToUpdater =
            req -> existing -> {
                Polygon polygon = lazyPolygon.apply(req.polygon()).get();
                Point centroid = polygonToCentroid.apply(polygon);
                return existing.withName(req.name())
                        .withLink(req.link())
                        .withTelegramGroupId(req.telegramGroupId())
                        .withPolygon(polygon)
                        .withCentroid(centroid);
            };

    public static final Function<TelegramGroup, GroupDTO.TelegramGroupResponse> entityToResponse = group ->
            new GroupDTO.TelegramGroupResponse(
                    group.getId(),
                    group.getName(),
                    group.getLink(),
                    group.getTelegramGroupId(),
                    toPointList(group.getPolygon()),
                    group.getCentroid().getY(),
                    group.getCentroid().getX()
            );

    public static Point toCentroid(Polygon polygon) {
        Point centroid = polygon.getCentroid();
        centroid.setSRID(4326);
        return centroid;
    }

    private static Polygon toPolygon(List<GroupDTO.PolygonPoint> points) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("Polygon must contain at least 3 points");
        }
        Coordinate[] coords = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            GroupDTO.PolygonPoint point = points.get(i);
            coords[i] = new Coordinate(point.longitude(), point.latitude());
        }
        coords[points.size()] = coords[0];
        Polygon polygon = geometryFactory.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    }

    private static List<GroupDTO.PolygonPoint> toPointList(Polygon polygon) {
        return Optional.ofNullable(polygon)
                .filter(p -> p.getCoordinates().length > 0)
                .map(polygonToList)
                .orElseGet(Collections::emptyList);
    }
}
