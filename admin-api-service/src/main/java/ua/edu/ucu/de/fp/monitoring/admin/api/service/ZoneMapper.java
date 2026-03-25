package ua.edu.ucu.de.fp.monitoring.admin.api.service;

import static ua.edu.ucu.de.fp.monitoring.admin.api.service.GeometryUtils.geometryFactory;

import java.util.function.Function;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.GroupDTO;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.ZoneOfInterest;

public class ZoneMapper {

    public static final Function<GroupDTO.ZoneRequest, Polygon> zoneRequestToPolygon = req -> {
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

    public static final Function<ZoneOfInterest, GroupDTO.ZoneResponse> zoneToResponse = zone -> {
        Coordinate[] coords = zone.getZone().getCoordinates();
        return new GroupDTO.ZoneResponse(
                zone.getId(),
                coords[0].y,
                coords[0].x,
                coords[2].y,
                coords[2].x,
                zone.isActive()
        );
    };
}
