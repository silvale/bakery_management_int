package com.bakery.api.framework.service;

import com.bakery.api.framework.dto.AdminFilter;
import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.common.entity.BaseAdminEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstract base service cho entity-level operations (CRUD + search).
 * Mỗi entity implement 1 concrete class extend này.
 *
 * REQ = Request DTO (create/update body)
 * RES = Response DTO (extends BakeryBaseResponse)
 * E   = Entity (extends BaseEntity)
 */
public abstract class AdminEntitySupportService<REQ, RES extends BakeryBaseResponse, E extends BaseAdminEntity> {

    /** Tên entity — dùng cho CommandRequest.entityType và EntityRevisionLog.entityType */
    public abstract String entityType();

    /** Repository của entity */
    protected abstract JpaRepository<E, UUID> repository();

    /** Repository với specification support (thường là cùng repo) */
    @SuppressWarnings("unchecked")
    protected JpaSpecificationExecutor<E> specExecutor() {
        return (JpaSpecificationExecutor<E>) repository();
    }

    /** Convert entity → response DTO */
    public abstract RES toResponse(E entity);

    /** Apply request DTO vào entity mới (CREATE) */
    protected abstract E toEntity(REQ request);

    /** Apply request DTO vào entity hiện có (UPDATE) */
    protected abstract void updateEntity(E entity, REQ request);

    /** Build Specification từ filter — override để thêm search conditions */
    protected Specification<E> buildSpecification(AdminFilter filter) {
        return Specification.where(null);
    }

    // ── Hooks — override nếu cần side-effects ─────────────────

    /** Validate trước khi tạo (throw AdminValidationException nếu invalid) */
    protected void beforeCreate(REQ request) {}

    /** Validate trước khi update */
    protected void beforeUpdate(E existing, REQ request) {}

    /** Validate trước khi delete (soft) */
    protected void beforeDelete(E existing) {}

    /** Callback sau khi entity được tạo trong DB */
    protected void afterCreate(E entity, REQ request) {}

    /** Callback sau khi entity được update trong DB */
    protected void afterUpdate(E entity, REQ request) {}

    /** Callback sau khi entity được soft-delete */
    protected void afterDelete(E entity) {}

    // ── Reads ─────────────────────────────────────────────────

    public Optional<E> findById(UUID id) {
        return repository().findById(id);
    }

    public Page<RES> list(AdminFilter filter) {
        var pageable = PageRequest.of(filter.getPage(), filter.getSize(),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        var spec     = buildSpecification(filter);
        return specExecutor().findAll(spec, pageable).map(this::toResponse);
    }
}
