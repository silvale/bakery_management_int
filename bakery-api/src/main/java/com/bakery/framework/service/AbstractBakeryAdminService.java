package com.bakery.framework.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.CommandRequest;
import com.bakery.framework.entity.CommandStatus;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.util.SpecificationBuilder;

/**
 * Abstract base implementation of BakeryAdminService.
 * Handles common CRUD + approval logic, with automatic command_request logging.
 *
 * @param <E>   JPA entity (extends BaseEntity)
 * @param <REQ> request DTO
 * @param <RES> response DTO
 */
@Transactional(readOnly = true)
public abstract class AbstractBakeryAdminService<E extends BaseEntity, REQ, RES>
        implements BakeryAdminService<REQ, RES> {

    // ── Abstract — must implement ─────────────────────────────

    protected abstract BaseRepository<E> getRepository();

    protected abstract BakeryActorResolver getActorResolver();

    protected abstract CommandRequestRepository getCommandRequestRepository();

    protected abstract String getEntityName();

    protected abstract E toEntity(REQ request);

    protected abstract RES toResponse(E entity);

    protected abstract void applyUpdate(E entity, REQ request);

    // ── Config override ───────────────────────────────────────

    /**
     * Override to true to skip approval flow (create/update go straight to APPROVED).
     */
    protected boolean isAutoApprove() {
        return false;
    }

    // ── Lifecycle hooks (optional override) ──────────────────

    protected void beforeCreate(E entity) {}

    protected void afterCreate(E entity) {}

    protected void beforeUpdate(E entity) {}

    protected void afterUpdate(E entity) {}

    protected void beforeDelete(E entity) {}

    protected void afterDelete(UUID id) {}

    protected void afterApprove(E entity) {}

    protected void afterReject(E entity) {}

    // ── Query ────────────────────────────────────────────────

    @Override
    public Page<RES> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        Specification<E> spec = SpecificationBuilder.from(params);
        return getRepository().findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    public List<RES> findAll() {
        return getRepository().findAllByStatus(EntityStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Optional<RES> findById(UUID id) {
        return getRepository().findById(id).map(this::toResponse);
    }

    // ── Mutate ───────────────────────────────────────────────

    @Override
    @Transactional
    public RES create(REQ request) {
        E entity = toEntity(request);
        entity.setApprovalStatus(isAutoApprove() ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING_APPROVAL);
        beforeCreate(entity);
        E saved = getRepository().save(entity);
        afterCreate(saved);
        log(CommandAction.CREATE, saved.getId(), null, CommandStatus.SUCCESS);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RES update(UUID id, REQ request) {
        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        applyUpdate(entity, request);
        if (!isAutoApprove()) {
            entity.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        }
        beforeUpdate(entity);
        E saved = getRepository().save(entity);
        afterUpdate(saved);
        log(CommandAction.UPDATE, id, null, CommandStatus.SUCCESS);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        beforeDelete(entity);
        entity.setStatus(EntityStatus.INACTIVE);
        getRepository().save(entity);
        afterDelete(id);
        log(CommandAction.DELETE, id, null, CommandStatus.SUCCESS);
    }

    // ── Approval ─────────────────────────────────────────────

    @Override
    @Transactional
    public RES approve(UUID id) {
        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (entity.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(getEntityName() + " is not pending approval");
        }
        entity.setApprovalStatus(ApprovalStatus.APPROVED);
        entity.setApprovedAt(Instant.now());
        entity.setApprovedBy(getActorResolver().currentUserId());
        E saved = getRepository().save(entity);
        afterApprove(saved);
        log(CommandAction.APPROVE, id, null, CommandStatus.SUCCESS);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RES reject(UUID id, String reason) {
        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (entity.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(getEntityName() + " is not pending approval");
        }
        entity.setApprovalStatus(ApprovalStatus.REJECTED);
        entity.setRejectedReason(reason);
        E saved = getRepository().save(entity);
        afterReject(saved);
        log(CommandAction.REJECT, id, reason, CommandStatus.SUCCESS);
        return toResponse(saved);
    }

    // ── Internal logging ──────────────────────────────────────

    private void log(CommandAction action, UUID entityId, String note, CommandStatus status) {
        try {
            CommandRequest cmd = CommandRequest.builder()
                    .entityName(getEntityName())
                    .entityId(entityId)
                    .action(action)
                    .actor(getActorResolver().currentUserId())
                    .note(note)
                    .status(status)
                    .createdAt(Instant.now())
                    .build();
            getCommandRequestRepository().save(cmd);
        } catch (Exception ignored) {
            // Logging must never fail the main transaction
        }
    }
}
