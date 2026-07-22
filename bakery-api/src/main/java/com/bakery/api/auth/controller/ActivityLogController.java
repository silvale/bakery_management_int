package com.bakery.api.auth.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.CommandRequest;
import com.bakery.framework.repository.CommandRequestRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nhật ký hoạt động người dùng.
 * GET /api/v1/activity-log           — danh sách có filter + phân trang
 * GET /api/v1/activity-log/entity/{id} — log theo entity cụ thể
 */
@RestController
@RequestMapping("/api/v1/activity-log")
@RequiredArgsConstructor
public class ActivityLogController {

    private final CommandRequestRepository commandRequestRepository;

    @GetMapping
    public ResponseEntity<Page<ActivityLogEntry>> getActivityLog(
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityLabel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        CommandAction actionEnum = null;
        if (action != null && !action.isBlank()) {
            try { actionEnum = CommandAction.valueOf(action.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        final CommandAction finalAction = actionEnum;
        final String actorF   = blank(actorName);
        final String entityF  = blank(entityName);
        final String labelF   = blank(entityLabel);

        // Dùng JPA Criteria / Specification để tránh lỗi type-inference của
        // Hibernate 6 khi truyền null vào @Query với PostgreSQL.
        Specification<CommandRequest> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (actorF != null)
                preds.add(cb.like(cb.lower(root.get("actorName")), "%" + actorF.toLowerCase() + "%"));
            if (finalAction != null)
                preds.add(cb.equal(root.get("action"), finalAction));
            if (entityF != null)
                preds.add(cb.like(cb.lower(root.get("entityName")), "%" + entityF.toLowerCase() + "%"));
            if (labelF != null)
                preds.add(cb.like(cb.lower(root.get("entityLabel")), "%" + labelF.toLowerCase() + "%"));
            if (from != null)
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null)
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(preds.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        return ResponseEntity.ok(commandRequestRepository.findAll(spec, pageable).map(ActivityLogEntry::from));
    }

    @GetMapping("/entity/{entityId}")
    public ResponseEntity<List<ActivityLogEntry>> getByEntity(@PathVariable UUID entityId) {
        return ResponseEntity.ok(
                commandRequestRepository.findByEntityIdOrderByCreatedAtDesc(entityId)
                        .stream().map(ActivityLogEntry::from).toList());
    }

    // ── DTO ───────────────────────────────────────────────────────

    public record ActivityLogEntry(
            UUID id,
            String entityName,
            UUID entityId,
            String action,
            String actorName,
            String entityLabel,
            String note,
            String status,
            Instant createdAt) {

        public static ActivityLogEntry from(CommandRequest c) {
            return new ActivityLogEntry(
                    c.getId(),
                    c.getEntityName(),
                    c.getEntityId(),
                    c.getAction() != null ? c.getAction().name() : null,
                    c.getActorName() != null ? c.getActorName() : c.getActor(),
                    c.getEntityLabel(),
                    c.getNote(),
                    c.getStatus() != null ? c.getStatus().name() : null,
                    c.getCreatedAt());
        }
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
