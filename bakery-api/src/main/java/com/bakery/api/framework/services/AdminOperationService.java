package com.bakery.api.framework.services;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.exceptions.AdminEntityNotFoundException;
import com.bakery.api.framework.BaseAdminEntity;
import com.bakery.api.framework.enums.CommandAction;
import com.bakery.api.framework.enums.EntityStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thực thi CRUD trực tiếp vào bảng chính (sau khi đã approved từ CommandRequest).
 *
 * Được gọi bởi AdminCommandService.approve() — không gọi trực tiếp từ controller.
 *
 * Flow:
 *   CommandRequest APPROVED
 *   → AdminCommandService.approve()
 *   → AdminOperationService.executeCreate/Update/Delete()
 *   → EntityHistoryService.record()
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AdminOperationService<REQ, RES extends BakeryBaseResponse, E extends BaseAdminEntity> {

    protected final AdminEntitySupportService<REQ, RES, E> support;
    protected final EntityHistoryService historyService;

    // ── Execute operations (called after approval) ────────────

    @Transactional
    public E executeCreate(REQ request, UUID commandRequestId) {
        support.beforeCreate(request);

        E entity = support.toEntity(request);
        entity.setEntityStatus(EntityStatus.ACTIVE);

        E saved = support.repository().save(entity);

        historyService.record(
            support.entityType(),
            saved.getId(),
            CommandAction.CREATE,
            commandRequestId,
            null,
            support.toResponse(saved)
        );

        support.afterCreate(saved, request);

        log.info("[{}] Created id={} by command={}", support.entityType(), saved.getId(), commandRequestId);
        return saved;
    }

    @Transactional
    public E executeUpdate(UUID entityId, REQ request, UUID commandRequestId) {
        E existing = support.findById(entityId)
            .orElseThrow(() -> new AdminEntityNotFoundException(support.entityType(), entityId));

        support.beforeUpdate(existing, request);

        RES before = support.toResponse(existing);

        support.updateEntity(existing, request);
        E saved = support.repository().save(existing);

        historyService.record(
            support.entityType(),
            saved.getId(),
            CommandAction.UPDATE,
            commandRequestId,
            before,
            support.toResponse(saved)
        );

        support.afterUpdate(saved, request);

        log.info("[{}] Updated id={} by command={}", support.entityType(), saved.getId(), commandRequestId);
        return saved;
    }

    @Transactional
    public void executeDelete(UUID entityId, UUID commandRequestId) {
        E existing = support.findById(entityId)
            .orElseThrow(() -> new AdminEntityNotFoundException(support.entityType(), entityId));

        support.beforeDelete(existing);

        RES before = support.toResponse(existing);

        existing.setEntityStatus(EntityStatus.INACTIVE);
        support.repository().save(existing);

        historyService.record(
            support.entityType(),
            entityId,
            CommandAction.DELETE,
            commandRequestId,
            before,
            null
        );

        support.afterDelete(existing);

        log.info("[{}] Deleted (soft) id={} by command={}", support.entityType(), entityId, commandRequestId);
    }
}
