package com.bakery.api.framework.controllers;

import com.bakery.api.framework.ActivityLog;
import com.bakery.api.framework.repositories.ActivityLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/activity")
@RequiredArgsConstructor
@Tag(name = "Activity Logs", description = "Lịch sử hoạt động nghiệp vụ — audit trail")
public class ActivityLogController {

    private final ActivityLogRepository activityLogRepository;

    @GetMapping("/logs")
    @Operation(summary = "Danh sách log hoạt động")
    public ResponseEntity<Object> listLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            List<ActivityLog> logs;

            if (performedBy != null) {
                logs = activityLogRepository.findAllByPerformedByOrderByCreatedAtDesc(performedBy);
                if (entityType != null) {
                    final String entityTypeFilter = entityType;
                    logs = logs.stream()
                        .filter(l -> entityTypeFilter.equals(l.getEntityType()))
                        .collect(Collectors.toList());
                }
            } else if (entityType != null) {
                logs = activityLogRepository.findAllByEntityTypeOrderByCreatedAtDesc(entityType);
            } else {
                logs = activityLogRepository.findAll().stream()
                    .sorted(Comparator.comparing(ActivityLog::getCreatedAt).reversed())
                    .collect(Collectors.toList());
            }

            if (from != null) {
                OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
                logs = logs.stream()
                    .filter(l -> !l.getCreatedAt().isBefore(start))
                    .collect(Collectors.toList());
            }
            if (to != null) {
                OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                logs = logs.stream()
                    .filter(l -> l.getCreatedAt().isBefore(end))
                    .collect(Collectors.toList());
            }

            return ResponseEntity.ok(logs.stream().map(this::toMap).collect(Collectors.toList()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/logs/entity/{type}/{entityId}")
    @Operation(summary = "Tất cả log cho một entity cụ thể")
    public ResponseEntity<Object> getEntityLogs(
            @PathVariable String type,
            @PathVariable UUID entityId) {
        try {
            List<ActivityLog> logs = activityLogRepository
                .findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(type, entityId);
            return ResponseEntity.ok(logs.stream().map(this::toMap).collect(Collectors.toList()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(ActivityLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("performedBy", log.getPerformedBy());
        m.put("action", log.getAction());
        m.put("entityType", log.getEntityType());
        m.put("entityId", log.getEntityId());
        m.put("entityCode", log.getEntityCode());
        m.put("oldStatus", log.getOldStatus());
        m.put("newStatus", log.getNewStatus());
        m.put("note", log.getNote());
        m.put("createdAt", log.getCreatedAt().toString());
        return m;
    }
}
