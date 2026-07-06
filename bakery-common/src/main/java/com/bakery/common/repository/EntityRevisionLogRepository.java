package com.bakery.common.repository;

import com.bakery.common.entity.EntityRevisionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EntityRevisionLogRepository extends JpaRepository<EntityRevisionLog, UUID> {

    /** Toàn bộ lịch sử của 1 entity, mới nhất trước */
    List<EntityRevisionLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    /** Paged — dùng trong history panel */
    Page<EntityRevisionLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId, Pageable pageable);
}
