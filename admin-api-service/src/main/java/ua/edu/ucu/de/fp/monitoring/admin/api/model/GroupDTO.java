package ua.edu.ucu.de.fp.monitoring.admin.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// DTOs using records for immutability (functional approach)
public class GroupDTO {
    
    public record TelegramGroupRequest(
            @NotBlank String name,
            @NotBlank String link,
            @NotNull Long telegramGroupId,
            @NotNull @Size(min = 3) List<@Valid PolygonPoint> polygon
    ) {}
    
    public record TelegramGroupResponse(
            Long id,
            String name,
            String link,
            Long telegramGroupId,
            List<PolygonPoint> polygon,
            Double centroidLatitude,
            Double centroidLongitude
    ) {}

    public record PolygonPoint(
            @NotNull Double latitude,
            @NotNull Double longitude
    ) {}

    public record PolygonFilterRequest(
            @NotNull @Size(min = 3) List<@Valid PolygonPoint> polygon
    ) {}
    
    public record ZoneRequest(
            @NotNull Double minLatitude,
            @NotNull Double minLongitude,
            @NotNull Double maxLatitude,
            @NotNull Double maxLongitude
    ) {}
    
    public record ZoneResponse(
            Long id,
            Double minLatitude,
            Double minLongitude,
            Double maxLatitude,
            Double maxLongitude,
            boolean active
    ) {}
}
