package com.bakery.api.framework.repositories;

import com.bakery.api.framework.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    List<ActivityLog> findAllByPerformedByOrderByCreatedAtDesc(String performedBy);

    List<ActivityLog> findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    List<ActivityLog> findAllByEntityTypeOrderByCreatedAtDesc(String entityType);
}
