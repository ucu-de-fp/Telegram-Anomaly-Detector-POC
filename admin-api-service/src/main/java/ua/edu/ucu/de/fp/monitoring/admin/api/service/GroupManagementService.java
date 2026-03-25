package ua.edu.ucu.de.fp.monitoring.admin.api.service;

import static ua.edu.ucu.de.fp.monitoring.admin.api.service.GroupMapper.*;
import static ua.edu.ucu.de.fp.monitoring.admin.api.service.ZoneMapper.zoneRequestToPolygon;
import static ua.edu.ucu.de.fp.monitoring.admin.api.service.ZoneMapper.zoneToResponse;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.GroupDTO.*;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.ZoneOfInterest;
import ua.edu.ucu.de.fp.monitoring.admin.api.repository.TelegramGroupRepository;
import ua.edu.ucu.de.fp.monitoring.admin.api.repository.ZoneOfInterestRepository;

@Service
@RequiredArgsConstructor
public class GroupManagementService {
    private final Function<ZoneOfInterest, ZoneOfInterest> deactivateZone = ZoneOfInterest::deactivate;
    
    private final TelegramGroupRepository groupRepository;
    private final ZoneOfInterestRepository zoneRepository;
    
    // Group management
    public List<TelegramGroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
            .map(entityToResponse)
            .toList();
    }

    public List<TelegramGroupResponse> getGroupsIntersectingPolygon(PolygonFilterRequest request) {
        Polygon polygon = lazyPolygon.apply(request.polygon()).get();
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
        if (groupRepository.existsByTelegramGroupId(request.telegramGroupId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Telegram group id already exists: " + request.telegramGroupId()
            );
        }
        return Optional.of(request)
            .map(requestToEntity)
            .map(groupRepository::save)
            .map(entityToResponse)
            .orElseThrow();
    }
    
    @Transactional
    public Optional<TelegramGroupResponse> updateGroup(Long id, TelegramGroupRequest request) {
        if (groupRepository.existsByTelegramGroupIdAndIdNot(request.telegramGroupId(), id)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Telegram group id already exists: " + request.telegramGroupId()
            );
        }
        return groupRepository.findById(id)
            .map(requestToUpdater.apply(request))
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
        zoneRepository.findAll().stream()
            .map(deactivateZone)
            .forEach(zoneRepository::save);
        
        // Create new active zone
        Polygon polygon = zoneRequestToPolygon.apply(request);
        ZoneOfInterest newZone = new ZoneOfInterest(null, polygon, true);
        
        return Optional.of(newZone)
            .map(zoneRepository::save)
            .map(zoneToResponse)
            .orElseThrow();
    }
}
