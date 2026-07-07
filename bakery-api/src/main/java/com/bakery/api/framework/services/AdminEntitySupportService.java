package com.bakery.api.framework.services;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.BaseAdminEntity;
import com.bakery.api.framework.meta.EntityMeta;
import com.bakery.api.framework.meta.EntityMetaService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private EntityMetaService entityMetaService;

    /** Tên entity — dùng cho CommandRequest.entityType và EntityRevisionLog.entityType */
    public abstract String entityType();

    /**
     * Class của Response DTO — EntityMetaService dùng để scan @FieldCapability.
     * Override nếu subclass muốn expose metadata.
     */
    protected Class<RES> responseClass() {
        return null;
    }

    /**
     * Build EntityMeta từ @FieldCapability annotations trên Response DTO.
     * Trả về null nếu responseClass() chưa được override.
     */
    public EntityMeta getMeta() {
        Class<RES> clazz = responseClass();
        if (clazz == null) return null;
        return entityMetaService.buildMeta(entityType(), clazz);
    }

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
