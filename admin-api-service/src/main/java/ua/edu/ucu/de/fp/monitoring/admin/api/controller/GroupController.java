package ua.edu.ucu.de.fp.monitoring.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.ucu.de.fp.monitoring.admin.api.model.GroupDTO.*;
import ua.edu.ucu.de.fp.monitoring.admin.api.service.GroupManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GroupController {
    
    private final GroupManagementService service;
    
    @GetMapping
    public ResponseEntity<List<TelegramGroupResponse>> getAllGroups() {
        return ResponseEntity.ok(service.getAllGroups());
    }

    @PostMapping("/search")
    public ResponseEntity<List<TelegramGroupResponse>> searchGroupsByPolygon(
            @Valid @RequestBody PolygonFilterRequest request) {
        return ResponseEntity.ok(service.getGroupsIntersectingPolygon(request));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TelegramGroupResponse> getGroup(@PathVariable Long id) {
        return service.getGroupById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<TelegramGroupResponse> createGroup(
            @Valid @RequestBody TelegramGroupRequest request) {
        return ResponseEntity.ok(service.createGroup(request));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TelegramGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody TelegramGroupRequest request) {
        return service.updateGroup(id, request)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        return service.deleteGroup(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}

@RestController
@RequestMapping("/api/zone")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
class ZoneController {
    
    private final GroupManagementService service;
    
    @GetMapping
    public ResponseEntity<ZoneResponse> getActiveZone() {
        return service.getActiveZone()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<ZoneResponse> setZone(@Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.ok(service.setZoneOfInterest(request));
    }
}
