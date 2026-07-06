package com.bakery.api.framework.controller;

import com.bakery.api.framework.dto.*;
import com.bakery.api.framework.exception.AdminEntityNotFoundException;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.BaseAdminEntity;
import com.bakery.common.entity.CommandRequest;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract REST controller cho Admin CRUD + Approval workflow.
 *
 * 3 tab UI:
 *   GET /active   → bảng chính (ACTIVE entities)
 *   GET /pending  → CommandRequest PENDING
 *   GET /rejected → CommandRequest REJECTED
 *
 * Lifecycle:
 *   POST /submit/create     → tạo CommandRequest PENDING
 *   POST /submit/update/:id → update CommandRequest PENDING
 *   POST /submit/delete/:id → delete CommandRequest PENDING
 *   POST /approve/:cmdId    → approve → execute vào bảng chính
 *   POST /reject/:cmdId     → reject
 *
 * Concrete controller chỉ cần:
 *   1. @RestController + @RequestMapping("/admin/xxx")
 *   2. Inject support, commandService, historyService
 *   3. Override abstractSupport(), abstractCommand()
 */
@RequiredArgsConstructor
public abstract class AdminBaseResource<REQ, RES extends BakeryBaseResponse, E extends BaseAdminEntity> {

    protected abstract AdminEntitySupportService<REQ, RES, E> abstractSupport();
    protected abstract AdminCommandService<REQ, RES, E> abstractCommand();
    protected abstract EntityHistoryService abstractHistory();

    // ── Tab Active: list bảng chính ───────────────────────────

    @GetMapping("/active")
    @Operation(summary = "Danh sách entities đang active")
    public PageResult<RES> listActive(AdminFilter filter) {
        return PageResult.of(abstractSupport().list(filter));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy entity theo id")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        return abstractSupport().findById(id)
            .map(abstractSupport()::toResponse)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new AdminEntityNotFoundException(abstractSupport().entityType(), id));
    }

    // ── Submit commands ───────────────────────────────────────

    @PostMapping("/submit/create")
    @Operation(summary = "Submit yêu cầu tạo mới (→ PENDING)")
    public ResponseEntity<Map<String, Object>> submitCreate(
            @RequestBody REQ request,
            @RequestParam(required = false) String note) {

        CommandRequest cmd = abstractCommand().submitCreate(request, note);
        return ResponseEntity.accepted().body(Map.of(
            "commandId", cmd.getId(),
            "status",    cmd.getStatus(),
            "message",   "Đã submit, chờ admin duyệt"
        ));
    }

    @PostMapping("/submit/update/{id}")
    @Operation(summary = "Submit yêu cầu cập nhật (→ PENDING)")
    public ResponseEntity<Map<String, Object>> submitUpdate(
            @PathVariable UUID id,
            @RequestBody REQ request,
            @RequestParam(required = false) String note) {

        CommandRequest cmd = abstractCommand().submitUpdate(id, request, note);
        return ResponseEntity.accepted().body(Map.of(
            "commandId", cmd.getId(),
            "entityId",  id,
            "status",    cmd.getStatus(),
            "message",   "Đã submit, chờ admin duyệt"
        ));
    }

    @PostMapping("/submit/delete/{id}")
    @Operation(summary = "Submit yêu cầu xóa (→ PENDING)")
    public ResponseEntity<Map<String, Object>> submitDelete(
            @PathVariable UUID id,
            @RequestParam(required = false) String note) {

        CommandRequest cmd = abstractCommand().submitDelete(id, note);
        return ResponseEntity.accepted().body(Map.of(
            "commandId", cmd.getId(),
            "entityId",  id,
            "status",    cmd.getStatus(),
            "message",   "Đã submit, chờ admin duyệt"
        ));
    }

    // ── Tab Pending: approve / reject ─────────────────────────

    @GetMapping("/pending")
    @Operation(summary = "Danh sách commands đang chờ duyệt")
    public PageResult<CommandRequest> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(abstractCommand().listPending(page, size));
    }

    @PostMapping("/approve/{commandId}")
    @Operation(summary = "Admin duyệt command → execute vào bảng chính")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID commandId) {
        E result = abstractCommand().approve(commandId);
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("commandId", commandId);
        resp.put("status",    "APPROVED");
        if (result != null) {
            resp.put("entityId", result.getId());
            resp.put("data",     abstractSupport().toResponse(result));
        }
        return ResponseEntity.ok(resp);
    }

    // ── Tab Rejected ──────────────────────────────────────────

    @GetMapping("/rejected")
    @Operation(summary = "Danh sách commands đã bị từ chối")
    public PageResult<CommandRequest> listRejected(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(abstractCommand().listRejected(page, size));
    }

    @PostMapping("/reject/{commandId}")
    @Operation(summary = "Admin từ chối command")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable UUID commandId,
            @RequestParam(required = false) String reason) {

        abstractCommand().reject(commandId, reason);
        return ResponseEntity.ok(Map.of(
            "commandId", commandId,
            "status",    "REJECTED",
            "reason",    reason != null ? reason : ""
        ));
    }

    // ── History ───────────────────────────────────────────────

    @GetMapping("/{id}/history")
    @Operation(summary = "Lịch sử thay đổi của entity")
    public List<RevisionLogResponse> getHistory(@PathVariable UUID id) {
        return abstractHistory()
            .getHistory(abstractSupport().entityType(), id)
            .stream()
            .map(log -> {
                RevisionLogResponse r = new RevisionLogResponse();
                r.setId(log.getId());
                r.setEntityType(log.getEntityType());
                r.setEntityId(log.getEntityId());
                r.setAction(log.getAction());
                r.setCommandRequestId(log.getCommandRequestId());
                r.setSnapshotBefore(log.getSnapshotBefore());
                r.setSnapshotAfter(log.getSnapshotAfter());
                r.setCreatedBy(log.getCreatedBy());
                r.setCreatedAt(log.getCreatedAt());
                return r;
            })
            .toList();
    }
}
