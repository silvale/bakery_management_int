package com.bakery.common.repository;

import com.bakery.common.entity.CommandRequest;
import com.bakery.common.entity.enums.CommandStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommandRequestRepository
        extends JpaRepository<CommandRequest, UUID>, JpaSpecificationExecutor<CommandRequest> {

    /** Pending commands của 1 entity type (tab Pending trên UI) */
    Page<CommandRequest> findByEntityTypeAndStatus(
            String entityType, CommandStatus status, Pageable pageable);

    /** Tất cả pending commands của 1 entity cụ thể */
    List<CommandRequest> findByEntityIdAndStatus(UUID entityId, CommandStatus status);

    /** Kiểm tra entity có pending command không (block tránh double-submit) */
    boolean existsByEntityIdAndStatus(UUID entityId, CommandStatus status);

    /** Tất cả commands (mọi status) của 1 entity — dùng cho history panel */
    List<CommandRequest> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);
}
